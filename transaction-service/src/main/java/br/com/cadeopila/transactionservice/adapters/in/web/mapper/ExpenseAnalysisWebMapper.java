package br.com.cadeopila.transactionservice.adapters.in.web.mapper;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse.CategoryLevel1Response;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse.DailyTotalResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse.OverviewResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse.SubcategoryCutResponse;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.ExpenseSummary;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Traduz o parâmetro de entrada {@code month} (formato MM-YYYY) para {@link YearMonth} e o resultado
 * do caso de uso para o DTO de resposta. Mantém o controller fino e a inteligência no caso de uso.
 */
@Component
public class ExpenseAnalysisWebMapper {

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MM-yyyy", Locale.ROOT);

    /** {@code null}/em branco → mês atual (resolvido no caso de uso). Formato inválido → 400. */
    public YearMonth toYearMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.strip(), MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new InvalidTransactionFilterException("month deve estar no formato MM-YYYY (ex.: 11-2026)");
        }
    }

    public ExpenseAnalysisResponse toResponse(ExpenseSummary summary) {
        var overview = summary.overview();
        return new ExpenseAnalysisResponse(
                MONTH_FORMAT.format(summary.month()),
                new OverviewResponse(overview.totalIncome(), overview.totalExpenses(), overview.freeBalance()),
                summary.byLevel1().stream()
                        .map(b -> new CategoryLevel1Response(b.level1(), b.spent(), b.percentOfIncome()))
                        .toList(),
                summary.topCuts().stream()
                        .map(c -> new SubcategoryCutResponse(c.subcategory(), c.spent()))
                        .toList(),
                summary.fixedOperationalCost(),
                summary.byDay().stream()
                        .map(d -> new DailyTotalResponse(d.date(), d.total()))
                        .toList(),
                summary.showBlackHoleWarning());
    }
}
