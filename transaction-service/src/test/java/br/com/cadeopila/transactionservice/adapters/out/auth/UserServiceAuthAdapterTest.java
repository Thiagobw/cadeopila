package br.com.cadeopila.transactionservice.adapters.out.auth;

import br.com.cadeopila.transactionservice.core.exception.AuthenticationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceAuthAdapterTest {

    private static final String BASE_URL = "http://user-api";
    private static final String VALIDATE_URL = BASE_URL + "/api/v1/auth/validate";
    private static final String INTERNAL_KEY = "chave-interna-secreta";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockRestServiceServer server;
    private UserServiceAuthAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new UserServiceAuthAdapter(builder.build(), INTERNAL_KEY);
    }

    @Test
    @DisplayName("Deve retornar o userId e enviar o header X-Internal-Key quando o MS1 valida (200)")
    void deveRetornarUserIdQuandoValido() {
        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(UserServiceAuthAdapter.INTERNAL_KEY_HEADER, INTERNAL_KEY))
                .andRespond(withSuccess("{\"userId\":\"%s\"}".formatted(USER_ID), MediaType.APPLICATION_JSON));

        assertThat(adapter.authenticate("maria@email.com", "senha123")).contains(USER_ID);
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio quando o MS1 responde 401 (credenciais inválidas)")
    void deveRetornarVazioEm401() {
        server.expect(requestTo(VALIDATE_URL))
                .andExpect(header(UserServiceAuthAdapter.INTERNAL_KEY_HEADER, INTERNAL_KEY))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(adapter.authenticate("maria@email.com", "errada")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio quando o MS1 responde 400 (e-mail malformado) — e NÃO 503")
    void deveRetornarVazioEm400() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(adapter.authenticate("email-invalido", "senha123")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio para qualquer outro 4xx do MS1 (ex.: 403)")
    void deveRetornarVazioEmOutros4xx() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(adapter.authenticate("maria@email.com", "senha123")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar indisponibilidade quando o MS1 responde 500")
    void deveLancarIndisponibilidadeEm500() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.authenticate("maria@email.com", "senha123"))
                .isInstanceOf(AuthenticationUnavailableException.class);
        server.verify();
    }
}
