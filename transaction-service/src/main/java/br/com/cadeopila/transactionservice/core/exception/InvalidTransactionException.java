package br.com.cadeopila.transactionservice.core.exception;

/**
 * Invariante de domínio violada na criação/edição de uma transação (ex.: transferência
 * sem destino, compra sem categoria, valor não positivo). Mapeada para 400 no adapter web.
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
