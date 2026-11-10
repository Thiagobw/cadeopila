package br.com.cadeopila.transactionservice.core.ports.in;

import java.util.UUID;

public interface DeleteTransactionUseCase {

    void delete(UUID id);
}
