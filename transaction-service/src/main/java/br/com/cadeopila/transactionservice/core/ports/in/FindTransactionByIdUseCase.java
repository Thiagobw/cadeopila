package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.Transaction;

import java.util.UUID;

public interface FindTransactionByIdUseCase {

    Transaction findById(UUID id);
}
