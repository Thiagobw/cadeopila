package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public interface CreateTransactionUseCase {

    Result create(Command command);

    record Command(
            UUID userId,
            TransactionType type,
            BigDecimal amount,
            String description,
            TransactionCategory category,
            UUID destinationUserId
    ) {}

    /**
     * Transação persistida mais o saldo a exibir na resposta do POST ({@code account.balanceAfter}):
     * novo saldo quando aprovada, saldo consultado quando rejeitada, {@code null} quando a conta não respondeu.
     * O saldo não é persistido.
     */
    record Result(Transaction transaction, BigDecimal balanceAfter) {}
}
