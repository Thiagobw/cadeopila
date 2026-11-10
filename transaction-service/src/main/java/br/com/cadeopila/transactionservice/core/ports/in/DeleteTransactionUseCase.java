package br.com.cadeopila.transactionservice.core.ports.in;

import java.util.UUID;

public interface DeleteTransactionUseCase {

    /** Exclui logicamente, garantindo que a transação pertença a {@code userId} (do contrário, 404). */
    void delete(UUID id, UUID userId);
}
