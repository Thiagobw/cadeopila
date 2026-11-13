package br.com.cadeopila.transactionservice.adapters.out.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MockApiHealthIndicatorTest {

    private static final String BASE_URL = "http://account-api";

    private MockRestServiceServer server;
    private MockApiHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        indicator = new MockApiHealthIndicator(builder.build());
    }

    @Test
    @DisplayName("MockAPI responde 2xx → UP")
    void respostaOkDeveSerUp() {
        server.expect(requestTo(containsString("/accounts")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("account-api", "reachable");
        server.verify();
    }

    @Test
    @DisplayName("MockAPI responde 401 (token inválido) → DOWN com o código HTTP")
    void token401DeveSerDown() {
        server.expect(requestTo(containsString("/accounts")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("account-api", "HTTP 401");
        server.verify();
    }

    @Test
    @DisplayName("MockAPI responde 404 (recurso inválido) → DOWN")
    void recurso404DeveSerDown() {
        server.expect(requestTo(containsString("/accounts")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("account-api", "HTTP 404");
        server.verify();
    }

    @Test
    @DisplayName("MockAPI inacessível (I/O / timeout) → DOWN sem vazar a URL")
    void origemInacessivelDeveSerDown() {
        server.expect(requestTo(containsString("/accounts")))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("account-api", "unreachable");
        server.verify();
    }
}
