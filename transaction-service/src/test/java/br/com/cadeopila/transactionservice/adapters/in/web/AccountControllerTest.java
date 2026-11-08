package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.mapper.AccountWebMapper;
import br.com.cadeopila.transactionservice.core.exception.AccountNotFoundException;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.in.GetAccountBalanceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(AccountWebMapper.class)
class AccountControllerTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAccountBalanceUseCase getAccountBalance;

    @Test
    @DisplayName("GET /accounts/balance deve retornar 200 com saldo e limites")
    void balanceDeveRetornar200() throws Exception {
        when(getAccountBalance.get(USER)).thenReturn(new GetAccountBalanceUseCase.Balance(
                new BigDecimal("1200.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                new BigDecimal("300.00"), new BigDecimal("9700.00")));

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1200.00))
                .andExpect(jsonPath("$.dailyLimit").value(10000.00))
                .andExpect(jsonPath("$.transactionLimit").value(5000.00))
                .andExpect(jsonPath("$.spentToday").value(300.00))
                .andExpect(jsonPath("$.availableToday").value(9700.00));
    }

    @Test
    @DisplayName("GET /accounts/balance sem userId deve retornar 400 indicando o campo")
    void balanceSemUserIdDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("userId"));
    }

    @Test
    @DisplayName("GET /accounts/balance deve retornar 404 quando a conta não existe")
    void balanceContaInexistenteDeveRetornar404() throws Exception {
        when(getAccountBalance.get(any())).thenThrow(new AccountNotFoundException(USER));

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /accounts/balance deve retornar 503 quando a API de contas está fora do ar")
    void balanceMockForaDoArDeveRetornar503() throws Exception {
        when(getAccountBalance.get(any())).thenThrow(new AccountUnavailableException("fora do ar"));

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isServiceUnavailable());
    }
}
