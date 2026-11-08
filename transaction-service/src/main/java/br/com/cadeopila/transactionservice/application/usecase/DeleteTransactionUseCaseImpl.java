package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.DeleteTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class DeleteTransactionUseCaseImpl implements DeleteTransactionUseCase {

    private final TransactionRepositoryPort transactionRepository;
    private final Clock clock;

    public DeleteTransactionUseCaseImpl(TransactionRepositoryPort transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var transaction = transactionRepository.findActiveById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        transactionRepository.save(transaction.markAsDeleted(clock.instant()));
    }
}
