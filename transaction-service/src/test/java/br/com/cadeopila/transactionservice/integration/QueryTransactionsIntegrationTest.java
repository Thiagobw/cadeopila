package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultas por usuário. O usuário autenticado (principal) é {@code USER_A}: as rotas derivam o dono
 * do contexto de segurança, não mais de um parâmetro. Filtros desligados no MockMvc
 * ({@code addFilters = false}); o Basic Auth real é coberto por {@code SecurityIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class QueryTransactionsIntegrationTest {

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID DESTINATION = UUID.randomUUID();

    private static final Instant IN_WINDOW = Instant.parse("2026-11-06T13:00:00Z");
    private static final Instant BEFORE_WINDOW = Instant.parse("2026-11-05T13:00:00Z");
    private static final Instant AFTER_WINDOW = Instant.parse("2026-11-07T13:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T13:00:01Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepositoryPort repository;

    @BeforeEach
    void authenticate() {
        var authentication = new UsernamePasswordAuthenticationToken(
                USER_A, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /{id} deve retornar 200 com os campos e o bloco de câmbio")
    void getByIdDeveRetornar200() throws Exception {
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), "BrasilAPI", NOW);
        var saved = repository.save(approvedPurchase(USER_A, "150.00", IN_WINDOW, List.of(quote)));

        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.exchange.available").value(true))
                .andExpect(jsonPath("$.exchange.quotes[0].currency").value("USD"))
                .andExpect(jsonPath("$.exchange.quotes[0].source").value("BrasilAPI"));
    }

    @Test
    @DisplayName("GET /{id} de transação excluída deve retornar 404")
    void getByIdExcluidaDeveRetornar404() throws Exception {
        var saved = repository.save(deletedTransfer(USER_A, "20.00", IN_WINDOW));

        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} inexistente deve retornar 404")
    void getByIdInexistenteDeveRetornar404() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} de transação de outro usuário deve retornar 404 (não vaza existência)")
    void getByIdDeOutroUsuarioDeveRetornar404() throws Exception {
        var saved = repository.save(approvedDeposit(USER_B, "500.00", IN_WINDOW));

        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET lista deve trazer só as ativas do usuário, excluindo apagadas e de outros")
    void listaFiltraUsuarioEExcluiApagadas() throws Exception {
        repository.save(approvedDeposit(USER_A, "100.00", IN_WINDOW));
        repository.save(approvedWithdrawal(USER_A, "40.00", IN_WINDOW));
        repository.save(deletedTransfer(USER_A, "20.00", IN_WINDOW));
        repository.save(approvedDeposit(USER_B, "500.00", IN_WINDOW));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET lista deve filtrar por status")
    void listaFiltraPorStatus() throws Exception {
        repository.save(approvedDeposit(USER_A, "100.00", IN_WINDOW));
        repository.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));

        mockMvc.perform(get("/api/v1/transactions").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"));
    }

    @Test
    @DisplayName("GET lista deve filtrar por período (datas inclusivas)")
    void listaFiltraPorPeriodo() throws Exception {
        repository.save(approvedDeposit(USER_A, "10.00", BEFORE_WINDOW));
        repository.save(approvedDeposit(USER_A, "20.00", IN_WINDOW));
        repository.save(approvedDeposit(USER_A, "30.00", AFTER_WINDOW));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("from", "2026-11-06")
                        .param("to", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-06T13:00:00Z"));
    }

    @Test
    @DisplayName("GET lista deve paginar e ordenar por occurredAt decrescente")
    void listaPaginaEOrdena() throws Exception {
        repository.save(approvedDeposit(USER_A, "10.00", BEFORE_WINDOW));
        repository.save(approvedDeposit(USER_A, "20.00", IN_WINDOW));

        mockMvc.perform(get("/api/v1/transactions").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-06T13:00:00Z"));
    }

    private Transaction approvedPurchase(UUID user, String amount, Instant occurredAt, List<ExchangeQuote> quotes) {
        var data = new NewTransaction(user, TransactionType.PURCHASE, new BigDecimal(amount), "Compra",
                TransactionCategory.FOOD, null);
        return Transaction.approved(data, occurredAt, quotes, NOW);
    }

    private Transaction approvedDeposit(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.DEPOSIT, new BigDecimal(amount), "Depósito", null, null);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
    }

    private Transaction approvedWithdrawal(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
    }

    private Transaction rejectedWithdrawal(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.rejected(data, RejectionReason.INSUFFICIENT_BALANCE, occurredAt, List.of(), NOW);
    }

    private Transaction deletedTransfer(UUID user, String amount, Instant occurredAt) {
        return Transaction.rehydrate(null, user, DESTINATION, TransactionType.TRANSFER, new BigDecimal(amount),
                "Transferência", TransactionCategory.OTHER, TransactionStatus.APPROVED, null, occurredAt,
                List.of(), NOW, NOW, NOW, 0L);
    }
}
