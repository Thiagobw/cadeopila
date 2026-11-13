package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projeção crua (read-model) de uma transação APROVADA e ativa de um período, com apenas os campos
 * que a análise de despesas precisa. A agregação por nível 1, subcategoria, dia e os cálculos de
 * fricção acontecem <b>em memória</b> no caso de uso (nunca via {@code GROUP BY} de data no banco),
 * para que o agrupamento por dia respeite o fuso {@code app.time-zone}.
 *
 * <p>Inclui depósitos e saídas: o tipo distingue receita (DEPOSIT) de despesa (demais). Para
 * depósitos, {@code categoryLevel1}/{@code categoryLevel2}/{@code fixed} são irrelevantes.</p>
 */
public record AnalyticsRow(
        TransactionType type,
        BigDecimal amount,
        CategoryLevel1 categoryLevel1,
        String categoryLevel2,
        boolean fixed,
        Instant occurredAt
) {
}
