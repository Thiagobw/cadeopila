package br.com.cadeopila.transactionservice.core.exception;

/**
 * Tentativa de excluir uma transação que não está REJECTED. É uma regra de negócio que impede a
 * operação (mapeada para 422), não um conflito de dados.
 */
public class TransactionNotDeletableException extends RuntimeException {

    public TransactionNotDeletableException() {
        super("Transação aprovada não pode ser excluída. Para corrigir, registre uma nova transação.");
    }
}
