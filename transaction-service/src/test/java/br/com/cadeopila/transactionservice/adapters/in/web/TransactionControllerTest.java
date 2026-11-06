package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.mapper.TransactionWebMapper;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(TransactionWebMapper.class)
class TransactionControllerTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final Instant TS = Instant.parse("2026-11-06T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTransactionUseCase createTransaction;

    private Transaction tx(UUID id, TransactionType type, String amount, TransactionStatus status,
                           RejectionReason reason, List<ExchangeQuote> quotes) {
        return Transaction.rehydrate(id, USER, null, type, new BigDecimal(amount), "desc",
                TransactionCategory.FOOD, status, reason, TS, quotes, TS, TS, null, 0L);
    }

    @Test
    @DisplayName("POST aprovado deve retornar 201 com Location, status APPROVED, câmbio e saldo")
    void postAprovadoDeveRetornar201() throws Exception {
        var id = UUID.randomUUID();
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), TS);
        var result = new CreateTransactionUseCase.Result(
                tx(id, TransactionType.PURCHASE, "150.00", TransactionStatus.APPROVED, null, List.of(quote)),
                new BigDecimal("14850.00"));
        when(createTransaction.create(any())).thenReturn(result);

        var body = """
                {"userId":"%s","type":"PURCHASE","amount":150.00,"description":"Mercado","category":"FOOD"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/transactions/" + id)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.exchange.available").value(true))
                .andExpect(jsonPath("$.exchange.amountBrl").value(150.00))
                .andExpect(jsonPath("$.exchange.quotes[0].currency").value("USD"))
                .andExpect(jsonPath("$.account.balanceAfter").value(14850.00));
    }

    @Test
    @DisplayName("POST rejeitado deve retornar 201 com status REJECTED, motivo e saldo consultado")
    void postRejeitadoDeveRetornar201() throws Exception {
        var id = UUID.randomUUID();
        var quote = new ExchangeQuote("USD", new BigDecimal("5.43"), new BigDecimal("7.37"), TS);
        var result = new CreateTransactionUseCase.Result(
                tx(id, TransactionType.WITHDRAWAL, "40.00", TransactionStatus.REJECTED,
                        RejectionReason.INSUFFICIENT_BALANCE, List.of(quote)),
                new BigDecimal("30.00"));
        when(createTransaction.create(any())).thenReturn(result);

        var body = """
                {"userId":"%s","type":"WITHDRAWAL","amount":40.00,"description":"Saque"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.account.balanceAfter").value(30.00));
    }

    @Test
    @DisplayName("Câmbio indisponível deve devolver available=false e mensagem")
    void postComCambioIndisponivel() throws Exception {
        var id = UUID.randomUUID();
        var result = new CreateTransactionUseCase.Result(
                tx(id, TransactionType.DEPOSIT, "500.00", TransactionStatus.APPROVED, null, List.of()),
                new BigDecimal("1500.00"));
        when(createTransaction.create(any())).thenReturn(result);

        var body = """
                {"userId":"%s","type":"DEPOSIT","amount":500.00,"description":"Depósito"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchange.available").value(false))
                .andExpect(jsonPath("$.exchange.message").value("Conversão indisponível no momento"))
                .andExpect(jsonPath("$.exchange.quotes").isEmpty());
    }

    @Test
    @DisplayName("POST sem userId deve retornar 400 indicando o campo")
    void postSemUserIdDeveRetornar400() throws Exception {
        var body = """
                {"type":"DEPOSIT","amount":500.00,"description":"Depósito"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("userId"));
    }

    @Test
    @DisplayName("POST com valor negativo deve retornar 400 indicando o campo amount")
    void postComValorNegativoDeveRetornar400() throws Exception {
        var body = """
                {"userId":"%s","type":"DEPOSIT","amount":-1,"description":"Depósito"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("amount"));
    }

    @Test
    @DisplayName("POST com tipo inválido deve retornar 400")
    void postComTipoInvalidoDeveRetornar400() throws Exception {
        var body = """
                {"userId":"%s","type":"FOO","amount":10.00,"description":"x"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invariante de domínio violada deve virar 400 (ProblemDetail)")
    void invarianteDeDominioDeveRetornar400() throws Exception {
        when(createTransaction.create(any()))
                .thenThrow(new InvalidTransactionException("destinationUserId é obrigatório em transferência"));

        var body = """
                {"userId":"%s","type":"TRANSFER","amount":10.00,"description":"PIX"}
                """.formatted(USER);

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("destinationUserId é obrigatório em transferência"));
    }
}
