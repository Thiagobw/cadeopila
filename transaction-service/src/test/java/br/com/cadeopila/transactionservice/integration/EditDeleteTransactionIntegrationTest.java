package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EditDeleteTransactionIntegrationTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID DESTINATION = UUID.randomUUID();
    private static final Instant TS = Instant.parse("2026-11-06T13:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepositoryPort repository;

    private Transaction approvedPurchase() {
        var data = new NewTransaction(USER, TransactionType.PURCHASE, new BigDecimal("150.00"), "Mercado",
                TransactionCategory.FOOD, null);
        return Transaction.approved(data, TS, List.of(), TS);
    }

    private Transaction rejectedWithdrawal() {
        var data = new NewTransaction(USER, TransactionType.WITHDRAWAL, new BigDecimal("40.00"), "Saque", null, null);
        return Transaction.rejected(data, RejectionReason.INSUFFICIENT_BALANCE, TS, List.of(), TS);
    }

    @Test
    @DisplayName("PATCH em transação APPROVED altera só metadados, sem afetar valor nem status")
    void patchApprovedAlteraSoMetadados() throws Exception {
        var saved = repository.save(approvedPurchase());

        mockMvc.perform(patch("/api/v1/transactions/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Mercado corrigido\",\"category\":\"SHOPPING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mercado corrigido"))
                .andExpect(jsonPath("$.category").value("SHOPPING"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("DELETE em transação REJECTED faz soft delete; depois GET devolve 404")
    void deleteRejeitadaFazSoftDelete() throws Exception {
        var saved = repository.save(rejectedWithdrawal());

        mockMvc.perform(delete("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE em transação APPROVED é bloqueado com 422")
    void deleteApprovedRetorna422() throws Exception {
        var saved = repository.save(approvedPurchase());

        mockMvc.perform(delete("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isUnprocessableEntity());

        // continua ativa
        mockMvc.perform(get("/api/v1/transactions/{id}", saved.getId()))
                .andExpect(status().isOk());
    }
}
