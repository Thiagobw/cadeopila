package br.com.cadeopila.transactionservice.adapters.out.account;

import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountRestClientAdapterTest {

    private static final String BASE_URL = "http://account-api";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockRestServiceServer server;
    private AccountRestClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AccountRestClientAdapter(builder.build());
    }

    @Test
    @DisplayName("findByUserId deve retornar o snapshot (saldo inicial + limites) quando a conta existe")
    void findByUserIdDeveRetornarSnapshot() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"1","userId":"%s","balance":1500.00,"dailyLimit":1000.00,"transactionLimit":500.00}]
                        """.formatted(USER_ID), MediaType.APPLICATION_JSON));

        var snapshot = adapter.findByUserId(USER_ID);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().userId()).isEqualTo(USER_ID);
        assertThat(snapshot.get().balance()).isEqualByComparingTo("1500.00");
        assertThat(snapshot.get().dailyLimit()).isEqualByComparingTo("1000.00");
        assertThat(snapshot.get().transactionLimit()).isEqualByComparingTo("500.00");
        server.verify();
    }

    @Test
    @DisplayName("findByUserId deve retornar vazio quando a API responde 404")
    void findByUserIdDeveRetornarVazioEm404() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThat(adapter.findByUserId(USER_ID)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("findByUserId deve retornar vazio quando a lista vem vazia")
    void findByUserIdDeveRetornarVazioComListaVazia() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(adapter.findByUserId(USER_ID)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("findByUserId deve ignorar contas cujo userId não bate exatamente (filtro do MockAPI)")
    void findByUserIdDeveFiltrarUserIdExato() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withSuccess("""
                        [{"id":"9","userId":"99999999-9999-9999-9999-999999999999","balance":10.00,"dailyLimit":10.00,"transactionLimit":10.00}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.findByUserId(USER_ID)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("findByUserId deve lançar indisponibilidade quando a API responde 500")
    void findByUserIdDeveLancarEm500() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.findByUserId(USER_ID))
                .isInstanceOf(AccountUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("findByUserId deve lançar indisponibilidade quando há mais de uma conta para o usuário")
    void findByUserIdDeveLancarComMultiplasContas() {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withSuccess("""
                        [{"id":"1","userId":"%s","balance":1.00,"dailyLimit":1.00,"transactionLimit":1.00},
                         {"id":"2","userId":"%s","balance":2.00,"dailyLimit":2.00,"transactionLimit":2.00}]
                        """.formatted(USER_ID, USER_ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.findByUserId(USER_ID))
                .isInstanceOf(AccountUnavailableException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":\"1\",\"userId\":\"%s\",\"balance\":null,\"dailyLimit\":1000.00,\"transactionLimit\":500.00}",
            "{\"id\":\"1\",\"userId\":\"%s\",\"balance\":1500.00,\"dailyLimit\":null,\"transactionLimit\":500.00}",
            "{\"id\":\"1\",\"userId\":\"%s\",\"balance\":1500.00,\"dailyLimit\":1000.00,\"transactionLimit\":null}"
    })
    @DisplayName("findByUserId deve lançar indisponibilidade quando a MockAPI omite um campo obrigatório (null)")
    void findByUserIdDeveLancarComCampoObrigatorioNulo(String accountJson) {
        server.expect(requestTo(BASE_URL + "/accounts?userId=" + USER_ID))
                .andRespond(withSuccess("[" + accountJson.formatted(USER_ID) + "]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.findByUserId(USER_ID))
                .isInstanceOf(AccountUnavailableException.class);
        server.verify();
    }
}
