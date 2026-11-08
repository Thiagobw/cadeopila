package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.ExpenseAnalysisResponse;
import br.com.cadeopila.transactionservice.adapters.in.web.mapper.ExpenseAnalysisWebMapper;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Análise de Despesas", description = "Blocos de decisão a partir das transações aprovadas do mês")
@RestController
@RequestMapping("/api/v1/analytics/expenses")
public class ExpenseAnalyticsController {

    private final GetExpenseAnalysisUseCase getExpenseAnalysis;
    private final ExpenseAnalysisWebMapper mapper;

    public ExpenseAnalyticsController(GetExpenseAnalysisUseCase getExpenseAnalysis,
                                      ExpenseAnalysisWebMapper mapper) {
        this.getExpenseAnalysis = getExpenseAnalysis;
        this.mapper = mapper;
    }

    @Operation(summary = "Resumo inteligente de despesas do mês",
            description = "Agrega as transações APROVADAS do mês (depósitos como receita; saques, transferências "
                    + "e compras como despesa) em: visão geral, raio-X por grau de necessidade, onde cortar (top 5), "
                    + "custo fixo, total por dia e o alerta do \"buraco negro\" (OUTROS > 5%). "
                    + "Sem o parâmetro month, usa o mês atual (fuso America/Sao_Paulo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo do mês"),
            @ApiResponse(responseCode = "400", description = "Parâmetro inválido (userId ausente ou month fora de MM-YYYY)")
    })
    @GetMapping("/summary")
    public ResponseEntity<ExpenseAnalysisResponse> summary(
            @RequestParam UUID userId,
            @RequestParam(required = false) String month) {
        var summary = getExpenseAnalysis.summarize(userId, mapper.toYearMonth(month));
        return ResponseEntity.ok(mapper.toResponse(summary));
    }
}
