package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.AnalyticsRow;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Análise inteligente de despesas (Pedido 5). Busca as transações APROVADAS do mês e agrega
 * <b>em memória</b> (Java Streams/coleções), aplicando o fuso {@code app.time-zone} no agrupamento
 * por dia — nunca {@code GROUP BY} de data no banco (evita divergência H2/PostgreSQL e erro de fuso).
 *
 * <p>Só saídas aprovadas (saque/transferência/compra) são despesa; depósitos compõem a receita, usada
 * como base das porcentagens e do saldo livre. Toda a matemática mora aqui, não nos controllers.</p>
 */
@Service
public class GetExpenseAnalysisUseCaseImpl implements GetExpenseAnalysisUseCase {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final int TOP_CUTS_LIMIT = 5;
    /** Gasto em "OUTROS" acima deste percentual das despesas dispara o alerta do "buraco negro". */
    private static final BigDecimal BLACK_HOLE_THRESHOLD = new BigDecimal("0.05");
    private static final String OUTROS_SUBCATEGORY = "OUTROS";
    private static final String NO_SUBCATEGORY = "SEM_SUBCATEGORIA";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);

    private final TransactionRepositoryPort repository;
    private final Clock clock;
    private final ZoneId zoneId;

    public GetExpenseAnalysisUseCaseImpl(TransactionRepositoryPort repository, Clock clock, ZoneId zoneId) {
        this.repository = repository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseSummary summarize(UUID userId, YearMonth month) {
        YearMonth target = month != null ? month : currentMonth();
        Instant start = target.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant end = target.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant(); // meia-aberta [start, end)

        List<AnalyticsRow> rows = repository.findApprovedByPeriod(userId, start, end);

        var acc = new Aggregates();
        for (AnalyticsRow row : rows) {
            acc.add(row);
        }
        return acc.toSummary(target);
    }

    private YearMonth currentMonth() {
        return YearMonth.from(clock.instant().atZone(zoneId));
    }

    /** Acumuladores da passada única sobre as linhas do mês. */
    private final class Aggregates {

        private BigDecimal totalIncome = ZERO;
        private BigDecimal totalExpenses = ZERO;
        private BigDecimal fixedCost = ZERO;
        private BigDecimal outrosExpense = ZERO;
        private final Map<CategoryLevel1, BigDecimal> byLevel1 = new EnumMap<>(CategoryLevel1.class);
        private final Map<String, BigDecimal> discretionaryByLevel2 = new TreeMap<>();
        private final Map<LocalDate, BigDecimal> byDay = new TreeMap<>();

        void add(AnalyticsRow row) {
            if (!row.type().isOutflow()) { // depósito = receita
                totalIncome = totalIncome.add(row.amount());
                return;
            }
            // saída = despesa
            totalExpenses = totalExpenses.add(row.amount());

            CategoryLevel1 level1 = row.categoryLevel1() != null
                    ? row.categoryLevel1() : CategoryLevel1.DIVIDAS_IMPREVISTOS;
            byLevel1.merge(level1, row.amount(), BigDecimal::add);

            LocalDate day = row.occurredAt().atZone(zoneId).toLocalDate();
            byDay.merge(day, row.amount(), BigDecimal::add);

            if (row.fixed()) {
                fixedCost = fixedCost.add(row.amount());
            }
            if (level1.isDiscretionary()) {
                discretionaryByLevel2.merge(subcategoryLabel(row.categoryLevel2()), row.amount(), BigDecimal::add);
            }
            if (isOutros(row.categoryLevel2())) {
                outrosExpense = outrosExpense.add(row.amount());
            }
        }

        ExpenseSummary toSummary(YearMonth month) {
            var overview = new Overview(money(totalIncome), money(totalExpenses),
                    money(totalIncome.subtract(totalExpenses)));

            List<CategoryBreakdown> level1Breakdown = byLevel1.entrySet().stream()
                    .sorted(Map.Entry.<CategoryLevel1, BigDecimal>comparingByValue().reversed()
                            .thenComparing(e -> e.getKey().name()))
                    .map(e -> new CategoryBreakdown(e.getKey(), money(e.getValue()),
                            percentOfIncome(e.getValue())))
                    .toList();

            List<SubcategoryTotal> topCuts = discretionaryByLevel2.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(TOP_CUTS_LIMIT)
                    .map(e -> new SubcategoryTotal(e.getKey(), money(e.getValue())))
                    .toList();

            List<DailyTotal> dailyTotals = byDay.entrySet().stream()
                    .map(e -> new DailyTotal(e.getKey(), money(e.getValue())))
                    .toList();

            return new ExpenseSummary(month, overview, level1Breakdown, topCuts,
                    money(fixedCost), dailyTotals, blackHoleWarning());
        }

        private BigDecimal percentOfIncome(BigDecimal part) {
            if (totalIncome.signum() == 0) {
                return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY);
            }
            return part.multiply(BigDecimal.valueOf(100)).divide(totalIncome, PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        private boolean blackHoleWarning() {
            return totalExpenses.signum() > 0
                    && outrosExpense.compareTo(totalExpenses.multiply(BLACK_HOLE_THRESHOLD)) > 0;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isOutros(String categoryLevel2) {
        return categoryLevel2 != null && OUTROS_SUBCATEGORY.equalsIgnoreCase(categoryLevel2.strip());
    }

    private static String subcategoryLabel(String categoryLevel2) {
        if (categoryLevel2 == null || categoryLevel2.isBlank()) {
            return NO_SUBCATEGORY;
        }
        return categoryLevel2.strip();
    }
}
