package br.com.cadeopila.transactionservice.core.exception;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID id) {
        super("Transação não encontrada com id: " + id);
    }
}
