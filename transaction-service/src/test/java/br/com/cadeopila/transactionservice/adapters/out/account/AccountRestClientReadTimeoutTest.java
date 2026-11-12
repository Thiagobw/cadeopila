package br.com.cadeopila.transactionservice.adapters.out.account;

import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova de resiliência à lentidão da MockAPI (D5): um servidor HTTP real atrasa a resposta em 4s, mas o
 * read-timeout do cliente — o mesmo mecanismo de {@code RestClientConfig} ({@link SimpleClientHttpRequestFactory}
 * com {@code setReadTimeout}) — encerra a chamada muito antes. A aplicação nunca trava: a falha técnica vira
 * {@link AccountUnavailableException} (que o caso de uso traduz para REJECTED e a rota de saldo para 503).
 *
 * <p>Usamos um servidor de verdade (JDK {@link HttpServer}) porque {@code MockRestServiceServer} não faz I/O
 * de socket real — logo não exercitaria o timeout. Sem dependência nova (nem MockWebServer nem WireMock).</p>
 */
class AccountRestClientReadTimeoutTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration SERVER_DELAY = Duration.ofSeconds(4);
    private static final String ACCOUNT_JSON = "[{\"id\":\"1\",\"userId\":\"" + USER_ID
            + "\",\"balance\":1500.00,\"dailyLimit\":1000.00,\"transactionLimit\":500.00}]";

    private HttpServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void startSlowServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "slow-account-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.createContext("/accounts", exchange -> {
            try {
                Thread.sleep(SERVER_DELAY.toMillis()); // atraso proposital, muito além do read-timeout do cliente
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            var body = ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopSlowServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    @DisplayName("MockAPI lenta (4s): o read-timeout lança AccountUnavailableException bem antes de 4s (D5)")
    void readTimeoutAbortaChamadaLentaAntesDe4s() {
        var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var adapter = new AccountRestClientAdapter(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(readTimeoutFactory())
                .build());

        var start = Instant.now();
        assertThatThrownBy(() -> adapter.findByUserId(USER_ID))
                .isInstanceOf(AccountUnavailableException.class);
        var elapsed = Duration.between(start, Instant.now());

        // Se o read-timeout não funcionasse, a chamada só retornaria após os 4s do servidor.
        assertThat(elapsed).isLessThan(SERVER_DELAY);
    }

    /** Mesma configuração de RestClientConfig: SimpleClientHttpRequestFactory com connect/read timeout. */
    private static ClientHttpRequestFactory readTimeoutFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return factory;
    }
}
