package br.com.cadeopila.transactionservice.core.domain;

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
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("18.41"), NOW);

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
                new ExchangeQuote("USD", BigDecimal.ONE, BigDecimal.ONE, NOW)))
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
}
