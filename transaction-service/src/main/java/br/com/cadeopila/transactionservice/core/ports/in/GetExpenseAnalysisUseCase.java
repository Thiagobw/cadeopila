package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Análise inteligente de despesas de um mês (Pedido 5). Monta os blocos de decisão para o frontend
 * a partir das transações APROVADAS do período; toda a inteligência dos cálculos vive no caso de uso
 * (camada application), nunca nos controllers.
 */
public interface GetExpenseAnalysisUseCase {

    /** Resumo do mês informado; {@code month} nulo usa o mês atual (no fuso da aplicação). */
    ExpenseSummary summarize(UUID userId, YearMonth month);

    /**
     * Payload da análise.
     *
     * @param month                 mês analisado
     * @param overview              "O Pulso": receitas, despesas e saldo livre
     * @param byLevel1              "Raio-X": gasto e % da receita por grau de necessidade (nível 1)
     * @param topCuts               "Onde Cortar Hoje": maiores subcategorias discricionárias (top 5)
     * @param fixedOperationalCost  "Custo Fixo Operacional": soma das despesas fixas
     * @param byDay                 estatística do PO: total gasto por dia (no fuso)
     * @param showBlackHoleWarning  alerta de ineficiência: gasto em "OUTROS" passou de 5% das despesas
     */
    record ExpenseSummary(
            YearMonth month,
            Overview overview,
            List<CategoryBreakdown> byLevel1,
            List<SubcategoryTotal> topCuts,
            BigDecimal fixedOperationalCost,
            List<DailyTotal> byDay,
            boolean showBlackHoleWarning
    ) {}

    /** Visão geral: total de receitas (depósitos), total de despesas (saídas) e saldo livre. */
    record Overview(BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal freeBalance) {}

    /** Gasto por nível 1 e sua participação na receita total do mês. */
    record CategoryBreakdown(CategoryLevel1 level1, BigDecimal spent, BigDecimal percentOfIncome) {}

    /** Gasto por subcategoria livre (nível 2). */
    record SubcategoryTotal(String subcategory, BigDecimal spent) {}

    /** Gasto total de um dia (data no fuso da aplicação). */
    record DailyTotal(LocalDate date, BigDecimal total) {}
}
