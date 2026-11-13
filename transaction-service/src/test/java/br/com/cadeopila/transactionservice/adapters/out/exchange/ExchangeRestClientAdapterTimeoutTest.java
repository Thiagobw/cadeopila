package br.com.cadeopila.transactionservice.adapters.out.exchange;

import br.com.cadeopila.transactionservice.adapters.config.AppProperties;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova de que o read-timeout do câmbio (BrasilAPI) realmente dispara e vira fallback, sem travar a
 * transação quando a origem está lenta. Diferente do {@code ExchangeRestClientAdapterTest} (que usa
 * MockRestServiceServer e não faz I/O de rede real), aqui sobe um servidor HTTP de verdade
 * (MockWebServer) que ATRASA o corpo da resposta muito além do read-timeout.
 *
 * <p>Nota de engenharia: a request factory de produção é a {@link SimpleClientHttpRequestFactory}
 * (JDK {@code HttpURLConnection}), que REtentar uma vez um GET idempotente ao estourar o
 * read-timeout. Logo o teto efetivo observado é ~2× o read-timeout (1s + 1s ≈ 2s) por moeda — não
 * 1s. O teste prova o que importa: o fallback volta em ~2s, MUITO antes dos {@value #BODY_DELAY_MS}ms
 * de atraso da origem, ou seja, o timeout corta a chamada e ela não trava.</p>
 */
class ExchangeRestClientAdapterTimeoutTest {

    private static final LocalDate DATE = LocalDate.of(2024, 5, 10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
    // Atraso da origem bem acima do teto efetivo (~2s): elimina ambiguidade. Se a chamada voltar MUITO
    // antes disso, só pode ter sido o read-timeout cortando — e não a resposta lenta chegando.
    private static final long BODY_DELAY_MS = 5_000;

    private static final String FECHAMENTO_USD = """
            {"cotacoes":[{"cotacao_venda":5.15,"data_hora_cotacao":"2024-05-10 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}]}
            """;

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Origem mais lenta que o read-timeout deve virar fallback (omite a moeda) em ~2s, sem travar nos 5s")
    void origemLentaDisparaTimeoutEOmiteMoeda() {
        // Enfileira uma resposta atrasada por tentativa de GET: o HttpURLConnection reexecuta o GET uma
        // vez ao estourar o read-timeout, então são até 2 tentativas. Enfileirar 2 evita que a retentativa
        // caia numa fila vazia; se a retentativa não ocorrer, a 2ª resposta simplesmente não é usada.
        server.enqueue(slowJson(FECHAMENTO_USD));
        server.enqueue(slowJson(FECHAMENTO_USD));

        var adapter = new ExchangeRestClientAdapter(timedRestClient(), propertiesFor("USD"));

        long startNanos = System.nanoTime();
        List<ExchangeRate> rates = adapter.quoteAll(DATE);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        // Tolerante a falha: o timeout vira ausência da cotação, sem exceção propagada ao caminho da transação.
        assertThat(rates).isEmpty();

        // Prova do timeout: voltou na faixa do read-timeout (~2s com a retentativa do GET) e MUITO antes
        // dos 5s da origem. Piso de 900ms garante que foi o timeout (e não uma falha instantânea
        // mascarando o teste); teto de 3s fica bem acima dos ~2s efetivos e bem abaixo dos 5s da origem.
        assertThat(elapsed)
                .as("fallback retornou na faixa do read-timeout, não nos 5s de atraso da origem")
                .isGreaterThanOrEqualTo(Duration.ofMillis(900))
                .isLessThan(Duration.ofSeconds(3));
    }

    /** Resposta JSON válida, porém com o CORPO atrasado além do read-timeout (origem lenta). */
    private MockResponse slowJson(String body) {
        return new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body)
                .setBodyDelay(BODY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** RestClient montado como em produção (RestClientConfig): SimpleClientHttpRequestFactory com read-timeout de 1s. */
    private RestClient timedRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(factory)
                .build();
    }

    private AppProperties propertiesFor(String... currencies) {
        return new AppProperties("America/Sao_Paulo",
                new AppProperties.Exchange(List.of(currencies), server.url("/").toString(), READ_TIMEOUT),
                new AppProperties.Account("http://acc"),
                new AppProperties.User("http://user"),
                new AppProperties.Http(Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }
}
