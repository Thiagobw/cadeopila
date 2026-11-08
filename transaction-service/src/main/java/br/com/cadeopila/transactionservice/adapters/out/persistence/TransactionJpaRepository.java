package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionJpaRepository
        extends JpaRepository<TransactionJpaEntity, UUID>, JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT SUM(CASE WHEN t.type = :depositType THEN t.amount ELSE (0 - t.amount) END)
            FROM TransactionJpaEntity t
            WHERE t.userId = :userId
              AND t.status = :status
              AND t.deletedAt IS NULL
            """)
    BigDecimal sumSignedApproved(@Param("userId") UUID userId,
                                 @Param("status") TransactionStatus status,
                                 @Param("depositType") TransactionType depositType);

    @Query("""
            SELECT SUM(t.amount) FROM TransactionJpaEntity t
            WHERE t.userId = :userId
              AND t.status = :status
              AND t.type IN :types
              AND t.occurredAt >= :start
              AND t.occurredAt < :end
              AND t.deletedAt IS NULL
            """)
    BigDecimal sumOutflows(@Param("userId") UUID userId,
                           @Param("status") TransactionStatus status,
                           @Param("types") Collection<TransactionType> types,
                           @Param("start") Instant start,
                           @Param("end") Instant end);
}
