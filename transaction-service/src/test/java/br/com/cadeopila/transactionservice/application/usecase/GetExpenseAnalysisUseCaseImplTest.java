package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.AnalyticsRow;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.ExpenseSummary;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetExpenseAnalysisUseCaseImplTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @Mock
    private TransactionRepositoryPort repository;

    private GetExpenseAnalysisUseCaseImpl useCase() {
        return new GetExpenseAnalysisUseCaseImpl(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), ZONE);
    }

    private static AnalyticsRow row(TransactionType type, String amount, CategoryLevel1 level1,
                                    String level2, boolean fixed, String occurredAt) {
        return new AnalyticsRow(type, new BigDecimal(amount), level1, level2, fixed, Instant.parse(occurredAt));
    }

    /** Cenário rico do mês: 1 depósito (receita) + 5 saídas em 3 graus de necessidade. */
    private List<AnalyticsRow> richMonth() {
        return List.of(
                row(TransactionType.DEPOSIT, "5000.00", CategoryLevel1.SAUDE_FINANCEIRA, "Salário", false, "2026-11-01T12:00:00Z"),
                row(TransactionType.PURCHASE, "1000.00", CategoryLevel1.ESSENCIAL, "Aluguel", true, "2026-11-03T12:00:00Z"),
                row(TransactionType.PURCHASE, "500.00", CategoryLevel1.ESTILO_DE_VIDA, "Delivery", false, "2026-11-05T12:00:00Z"),
                row(TransactionType.PURCHASE, "300.00", CategoryLevel1.ESTILO_DE_VIDA, "Delivery", false, "2026-11-05T15:00:00Z"),
                row(TransactionType.WITHDRAWAL, "200.00", CategoryLevel1.DIVIDAS_IMPREVISTOS, "OUTROS", false, "2026-11-10T12:00:00Z"),
                row(TransactionType.TRANSFER, "100.00", CategoryLevel1.ESSENCIAL, "Conta de luz", true, "2026-11-12T12:00:00Z"));
    }

    @Test
    @DisplayName("Visão geral: receitas (depósitos), despesas (saídas) e saldo livre")
    void overviewDeveSepararReceitaEDespesa() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        var overview = useCase().summarize(USER, NOVEMBER).overview();

        assertThat(overview.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(overview.totalExpenses()).isEqualByComparingTo("2100.00");
        assertThat(overview.freeBalance()).isEqualByComparingTo("2900.00");
    }

    @Test
    @DisplayName("Raio-X (nível 1): soma por grau de necessidade e % da receita, maior primeiro")
    void raioXDeveAgruparPorNivel1ComPercentualDaReceita() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        var byLevel1 = useCase().summarize(USER, NOVEMBER).byLevel1();

        assertThat(byLevel1).extracting(b -> b.level1()).containsExactly(
                CategoryLevel1.ESSENCIAL, CategoryLevel1.ESTILO_DE_VIDA, CategoryLevel1.DIVIDAS_IMPREVISTOS);
        assertThat(byLevel1.get(0).spent()).isEqualByComparingTo("1100.00");
        assertThat(byLevel1.get(0).percentOfIncome()).isEqualByComparingTo("22.00");
        assertThat(byLevel1.get(1).spent()).isEqualByComparingTo("800.00");
        assertThat(byLevel1.get(1).percentOfIncome()).isEqualByComparingTo("16.00");
        assertThat(byLevel1.get(2).spent()).isEqualByComparingTo("200.00");
        assertThat(byLevel1.get(2).percentOfIncome()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("Onde Cortar Hoje: só subcategorias discricionárias (ESTILO_DE_VIDA/DIVIDAS), top por valor")
    void ondeCortarDeveFiltrarDiscricionariasEOrdenar() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        var topCuts = useCase().summarize(USER, NOVEMBER).topCuts();

        // "Aluguel"/"Conta de luz" são ESSENCIAL → fora. Delivery = 500+300 = 800; OUTROS = 200.
        assertThat(topCuts).extracting(c -> c.subcategory()).containsExactly("Delivery", "OUTROS");
        assertThat(topCuts.get(0).spent()).isEqualByComparingTo("800.00");
        assertThat(topCuts.get(1).spent()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Onde Cortar Hoje: limita aos 5 maiores")
    void ondeCortarDeveLimitarACinco() {
        var rows = List.of(
                row(TransactionType.PURCHASE, "60.00", CategoryLevel1.ESTILO_DE_VIDA, "A", false, "2026-11-02T12:00:00Z"),
                row(TransactionType.PURCHASE, "50.00", CategoryLevel1.ESTILO_DE_VIDA, "B", false, "2026-11-02T12:00:00Z"),
                row(TransactionType.PURCHASE, "40.00", CategoryLevel1.ESTILO_DE_VIDA, "C", false, "2026-11-02T12:00:00Z"),
                row(TransactionType.PURCHASE, "30.00", CategoryLevel1.ESTILO_DE_VIDA, "D", false, "2026-11-02T12:00:00Z"),
                row(TransactionType.PURCHASE, "20.00", CategoryLevel1.ESTILO_DE_VIDA, "E", false, "2026-11-02T12:00:00Z"),
                row(TransactionType.PURCHASE, "10.00", CategoryLevel1.ESTILO_DE_VIDA, "F", false, "2026-11-02T12:00:00Z"));
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(rows);

        var topCuts = useCase().summarize(USER, NOVEMBER).topCuts();

        assertThat(topCuts).hasSize(5);
        assertThat(topCuts).extracting(c -> c.subcategory()).containsExactly("A", "B", "C", "D", "E");
    }

    @Test
    @DisplayName("Custo Fixo Operacional: soma só as despesas com isFixed = true")
    void custoFixoDeveSomarApenasFixas() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        var summary = useCase().summarize(USER, NOVEMBER);

        assertThat(summary.fixedOperationalCost()).isEqualByComparingTo("1100.00"); // Aluguel 1000 + Conta de luz 100
    }

    @Test
    @DisplayName("Total por dia: agrupado no fuso de São Paulo, em ordem cronológica")
    void totalPorDiaDeveSerCronologico() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        var byDay = useCase().summarize(USER, NOVEMBER).byDay();

        assertThat(byDay).extracting(d -> d.date()).containsExactly(
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
        assertThat(byDay.get(1).total()).isEqualByComparingTo("800.00"); // 500 + 300 no mesmo dia
    }

    @Test
    @DisplayName("Agrupamento por dia usa o fuso: 02:00 UTC cai no dia anterior em São Paulo")
    void totalPorDiaDeveUsarFusoNaoUtc() {
        // 2026-11-06T02:00Z = 2026-11-05 23:00 em São Paulo → dia 05, não 06.
        var rows = List.of(
                row(TransactionType.PURCHASE, "90.00", CategoryLevel1.ESSENCIAL, "Mercado", false, "2026-11-06T02:00:00Z"));
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(rows);

        var byDay = useCase().summarize(USER, NOVEMBER).byDay();

        assertThat(byDay).extracting(d -> d.date()).containsExactly(LocalDate.of(2026, 11, 5));
    }

    @Test
    @DisplayName("Alerta do buraco negro: OUTROS acima de 5% das despesas liga a flag")
    void buracoNegroDeveLigarQuandoOutrosPassaDe5Porcento() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(richMonth());

        // OUTROS = 200; despesas = 2100; 5% = 105; 200 > 105 → true.
        assertThat(useCase().summarize(USER, NOVEMBER).showBlackHoleWarning()).isTrue();
    }

    @Test
    @DisplayName("Alerta do buraco negro: fica desligado quando OUTROS não passa de 5%")
    void buracoNegroDeveFicarDesligadoAbaixoDe5Porcento() {
        var rows = List.of(
                row(TransactionType.PURCHASE, "2000.00", CategoryLevel1.ESSENCIAL, "Aluguel", true, "2026-11-03T12:00:00Z"),
                row(TransactionType.WITHDRAWAL, "100.00", CategoryLevel1.DIVIDAS_IMPREVISTOS, "outros", false, "2026-11-10T12:00:00Z"));
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(rows);

        // OUTROS = 100 (case-insensitive); despesas = 2100; 5% = 105; 100 <= 105 → false.
        assertThat(useCase().summarize(USER, NOVEMBER).showBlackHoleWarning()).isFalse();
    }

    @Test
    @DisplayName("Sem receita, a % da receita é 0 (sem divisão por zero)")
    void percentualDeveSerZeroSemReceita() {
        var rows = List.of(
                row(TransactionType.PURCHASE, "150.00", CategoryLevel1.ESSENCIAL, "Mercado", false, "2026-11-03T12:00:00Z"));
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(rows);

        var byLevel1 = useCase().summarize(USER, NOVEMBER).byLevel1();

        assertThat(byLevel1.get(0).percentOfIncome()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Mês vazio: zeros e listas vazias, sem alerta")
    void mesVazioDeveZerar() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(List.of());

        ExpenseSummary summary = useCase().summarize(USER, NOVEMBER);

        assertThat(summary.overview().totalIncome()).isEqualByComparingTo("0.00");
        assertThat(summary.overview().totalExpenses()).isEqualByComparingTo("0.00");
        assertThat(summary.overview().freeBalance()).isEqualByComparingTo("0.00");
        assertThat(summary.byLevel1()).isEmpty();
        assertThat(summary.topCuts()).isEmpty();
        assertThat(summary.byDay()).isEmpty();
        assertThat(summary.fixedOperationalCost()).isEqualByComparingTo("0.00");
        assertThat(summary.showBlackHoleWarning()).isFalse();
    }

    @Test
    @DisplayName("Sem o mês, usa o mês atual: consulta o repositório no intervalo do mês no fuso")
    void semMesDeveUsarMesAtualNoFuso() {
        when(repository.findApprovedByPeriod(eq(USER), any(), any())).thenReturn(List.of());

        var summary = useCase().summarize(USER, null);

        assertThat(summary.month()).isEqualTo(NOVEMBER); // FIXED_NOW = 2026-11-15

        var startCaptor = ArgumentCaptor.forClass(Instant.class);
        var endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findApprovedByPeriod(eq(USER), startCaptor.capture(), endCaptor.capture());
        // 2026-11-01 00:00 e 2026-12-01 00:00 em São Paulo (UTC-3) = 03:00Z.
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z"));
    }
}
