package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * Fluxo real de saldo. A cadeia de segurança fica desligada no MockMvc ({@code addFilters = false});
 * o usuário autenticado é simulado no {@link SecurityContextHolder} (o {@code @AuthenticationPrincipal}
 * o lê de lá). O Basic Auth/filtro real é coberto por {@code SecurityIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AccountBalanceIntegrationTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepositoryPort repository;

    @MockitoBean
    private AccountPort accountPort;

    @BeforeEach
    void authenticate() {
        var authentication = new UsernamePasswordAuthenticationToken(
                USER, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Transaction approvedDeposit(String amount) {
        var data = new NewTransaction(USER, TransactionType.DEPOSIT, new BigDecimal(amount), "Depósito", null, null);
        return Transaction.approved(data, Instant.now(), List.of(), Instant.now());
    }

    private Transaction approvedWithdrawal(String amount) {
        var data = new NewTransaction(USER, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.approved(data, Instant.now(), List.of(), Instant.now());
    }

    /** Transferência de um terceiro para o usuário autenticado (ele é o destinatário). */
    private Transaction incomingTransfer(String amount) {
        var data = new NewTransaction(UUID.randomUUID(), TransactionType.TRANSFER, new BigDecimal(amount),
                "Transferência recebida", null, USER);
        return Transaction.approved(data, Instant.now(), List.of(), Instant.now());
    }

    @Test
    @DisplayName("GET /accounts/balance devolve o saldo derivado (Mock + histórico) e o consumo do dia")
    void saldoDerivado() throws Exception {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(
                new AccountSnapshot(USER, new BigDecimal("1000.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"))));
        repository.save(approvedDeposit("500.00"));
        repository.save(approvedWithdrawal("200.00"));

        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1300.00))       // 1000 + 500 − 200
                .andExpect(jsonPath("$.spentToday").value(200.00))     // só a saída aprovada do dia
                .andExpect(jsonPath("$.availableToday").value(9800.00));
    }

    @Test
    @DisplayName("GET /accounts/balance inclui as transferências recebidas (usuário é o destinatário)")
    void saldoIncluiTransferenciasRecebidas() throws Exception {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(
                new AccountSnapshot(USER, new BigDecimal("1000.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"))));
        repository.save(approvedDeposit("500.00"));   // +500 (próprio)
        repository.save(incomingTransfer("300.00"));  // +300 (recebida)

        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1800.00)); // 1000 + 500 + 300
    }

    @Test
    @DisplayName("GET /accounts/balance retorna 404 quando a conta não existe")
    void contaInexistente404() throws Exception {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /accounts/balance retorna 503 quando a API de contas está fora do ar")
    void mockForaDoAr503() throws Exception {
        when(accountPort.findByUserId(any())).thenThrow(new AccountUnavailableException("fora do ar"));

        mockMvc.perform(get("/api/v1/accounts/balance"))
                .andExpect(status().isServiceUnavailable());
    }
}
