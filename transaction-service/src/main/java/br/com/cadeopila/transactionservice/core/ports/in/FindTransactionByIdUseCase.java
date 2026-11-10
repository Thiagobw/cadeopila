package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.Transaction;

import java.util.UUID;

public interface FindTransactionByIdUseCase {

    /** Busca por id, garantindo que a transação pertença a {@code userId} (do contrário, 404). */
    Transaction findById(UUID id, UUID userId);
}
