package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneId;
import java.util.ArrayList;

/**
 * Filtros dinâmicos da listagem via Criteria (evita o problema de "could not determine data type of
 * parameter" do PostgreSQL com JPQL `:param is null or ...`). Período em datas inclusivas, convertido
 * para instantes no fuso configurado. Sempre exclui as transações excluídas (soft delete).
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<TransactionJpaEntity> matching(TransactionQuery query, ZoneId zoneId) {
        return (root, criteriaQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), query.userId()));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (query.type() != null) {
                predicates.add(cb.equal(root.get("type"), query.type()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),
                        query.from().atStartOfDay(zoneId).toInstant()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"),
                        query.to().plusDays(1).atStartOfDay(zoneId).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
