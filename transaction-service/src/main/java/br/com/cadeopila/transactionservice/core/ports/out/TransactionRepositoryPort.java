package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.AnalyticsRow;
import br.com.cadeopila.transactionservice.core.domain.PageResult;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {

    Transaction save(Transaction transaction);

    /** Busca a transação ativa (não excluída) por id. */
    Optional<Transaction> findActiveById(UUID id);

    /** Listagem paginada das transações ativas do usuário, aplicando os filtros da query. */
    PageResult<Transaction> search(TransactionQuery query);

    /**
     * Variação de saldo acumulada no histórico aprovado (não excluído) do usuário: depósitos somam,
     * saídas subtraem. Somada ao saldo inicial da Mock, dá o saldo atual. Nunca retorna {@code null}.
     */
    BigDecimal sumApprovedSignedAmount(UUID userId);

    /**
     * Soma dos valores das saídas APPROVED (não excluídas) do usuário cujo {@code occurredAt}
     * está em [início, fim), para checar o limite diário. Nunca retorna {@code null}.
     */
    BigDecimal sumApprovedOutflowsOfDay(UUID userId, Instant dayStartInclusive, Instant dayEndExclusive);

    /**
     * Linhas cruas das transações APROVADAS e ativas (não excluídas) do usuário cujo {@code occurredAt}
     * está em [início, fim) — depósitos e saídas. O agrupamento por dia/mês acontece em memória no caso
     * de uso (no fuso da aplicação), nunca via {@code GROUP BY} de data no banco. Nunca retorna {@code null}.
     */
    List<AnalyticsRow> findApprovedByPeriod(UUID userId, Instant startInclusive, Instant endExclusive);

    /**
     * Transações ativas (não excluídas) do usuário no período {@code [from, to]} (datas inclusivas, no
     * fuso da aplicação), usadas no relatório (Pedido 7). Inclui APPROVED <b>e</b> REJECTED (o relatório
     * tem colunas de status/motivo) e ordena por {@code occurredAt} crescente. Nunca retorna {@code null}.
     */
    List<Transaction> findActiveForReport(UUID userId, LocalDate from, LocalDate to);
}
