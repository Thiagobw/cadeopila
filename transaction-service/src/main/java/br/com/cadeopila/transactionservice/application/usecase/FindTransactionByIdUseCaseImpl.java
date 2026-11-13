package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.FindTransactionByIdUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FindTransactionByIdUseCaseImpl implements FindTransactionByIdUseCase {

    private final TransactionRepositoryPort transactionRepository;

    public FindTransactionByIdUseCaseImpl(TransactionRepositoryPort transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Transaction findById(UUID id, UUID userId) {
        // Não-dono é tratado como inexistente (404): não revela a existência de transações alheias.
        return transactionRepository.findActiveById(id)
                .filter(transaction -> transaction.getUserId().equals(userId))
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
