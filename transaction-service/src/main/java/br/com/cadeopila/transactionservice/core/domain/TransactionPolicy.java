package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Política de aprovação de saídas (saque, transferência, compra). Depósito não passa por limites.
 * A primeira regra violada define o motivo da rejeição; sem violação, a transação é aprovada.
 *
 * <p>Ordem: limite por transação → saldo → limite diário. O "gasto do dia" vem do nosso banco.</p>
 */
public final class TransactionPolicy {

    public Optional<RejectionReason> evaluate(TransactionType type,
                                              BigDecimal amount,
                                              AccountSnapshot account,
                                              BigDecimal approvedOutflowsToday) {
        if (!type.isOutflow()) {
            return Optional.empty();
        }
        if (amount.compareTo(account.transactionLimit()) > 0) {
            return Optional.of(RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
        }
        if (amount.compareTo(account.balance()) > 0) {
            return Optional.of(RejectionReason.INSUFFICIENT_BALANCE);
        }
        if (approvedOutflowsToday.add(amount).compareTo(account.dailyLimit()) > 0) {
            return Optional.of(RejectionReason.DAILY_LIMIT_EXCEEDED);
        }
        return Optional.empty();
    }
}
