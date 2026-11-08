package br.com.cadeopila.transactionservice.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPolicyTest {

    private final TransactionPolicy policy = new TransactionPolicy();

    private Optional<RejectionReason> evaluate(TransactionType type, String amount, String currentBalance,
                                               String transactionLimit, String dailyLimit, String outflowsToday) {
        return policy.evaluate(type, new BigDecimal(amount), new BigDecimal(currentBalance),
                new BigDecimal(transactionLimit), new BigDecimal(dailyLimit), new BigDecimal(outflowsToday));
    }

    @Test
    @DisplayName("Depósito sempre aprova, mesmo acima de qualquer limite")
    void depositoSempreAprova() {
        assertThat(evaluate(TransactionType.DEPOSIT, "999999.00", "0.00", "1.00", "1.00", "0")).isEmpty();
    }

    @Test
    @DisplayName("Aprova saída dentro do limite por transação, do saldo atual e do limite diário")
    void aprovaDentroDosLimites() {
        assertThat(evaluate(TransactionType.WITHDRAWAL, "100.00", "1000.00", "300.00", "800.00", "200.00")).isEmpty();
    }

    @Test
    @DisplayName("Rejeita por limite por transação quando o valor excede o teto da transação")
    void rejeitaPorLimitePorTransacao() {
        assertThat(evaluate(TransactionType.PURCHASE, "350.00", "1000.00", "300.00", "800.00", "0"))
                .contains(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Rejeita por saldo insuficiente quando o valor cabe no limite mas excede o saldo atual")
    void rejeitaPorSaldoInsuficiente() {
        assertThat(evaluate(TransactionType.WITHDRAWAL, "100.00", "50.00", "300.00", "800.00", "0"))
                .contains(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("Rejeita por limite diário quando a soma do dia mais o valor ultrapassa o teto diário")
    void rejeitaPorLimiteDiario() {
        assertThat(evaluate(TransactionType.TRANSFER, "100.00", "1000.00", "300.00", "800.00", "750.00"))
                .contains(RejectionReason.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("O limite por transação tem precedência sobre saldo e limite diário")
    void limitePorTransacaoTemPrecedencia() {
        assertThat(evaluate(TransactionType.WITHDRAWAL, "100.00", "10.00", "5.00", "1.00", "999.00"))
                .contains(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Valores exatamente iguais aos limites não são rejeitados (compareTo, não equals)")
    void valoresNoLimiteNaoRejeitam() {
        assertThat(evaluate(TransactionType.WITHDRAWAL, "300.00", "300.00", "300.00", "300.00", "0")).isEmpty();
    }
}
