package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountBalanceIntegrationTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepositoryPort repository;

    @MockitoBean
    private AccountPort accountPort;

    private Transaction approvedDeposit(String amount) {
        var data = new NewTransaction(USER, TransactionType.DEPOSIT, new BigDecimal(amount), "Depósito", null, null);
        return Transaction.approved(data, Instant.now(), List.of(), Instant.now());
    }

    private Transaction approvedWithdrawal(String amount) {
        var data = new NewTransaction(USER, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.approved(data, Instant.now(), List.of(), Instant.now());
    }

    @Test
    @DisplayName("GET /accounts/balance devolve o saldo derivado (Mock + histórico) e o consumo do dia")
    void saldoDerivado() throws Exception {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(
                new AccountSnapshot(USER, new BigDecimal("1000.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"))));
        repository.save(approvedDeposit("500.00"));
        repository.save(approvedWithdrawal("200.00"));

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1300.00))       // 1000 + 500 − 200
                .andExpect(jsonPath("$.spentToday").value(200.00))     // só a saída aprovada do dia
                .andExpect(jsonPath("$.availableToday").value(9800.00));
    }

    @Test
    @DisplayName("GET /accounts/balance retorna 404 quando a conta não existe")
    void contaInexistente404() throws Exception {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /accounts/balance retorna 503 quando a API de contas está fora do ar")
    void mockForaDoAr503() throws Exception {
        when(accountPort.findByUserId(any())).thenThrow(new AccountUnavailableException("fora do ar"));

        mockMvc.perform(get("/api/v1/accounts/balance").param("userId", USER.toString()))
                .andExpect(status().isServiceUnavailable());
    }
}
