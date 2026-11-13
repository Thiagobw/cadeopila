package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.mapper.TransactionWebMapper;
import br.com.cadeopila.transactionservice.adapters.in.web.security.SecurityConfig;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotDeletableException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.DeleteTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.FindTransactionByIdUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.ListTransactionsUseCase;
import br.com.cadeopila.transactionservice.core.ports.in.UpdateTransactionMetadataUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice de web do {@link TransactionController}. Os filtros de segurança ficam desligados aqui
 * ({@code addFilters = false}): o foco é o controller, com o usuário autenticado simulado no
 * {@link SecurityContextHolder} (o {@code @AuthenticationPrincipal} lê o {@code userId} de lá).
 * O comportamento real do Basic Auth/filtro vive em {@code SecurityIntegrationTest}.
 */
@WebMvcTest(TransactionController.class)
@Import({TransactionWebMapper.class, SecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final Instant TS = Instant.parse("2026-11-06T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTransactionUseCase createTransaction;
    @MockitoBean
    private FindTransactionByIdUseCase findTransactionById;
    @MockitoBean
    private ListTransactionsUseCase listTransactions;
    @MockitoBean
    private UpdateTransactionMetadataUseCase updateTransactionMetadata;
    @MockitoBean
    private DeleteTransactionUseCase deleteTransaction;
    @MockitoBean
    private UserAuthenticationPort userAuthenticationPort;

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

    private Transaction tx(UUID id, TransactionType type, String amount, TransactionStatus status,
                           RejectionReason reason, List<ExchangeQuote> quotes) {
        return Transaction.rehydrate(id, USER, null, type, new BigDecimal(amount), "desc",
                TransactionCategory.FOOD, status, reason, TS, quotes, TS, TS, null, 0L);
    }

    // ---------- POST ----------

    @Test
    @DisplayName("POST aprovado deve retornar 201 com Location, status APPROVED, câmbio e saldo")
    void postAprovadoDeveRetornar201() throws Exception {
        var id = UUID.randomUUID();
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), "BrasilAPI", TS);
        var result = new CreateTransactionUseCase.Result(
                tx(id, TransactionType.PURCHASE, "150.00", TransactionStatus.APPROVED, null, List.of(quote)),
                new BigDecimal("14850.00"));
        when(createTransaction.create(any())).thenReturn(result);

        var body = """
                {"type":"PURCHASE","amount":150.00,"description":"Mercado","category":"FOOD"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/transactions/" + id)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.account.balanceAfter").value(14850.00));
    }

    @Test
    @DisplayName("POST rejeitado deve retornar 201 com status REJECTED e motivo")
    void postRejeitadoDeveRetornar201() throws Exception {
        var result = new CreateTransactionUseCase.Result(
                tx(UUID.randomUUID(), TransactionType.WITHDRAWAL, "40.00", TransactionStatus.REJECTED,
                        RejectionReason.INSUFFICIENT_BALANCE, List.of()),
                new BigDecimal("30.00"));
        when(createTransaction.create(any())).thenReturn(result);

        var body = """
                {"type":"WITHDRAWAL","amount":40.00,"description":"Saque"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("POST com tipo inválido deve retornar 400")
    void postComTipoInvalidoDeveRetornar400() throws Exception {
        var body = """
                {"type":"FOO","amount":10.00,"description":"x"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invariante de domínio violada deve virar 400 (ProblemDetail)")
    void invarianteDeDominioDeveRetornar400() throws Exception {
        when(createTransaction.create(any()))
                .thenThrow(new InvalidTransactionException("destinationUserId é obrigatório em transferência"));

        var body = """
                {"type":"TRANSFER","amount":10.00,"description":"PIX"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("destinationUserId é obrigatório em transferência"));
    }

    // ---------- GET /{id} ----------

    @Test
    @DisplayName("GET /{id} deve retornar 200 com a transação e o bloco de câmbio")
    void findByIdDeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), "BrasilAPI", TS);
        when(findTransactionById.findById(id, USER))
                .thenReturn(tx(id, TransactionType.PURCHASE, "150.00", TransactionStatus.APPROVED, null, List.of(quote)));

        mockMvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.exchange.quotes[0].currency").value("USD"))
                .andExpect(jsonPath("$.exchange.quotes[0].source").value("BrasilAPI"));
    }

    @Test
    @DisplayName("GET /{id} inexistente (ou de outro dono) deve retornar 404")
    void findByIdInexistenteDeveRetornar404() throws Exception {
        var id = UUID.randomUUID();
        when(findTransactionById.findById(id, USER)).thenThrow(new TransactionNotFoundException(id));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} com id não-UUID deve retornar 400")
    void findByIdComIdInvalidoDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "nao-eh-uuid")).andExpect(status().isBadRequest());
    }

    // ---------- GET (lista) ----------

    @Test
    @DisplayName("GET lista deve retornar 200 com a página de transações do usuário autenticado")
    void listDeveRetornar200() throws Exception {
        var tx = tx(UUID.randomUUID(), TransactionType.DEPOSIT, "500.00", TransactionStatus.APPROVED, null, List.of());
        when(listTransactions.list(any())).thenReturn(new PageResult<>(List.of(tx), 1, 1, 0, 20));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("GET lista com período invertido deve retornar 400")
    void listComPeriodoInvertidoDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("from", "2026-11-10")
                        .param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("from não pode ser depois de to"));
    }

    @Test
    @DisplayName("GET lista com size acima do limite deve retornar 400 indicando o campo size")
    void listComSizeAcimaDoLimiteDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    // ---------- PATCH ----------

    @Test
    @DisplayName("PATCH deve retornar 200 com os metadados atualizados")
    void patchDeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var updated = Transaction.rehydrate(id, USER, null, TransactionType.PURCHASE, new BigDecimal("150.00"),
                "Mercado corrigido", TransactionCategory.SHOPPING, TransactionStatus.APPROVED, null,
                TS, List.of(), TS, TS, null, 1L);
        when(updateTransactionMetadata.update(any())).thenReturn(updated);

        var body = """
                {"description":"Mercado corrigido","category":"SHOPPING"}
                """;

        mockMvc.perform(patch("/api/v1/transactions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mercado corrigido"))
                .andExpect(jsonPath("$.category").value("SHOPPING"));
    }

    @Test
    @DisplayName("PATCH sem nenhum campo deve retornar 400")
    void patchSemCamposDeveRetornar400() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH em transação inexistente (ou de outro dono) deve retornar 404")
    void patchInexistenteDeveRetornar404() throws Exception {
        var id = UUID.randomUUID();
        when(updateTransactionMetadata.update(any())).thenThrow(new TransactionNotFoundException(id));

        mockMvc.perform(patch("/api/v1/transactions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SHOPPING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH com conflito de versão deve retornar 409")
    void patchConflitoDeVersaoDeveRetornar409() throws Exception {
        when(updateTransactionMetadata.update(any()))
                .thenThrow(new OptimisticLockingFailureException("conflito"));

        mockMvc.perform(patch("/api/v1/transactions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SHOPPING\"}"))
                .andExpect(status().isConflict());
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("DELETE deve retornar 204")
    void deleteDeveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE em transação inexistente (ou de outro dono) deve retornar 404")
    void deleteInexistenteDeveRetornar404() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new TransactionNotFoundException(id)).when(deleteTransaction).delete(id, USER);

        mockMvc.perform(delete("/api/v1/transactions/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE em transação APPROVED deve retornar 422")
    void deleteApprovedDeveRetornar422() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new TransactionNotDeletableException()).when(deleteTransaction).delete(id, USER);

        mockMvc.perform(delete("/api/v1/transactions/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Transação aprovada não pode ser excluída. Para corrigir, registre uma nova transação."));
    }
}
