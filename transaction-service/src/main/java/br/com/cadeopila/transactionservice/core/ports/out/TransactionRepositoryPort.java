package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TransactionRepositoryPort {

    Transaction save(Transaction transaction);

    /**
     * Soma dos valores das saídas APPROVED (não excluídas) do usuário cujo {@code occurredAt}
     * está em [início, fim), para checar o limite diário. Nunca retorna {@code null}.
     */
    BigDecimal sumApprovedOutflowsOfDay(UUID userId, Instant dayStartInclusive, Instant dayEndExclusive);
}
