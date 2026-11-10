package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotDeletableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T18:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T18:00:01Z");

    private NewTransaction deposit() {
        return new NewTransaction(OWNER, TransactionType.DEPOSIT, new BigDecimal("100.00"), "Depósito", null, null);
    }

    @Test
    @DisplayName("Fábrica approved deve marcar APPROVED sem motivo de rejeição")
    void approvedDeveMarcarAprovada() {
        var tx = Transaction.approved(deposit(), OCCURRED_AT, List.of(), NOW);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(tx.getRejectionReason()).isNull();
        assertThat(tx.getId()).isNull();
        assertThat(tx.getCreatedAt()).isEqualTo(NOW);
        assertThat(tx.getUpdatedAt()).isEqualTo(NOW);
        assertThat(tx.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(tx.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Fábrica rejected deve marcar REJECTED com o motivo informado")
    void rejectedDeveMarcarRejeitada() {
        var tx = Transaction.rejected(deposit(), RejectionReason.INSUFFICIENT_BALANCE, OCCURRED_AT, List.of(), NOW);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(tx.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("Fábrica rejected deve recusar motivo nulo")
    void rejectedDeveRecusarMotivoNulo() {
        var data = deposit();
        assertThatThrownBy(() -> Transaction.rejected(data, null, OCCURRED_AT, List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rehydrate deve preservar os valores fornecidos (imutabilidade)")
    void rehydrateDevePreservarValores() {
        var id = UUID.randomUUID();
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("18.41"), "BrasilAPI", NOW);

        var tx = Transaction.rehydrate(id, OWNER, null, TransactionType.PURCHASE, new BigDecimal("100.00"),
                "Mercado", TransactionCategory.FOOD, TransactionStatus.APPROVED, null, OCCURRED_AT,
                List.of(quote), NOW, NOW, null, 3L);

        assertThat(tx.getId()).isEqualTo(id);
        assertThat(tx.getUserId()).isEqualTo(OWNER);
        assertThat(tx.getType()).isEqualTo(TransactionType.PURCHASE);
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.FOOD);
        assertThat(tx.getExchangeQuotes()).containsExactly(quote);
        assertThat(tx.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("A lista de cotações exposta deve ser imutável")
    void listaDeCotacoesDeveSerImutavel() {
        var tx = Transaction.approved(deposit(), OCCURRED_AT, List.of(), NOW);

        assertThatThrownBy(() -> tx.getExchangeQuotes().add(
                new ExchangeQuote("USD", BigDecimal.ONE, BigDecimal.ONE, "BrasilAPI", NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("isOutflow deve refletir o tipo (depósito é entrada; demais são saída)")
    void isOutflowDeveRefletirTipo() {
        var deposit = Transaction.approved(deposit(), OCCURRED_AT, List.of(), NOW);
        var withdrawal = Transaction.approved(
                new NewTransaction(OWNER, TransactionType.WITHDRAWAL, new BigDecimal("10.00"), "Saque", null, null),
                OCCURRED_AT, List.of(), NOW);

        assertThat(deposit.isOutflow()).isFalse();
        assertThat(withdrawal.isOutflow()).isTrue();
    }

    private NewTransaction purchase() {
        return new NewTransaction(OWNER, TransactionType.PURCHASE, new BigDecimal("150.00"), "Mercado",
                TransactionCategory.FOOD, null);
    }

    @Test
    @DisplayName("updateMetadata deve alterar só descrição e categoria, preservando os dados financeiros")
    void updateMetadataDeveAlterarSoMetadados() {
        var original = Transaction.approved(purchase(), OCCURRED_AT, List.of(), NOW);
        var later = NOW.plusSeconds(3600);

        var updated = original.updateMetadata("Mercado corrigido", TransactionCategory.SHOPPING, later);

        assertThat(updated.getDescription()).isEqualTo("Mercado corrigido");
        assertThat(updated.getCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(updated.getUpdatedAt()).isEqualTo(later);
        // imutáveis
        assertThat(updated.getAmount()).isEqualByComparingTo("150.00");
        assertThat(updated.getType()).isEqualTo(TransactionType.PURCHASE);
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(updated.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(updated.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("updateMetadata deve manter o campo não informado (nulo)")
    void updateMetadataDeveManterCampoNulo() {
        var original = Transaction.approved(purchase(), OCCURRED_AT, List.of(), NOW);

        var soCategoria = original.updateMetadata(null, TransactionCategory.SHOPPING, NOW);
        assertThat(soCategoria.getDescription()).isEqualTo("Mercado");
        assertThat(soCategoria.getCategory()).isEqualTo(TransactionCategory.SHOPPING);

        var soDescricao = original.updateMetadata("Outra", null, NOW);
        assertThat(soDescricao.getDescription()).isEqualTo("Outra");
        assertThat(soDescricao.getCategory()).isEqualTo(TransactionCategory.FOOD);
    }

    @Test
    @DisplayName("updateMetadata deve rejeitar descrição em branco")
    void updateMetadataDeveRejeitarDescricaoEmBranco() {
        var original = Transaction.approved(purchase(), OCCURRED_AT, List.of(), NOW);

        assertThatThrownBy(() -> original.updateMetadata("   ", null, NOW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    @DisplayName("markAsDeleted deve preencher deletedAt quando a transação é REJECTED")
    void markAsDeletedDeveExcluirRejeitada() {
        var rejected = Transaction.rejected(deposit(), RejectionReason.INSUFFICIENT_BALANCE, OCCURRED_AT, List.of(), NOW);
        var later = NOW.plusSeconds(60);

        var deleted = rejected.markAsDeleted(later);

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(later);
        assertThat(deleted.getUpdatedAt()).isEqualTo(later);
        assertThat(deleted.getStatus()).isEqualTo(TransactionStatus.REJECTED);
    }

    @Test
    @DisplayName("markAsDeleted deve recusar transação APPROVED (422)")
    void markAsDeletedDeveRecusarApproved() {
        var approved = Transaction.approved(deposit(), OCCURRED_AT, List.of(), NOW);

        assertThatThrownBy(() -> approved.markAsDeleted(NOW))
                .isInstanceOf(TransactionNotDeletableException.class);
    }
}
