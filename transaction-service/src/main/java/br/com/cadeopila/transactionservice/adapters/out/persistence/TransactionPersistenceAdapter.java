package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.adapters.out.persistence.mapper.TransactionPersistenceMapper;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private static final List<TransactionType> OUTFLOW_TYPES =
            Arrays.stream(TransactionType.values()).filter(TransactionType::isOutflow).toList();

    private final TransactionJpaRepository jpaRepository;
    private final TransactionPersistenceMapper mapper;

    public TransactionPersistenceAdapter(TransactionJpaRepository jpaRepository,
                                         TransactionPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var saved = jpaRepository.saveAndFlush(mapper.toEntity(transaction));
        return mapper.toDomain(saved);
    }

    @Override
    public BigDecimal sumApprovedSignedAmount(UUID userId) {
        var sum = jpaRepository.sumSignedApproved(userId, TransactionStatus.APPROVED, TransactionType.DEPOSIT);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal sumApprovedOutflowsOfDay(UUID userId, Instant dayStartInclusive, Instant dayEndExclusive) {
        var sum = jpaRepository.sumOutflows(
                userId, TransactionStatus.APPROVED, OUTFLOW_TYPES, dayStartInclusive, dayEndExclusive);
        return sum != null ? sum : BigDecimal.ZERO;
    }
}
