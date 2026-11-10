package br.com.cadeopila.transactionservice.core.exception;

/**
 * Combinação inválida de filtros na listagem (ex.: período com início depois do fim). Mapeada para 400.
 */
public class InvalidTransactionFilterException extends RuntimeException {

    public InvalidTransactionFilterException(String message) {
        super(message);
    }
}
