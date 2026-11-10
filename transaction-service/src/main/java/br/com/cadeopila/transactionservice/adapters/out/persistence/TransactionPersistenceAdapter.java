package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.adapters.out.persistence.mapper.TransactionPersistenceMapper;
import br.com.cadeopila.transactionservice.core.domain.AnalyticsRow;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private static final List<TransactionType> OUTFLOW_TYPES =
            Arrays.stream(TransactionType.values()).filter(TransactionType::isOutflow).toList();

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    /** Relatório lê em ordem cronológica (ascendente), ao contrário da listagem paginada (desc). */
    private static final Sort REPORT_SORT =
            Sort.by(Sort.Order.asc("occurredAt"), Sort.Order.asc("id"));

    private final TransactionJpaRepository jpaRepository;
    private final TransactionPersistenceMapper mapper;
    private final ZoneId zoneId;

    public TransactionPersistenceAdapter(TransactionJpaRepository jpaRepository,
                                         TransactionPersistenceMapper mapper,
                                         ZoneId zoneId) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.zoneId = zoneId;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var saved = jpaRepository.saveAndFlush(mapper.toEntity(transaction));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transaction> findActiveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Transaction> search(TransactionQuery query) {
        var pageable = PageRequest.of(query.page().page(), query.page().size(), DEFAULT_SORT);
        var page = jpaRepository.findAll(TransactionSpecifications.matching(query, zoneId), pageable);
        var content = page.getContent().stream().map(mapper::toDomain).toList();
        return new PageResult<>(content, page.getTotalElements(), page.getTotalPages(),
                query.page().page(), query.page().size());
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

    @Override
    public List<Transaction> findActiveForReport(UUID userId, LocalDate from, LocalDate to) {
        // Reusa a Specification de filtro (usuário/período, exclui soft-deleted) sem tipo/status — assim
        // o relatório traz APPROVED e REJECTED. A validação de período (from > to) vem do próprio
        // TransactionQuery. Sem Pageable: o relatório exporta todas as linhas do período.
        var query = new TransactionQuery(userId, null, null, from, to, null);
        return jpaRepository.findAll(TransactionSpecifications.matching(query, zoneId), REPORT_SORT)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AnalyticsRow> findApprovedByPeriod(UUID userId, Instant startInclusive, Instant endExclusive) {
        return jpaRepository
                .findApprovedByPeriod(userId, TransactionStatus.APPROVED, startInclusive, endExclusive)
                .stream()
                .map(TransactionPersistenceAdapter::toAnalyticsRow)
                .toList();
    }

    private static AnalyticsRow toAnalyticsRow(TransactionJpaEntity e) {
        var level1 = e.getCategoryLevel1() != null
                ? e.getCategoryLevel1()
                : CategoryLevel1.defaultFor(e.getCategory());
        return new AnalyticsRow(e.getType(), e.getAmount(), level1, e.getCategoryLevel2(),
                e.isFixed(), e.getOccurredAt());
    }
}
