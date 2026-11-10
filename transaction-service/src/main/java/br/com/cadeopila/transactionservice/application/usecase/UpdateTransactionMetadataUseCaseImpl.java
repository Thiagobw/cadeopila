package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.UpdateTransactionMetadataUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class UpdateTransactionMetadataUseCaseImpl implements UpdateTransactionMetadataUseCase {

    private final TransactionRepositoryPort transactionRepository;
    private final Clock clock;

    public UpdateTransactionMetadataUseCaseImpl(TransactionRepositoryPort transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Transaction update(Command command) {
        var transaction = transactionRepository.findActiveById(command.id())
                .orElseThrow(() -> new TransactionNotFoundException(command.id()));
        var updated = transaction.updateMetadata(command.description(), command.category(), clock.instant());
        return transactionRepository.save(updated);
    }
}
