package br.com.cadeopila.transactionservice.adapters.in.web.security;

import br.com.cadeopila.transactionservice.adapters.in.web.AccountController;
import br.com.cadeopila.transactionservice.adapters.in.web.mapper.AccountWebMapper;
import br.com.cadeopila.transactionservice.core.exception.AuthenticationUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.in.GetAccountBalanceUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita a cadeia de segurança real (filtro Basic + SecurityConfig + entry point) sobre um endpoint
 * protegido: sem credenciais → 401; credenciais válidas → 200 com o userId extraído do MS1; inválidas →
 * 401; MS1 indisponível → 503.
 */
@WebMvcTest(AccountController.class)
@Import({AccountWebMapper.class, SecurityConfig.class})
class SecurityIntegrationTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAccountBalanceUseCase getAccountBalance;
    @MockitoBean
    private UserAuthenticationPort userAuthenticationPort;

    private static String basic(String email, String password) {
        var raw = (email + ":" + password).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(raw);
    }

    @Test
    @DisplayName("Sem Authorization → 401")
    void semCredenciaisDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Credenciais válidas → 200 usando o userId devolvido pelo MS1")
    void credenciaisValidasDeveRetornar200() throws Exception {
        when(userAuthenticationPort.authenticate("maria@email.com", "senha123")).thenReturn(Optional.of(USER));
        when(getAccountBalance.get(USER)).thenReturn(new GetAccountBalanceUseCase.Balance(
                new BigDecimal("1200.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                new BigDecimal("300.00"), new BigDecimal("9700.00")));

        mockMvc.perform(get("/api/v1/accounts/balance")
                        .header(HttpHeaders.AUTHORIZATION, basic("maria@email.com", "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1200.00));
    }

    @Test
    @DisplayName("Credenciais inválidas → 401")
    void credenciaisInvalidasDeveRetornar401() throws Exception {
        when(userAuthenticationPort.authenticate(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/balance")
                        .header(HttpHeaders.AUTHORIZATION, basic("maria@email.com", "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MS1 indisponível → 503")
    void ms1IndisponivelDeveRetornar503() throws Exception {
        when(userAuthenticationPort.authenticate(any(), any()))
                .thenThrow(new AuthenticationUnavailableException("fora do ar"));

        mockMvc.perform(get("/api/v1/accounts/balance")
                        .header(HttpHeaders.AUTHORIZATION, basic("maria@email.com", "senha123")))
                .andExpect(status().isServiceUnavailable());
    }
}
