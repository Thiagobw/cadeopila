package br.com.cadeopila.transactionservice.core.domain;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros da listagem de transações. {@code userId} é obrigatório (lista as transações de um usuário);
 * {@code type}, {@code status}, {@code from} e {@code to} são opcionais. Período por data inclusiva;
 * início depois do fim é inválido. Transações excluídas nunca entram na busca.
 */
public record TransactionQuery(
        UUID userId,
        TransactionType type,
        TransactionStatus status,
        LocalDate from,
        LocalDate to,
        PageQuery page
) {
    public TransactionQuery {
        if (userId == null) {
            throw new InvalidTransactionFilterException("userId é obrigatório");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionFilterException("from não pode ser depois de to");
        }
    }
}
