package br.com.cadeopila.transactionservice.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPolicyTest {

    private final TransactionPolicy policy = new TransactionPolicy();

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private AccountSnapshot account(String balance, String dailyLimit, String transactionLimit) {
        return new AccountSnapshot("1", UUID.randomUUID(),
                new BigDecimal(balance), new BigDecimal(dailyLimit), new BigDecimal(transactionLimit));
    }

    @Test
    @DisplayName("Depósito sempre aprova, mesmo acima de qualquer limite")
    void depositoSempreAprova() {
        var result = policy.evaluate(TransactionType.DEPOSIT, new BigDecimal("999999.00"),
                account("0.00", "1.00", "1.00"), ZERO);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Aprova saída dentro do limite por transação, do saldo e do limite diário")
    void aprovaDentroDosLimites() {
        var result = policy.evaluate(TransactionType.WITHDRAWAL, new BigDecimal("100.00"),
                account("1000.00", "800.00", "300.00"), new BigDecimal("200.00"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Rejeita por limite por transação quando o valor excede o teto da transação")
    void rejeitaPorLimitePorTransacao() {
        var result = policy.evaluate(TransactionType.PURCHASE, new BigDecimal("350.00"),
                account("1000.00", "800.00", "300.00"), ZERO);

        assertThat(result).contains(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Rejeita por saldo insuficiente quando o valor cabe no limite mas excede o saldo")
    void rejeitaPorSaldoInsuficiente() {
        var result = policy.evaluate(TransactionType.WITHDRAWAL, new BigDecimal("100.00"),
                account("50.00", "800.00", "300.00"), ZERO);

        assertThat(result).contains(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("Rejeita por limite diário quando a soma do dia mais o valor ultrapassa o teto diário")
    void rejeitaPorLimiteDiario() {
        var result = policy.evaluate(TransactionType.TRANSFER, new BigDecimal("100.00"),
                account("1000.00", "800.00", "300.00"), new BigDecimal("750.00"));

        assertThat(result).contains(RejectionReason.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("O limite por transação tem precedência sobre saldo e limite diário")
    void limitePorTransacaoTemPrecedencia() {
        var result = policy.evaluate(TransactionType.WITHDRAWAL, new BigDecimal("100.00"),
                account("10.00", "1.00", "5.00"), new BigDecimal("999.00"));

        assertThat(result).contains(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Valores exatamente iguais aos limites não são rejeitados (compareTo, não equals)")
    void valoresNoLimiteNaoRejeitam() {
        var result = policy.evaluate(TransactionType.WITHDRAWAL, new BigDecimal("300.00"),
                account("300.00", "300.00", "300.00"), ZERO);

        assertThat(result).isEmpty();
    }
}
