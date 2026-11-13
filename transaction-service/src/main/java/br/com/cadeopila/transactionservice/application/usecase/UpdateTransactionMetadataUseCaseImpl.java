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
        // Não-dono é tratado como inexistente (404): não revela a existência de transações alheias.
        var transaction = transactionRepository.findActiveById(command.id())
                .filter(found -> found.getUserId().equals(command.userId()))
                .orElseThrow(() -> new TransactionNotFoundException(command.id()));
        var updated = transaction.updateMetadata(command.description(), command.category(), clock.instant());
        return transactionRepository.save(updated);
    }
}
