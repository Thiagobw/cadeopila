package br.com.cadeopila.transactionservice.adapters.out.exchange;

import br.com.cadeopila.transactionservice.adapters.config.AppProperties;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExchangeRestClientAdapterTest {

    private static final String BASE_URL = "http://brasilapi";
    private static final LocalDate DATE = LocalDate.of(2024, 5, 10);

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    private ExchangeRestClientAdapter adapterFor(String... currencies) {
        var props = new AppProperties("America/Sao_Paulo",
                new AppProperties.Exchange(List.of(currencies), BASE_URL),
                new AppProperties.Account("http://acc"),
                new AppProperties.Http(Duration.ofSeconds(2), Duration.ofSeconds(3)));
        return new ExchangeRestClientAdapter(client, props);
    }

    private String url(String currency, LocalDate date) {
        return BASE_URL + "/cambio/v1/cotacao/" + currency + "/" + date;
    }

    @Test
    @DisplayName("Deve usar a cotação de venda do FECHAMENTO e converter o horário de Brasília para Instant")
    void deveUsarFechamentoEConverterZona() {
        server.expect(requestTo(url("USD", DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[
                          {"cotacao_venda":5.10,"data_hora_cotacao":"2024-05-10 10:00:00.000","tipo_boletim":"ABERTURA"},
                          {"cotacao_venda":5.15,"data_hora_cotacao":"2024-05-10 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ExchangeRate> rates = adapterFor("USD").quoteAll(DATE);

        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().currency()).isEqualTo("USD");
        assertThat(rates.getFirst().rate()).isEqualByComparingTo("5.15");
        // 13:00 em America/Sao_Paulo (UTC-3) = 16:00Z
        assertThat(rates.getFirst().quotedAt()).isEqualTo(Instant.parse("2024-05-10T16:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("Sem FECHAMENTO deve usar o boletim mais recente do dia")
    void semFechamentoDeveUsarMaisRecente() {
        server.expect(requestTo(url("USD", DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[
                          {"cotacao_venda":5.10,"data_hora_cotacao":"2024-05-10 10:00:00.000","tipo_boletim":"ABERTURA"},
                          {"cotacao_venda":5.12,"data_hora_cotacao":"2024-05-10 12:00:00.000","tipo_boletim":"INTERMEDIÁRIO"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ExchangeRate> rates = adapterFor("USD").quoteAll(DATE);

        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().rate()).isEqualByComparingTo("5.12");
        assertThat(rates.getFirst().quotedAt()).isEqualTo(Instant.parse("2024-05-10T15:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("Deve cotar todas as moedas configuradas")
    void deveCotarTodasAsMoedas() {
        server.expect(requestTo(url("USD", DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[{"cotacao_venda":5.15,"data_hora_cotacao":"2024-05-10 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(url("EUR", DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[{"cotacao_venda":5.90,"data_hora_cotacao":"2024-05-10 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}]}
                        """, MediaType.APPLICATION_JSON));

        List<ExchangeRate> rates = adapterFor("USD", "EUR").quoteAll(DATE);

        assertThat(rates).extracting(ExchangeRate::currency).containsExactly("USD", "EUR");
        server.verify();
    }

    @Test
    @DisplayName("Com lista vazia deve retroceder um dia e usar a cotação anterior")
    void listaVaziaDeveRetroceder() {
        server.expect(requestTo(url("USD", DATE)))
                .andRespond(withSuccess("{\"cotacoes\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url("USD", DATE.minusDays(1))))
                .andRespond(withSuccess("""
                        {"cotacoes":[{"cotacao_venda":5.09,"data_hora_cotacao":"2024-05-09 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}]}
                        """, MediaType.APPLICATION_JSON));

        List<ExchangeRate> rates = adapterFor("USD").quoteAll(DATE);

        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().rate()).isEqualByComparingTo("5.09");
        server.verify();
    }

    @Test
    @DisplayName("Após 7 dias sem dados deve devolver lista sem a moeda (7 tentativas)")
    void seteDiasSemDadosDeveOmitirMoeda() {
        for (int i = 0; i < 7; i++) {
            server.expect(requestTo(url("USD", DATE.minusDays(i))))
                    .andRespond(withSuccess("{\"cotacoes\":[]}", MediaType.APPLICATION_JSON));
        }

        assertThat(adapterFor("USD").quoteAll(DATE)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Erro 5xx deve parar na hora e omitir a moeda (tolerante a falha)")
    void erro5xxDeveOmitirMoeda() {
        server.expect(requestTo(url("USD", DATE))).andRespond(withServerError());

        assertThat(adapterFor("USD").quoteAll(DATE)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Erro 4xx deve parar na hora e omitir a moeda")
    void erro4xxDeveOmitirMoeda() {
        server.expect(requestTo(url("USD", DATE)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThat(adapterFor("USD").quoteAll(DATE)).isEmpty();
        server.verify();
    }
}
