package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Análise inteligente de despesas do mês: visão geral, raio-X por grau de necessidade, "
        + "onde cortar, custo fixo, total por dia e alerta de ineficiência")
public record ExpenseAnalysisResponse(

        @Schema(description = "Mês analisado (MM-YYYY)", example = "11-2026") String month,

        @Schema(description = "O Pulso: receitas, despesas e saldo livre") OverviewResponse overview,

        @Schema(description = "Raio-X: gasto e % da receita por grau de necessidade (nível 1), maior primeiro")
        List<CategoryLevel1Response> xray,

        @Schema(description = "Onde Cortar Hoje: 5 maiores subcategorias discricionárias "
                + "(nível 1 ESTILO_DE_VIDA ou DIVIDAS_IMPREVISTOS)")
        List<SubcategoryCutResponse> topCuts,

        @Schema(description = "Custo Fixo Operacional: soma das despesas fixas (o quanto precisa no dia 1º)",
                example = "1200.00")
        BigDecimal fixedOperationalCost,

        @Schema(description = "Total gasto por dia (fuso America/Sao_Paulo), em ordem cronológica")
        List<DailyTotalResponse> byDay,

        @Schema(description = "Alerta do \"buraco negro\": gasto em OUTROS passou de 5% das despesas", example = "false")
        boolean showBlackHoleWarning
) {

    @Schema(description = "Visão geral do mês")
    public record OverviewResponse(
            @Schema(description = "Total de receitas (depósitos aprovados)", example = "5000.00") BigDecimal totalIncome,
            @Schema(description = "Total de despesas (saídas aprovadas)", example = "3200.00") BigDecimal totalExpenses,
            @Schema(description = "Saldo livre (receitas - despesas)", example = "1800.00") BigDecimal freeBalance) {}

    @Schema(description = "Gasto por grau de necessidade (nível 1)")
    public record CategoryLevel1Response(
            @Schema(example = "ESTILO_DE_VIDA") CategoryLevel1 level1,
            @Schema(description = "Valor gasto no nível 1", example = "900.00") BigDecimal spent,
            @Schema(description = "Participação na receita total (%)", example = "18.00") BigDecimal percentOfIncome) {}

    @Schema(description = "Gasto por subcategoria livre (nível 2)")
    public record SubcategoryCutResponse(
            @Schema(example = "Delivery") String subcategory,
            @Schema(example = "450.00") BigDecimal spent) {}

    @Schema(description = "Total gasto em um dia")
    public record DailyTotalResponse(
            @Schema(example = "2026-11-06") LocalDate date,
            @Schema(example = "320.00") BigDecimal total) {}
}
