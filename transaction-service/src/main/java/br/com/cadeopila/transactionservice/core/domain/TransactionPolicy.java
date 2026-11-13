package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Política de aprovação de saídas (saque, transferência, compra). Depósito não passa por limites.
 * A primeira regra violada define o motivo; sem violação, a transação é aprovada.
 *
 * <p>Ordem: limite por transação → saldo atual → limite diário. O saldo atual e o gasto do dia são
 * calculados pelo caso de uso a partir do histórico aprovado do MS2 (a Mock é somente leitura).</p>
 *
 * <p>Fallback defensivo: um limite nulo (a MockAPI pode omitir) faz aquela restrição não se aplicar —
 * evita {@code NullPointerException}/500 sem bloquear transações por falha de dado da origem.</p>
 */
public final class TransactionPolicy {

    public Optional<RejectionReason> evaluate(TransactionType type,
                                              BigDecimal amount,
                                              BigDecimal currentBalance,
                                              BigDecimal transactionLimit,
                                              BigDecimal dailyLimit,
                                              BigDecimal approvedOutflowsToday) {
        if (!type.isOutflow()) {
            return Optional.empty();
        }
        // Limites podem vir nulos da MockAPI: nesse caso a restrição não se aplica (evita 500 e bloqueio
        // indevido). O saldo atual continua protegendo contra gasto acima do disponível.
        if (transactionLimit != null && amount.compareTo(transactionLimit) > 0) {
            return Optional.of(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
        }
        if (amount.compareTo(currentBalance) > 0) {
            return Optional.of(RejectionReason.INSUFFICIENT_BALANCE);
        }
        if (dailyLimit != null && approvedOutflowsToday.add(amount).compareTo(dailyLimit) > 0) {
            return Optional.of(RejectionReason.DAILY_LIMIT_EXCEEDED);
        }
        return Optional.empty();
    }
}
