package br.com.cadeopila.transactionservice.adapters.out.exchange;

import br.com.cadeopila.transactionservice.adapters.config.AppProperties;
import br.com.cadeopila.transactionservice.adapters.out.exchange.dto.BrasilApiCambioResponse;
import br.com.cadeopila.transactionservice.adapters.out.exchange.dto.BrasilApiCambioResponse.Cotacao;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import br.com.cadeopila.transactionservice.core.ports.out.ExchangeRatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adapter da {@link ExchangeRatePort} sobre a BrasilAPI (câmbio PTAX, cotação de venda).
 * Tolerante a falha: qualquer erro (4xx/5xx/timeout/rede) numa moeda resulta em ausência dessa
 * cotação, nunca bloqueia a transação. Em "sem dados" (lista vazia) retrocede até 7 dias.
 */
@Component
public class ExchangeRestClientAdapter implements ExchangeRatePort {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRestClientAdapter.class);

    private static final int MAX_LOOKBACK_DAYS = 7;
    private static final String CLOSING_BULLETIN_PREFIX = "FECHAMENTO";
    private static final String SOURCE = "BrasilAPI";
    private static final ZoneId BULLETIN_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter BULLETIN_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter(Locale.ROOT);

    private final RestClient restClient;
    private final List<String> currencies;

    public ExchangeRestClientAdapter(@Qualifier("exchangeRestClient") RestClient restClient,
                                     AppProperties properties) {
        this.restClient = restClient;
        this.currencies = properties.exchange().currencies();
    }

    @Override
    public List<ExchangeRate> quoteAll(LocalDate date) {
        var rates = new ArrayList<ExchangeRate>(currencies.size());
        for (var currency : currencies) {
            quote(currency, date).ifPresent(rates::add);
        }
        return rates;
    }

    private Optional<ExchangeRate> quote(String currency, LocalDate date) {
        var queryDate = date;
        for (int attempt = 0; attempt < MAX_LOOKBACK_DAYS; attempt++, queryDate = queryDate.minusDays(1)) {
            BrasilApiCambioResponse response;
            try {
                response = restClient.get()
                        .uri("/cambio/v1/cotacao/{moeda}/{data}", currency, queryDate.toString())
                        .retrieve()
                        .body(BrasilApiCambioResponse.class);
            } catch (RestClientException ex) {
                log.warn("Falha ao consultar a BrasilAPI (moeda={}, erro={})", currency, ex.getClass().getSimpleName());
                return Optional.empty();
            }

            if (response == null || response.cotacoes() == null || response.cotacoes().isEmpty()) {
                continue;
            }
            return Optional.of(toRate(currency, pickBulletin(response.cotacoes())));
        }
        return Optional.empty();
    }

    private Cotacao pickBulletin(List<Cotacao> cotacoes) {
        return cotacoes.stream()
                .filter(c -> c.tipoBoletim() != null && c.tipoBoletim().startsWith(CLOSING_BULLETIN_PREFIX))
                .findFirst()
                .orElseGet(() -> cotacoes.stream()
                        .max(Comparator.comparing(c -> LocalDateTime.parse(c.dataHoraCotacao(), BULLETIN_FORMAT)))
                        .orElseThrow());
    }

    private ExchangeRate toRate(String currency, Cotacao cotacao) {
        var quotedAt = LocalDateTime.parse(cotacao.dataHoraCotacao(), BULLETIN_FORMAT)
                .atZone(BULLETIN_ZONE)
                .toInstant();
        return new ExchangeRate(currency, cotacao.cotacaoVenda(), SOURCE, quotedAt);
    }
}
