package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.ports.in.GetTransactionReportUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Exporta o relatório de transações do usuário para download. Mantém-se fino: delega a seleção/derivação
 * dos dados ao caso de uso ({@link GetTransactionReportUseCase}) e a serialização para bytes aos
 * <i>writers</i> ({@link TransactionCsvWriter}/{@link TransactionExcelWriter}). A escolha do formato é feita
 * por <i>content negotiation</i> via parâmetro {@code ?format=csv|xlsx} (padrão {@code csv}).
 */
@Tag(name = "Relatórios", description = "Exportação das transações do usuário em CSV e Excel")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionReportController {

    private final GetTransactionReportUseCase getTransactionReport;
    private final TransactionCsvWriter csvWriter;
    private final TransactionExcelWriter excelWriter;
    private final TransactionPdfWriter pdfWriter;

    public TransactionReportController(GetTransactionReportUseCase getTransactionReport,
                                       TransactionCsvWriter csvWriter,
                                       TransactionExcelWriter excelWriter,
                                       TransactionPdfWriter pdfWriter) {
        this.getTransactionReport = getTransactionReport;
        this.csvWriter = csvWriter;
        this.excelWriter = excelWriter;
        this.pdfWriter = pdfWriter;
    }

    @Operation(summary = "Exporta o relatório de transações do usuário (CSV, Excel ou PDF)",
            description = "Baixa as transações do usuário num período (datas inclusivas, fuso America/Sao_Paulo; "
                    + "sem período usa o mês atual). Inclui APPROVED e REJECTED, excluindo as removidas (soft delete). "
                    + "Colunas: data, tipo, categoria nível 1, subcategoria, descrição, valor em BRL, status, motivo da "
                    + "rejeição e valores convertidos USD/EUR do snapshot. Formato via ?format=csv (padrão), ?format=xlsx "
                    + "ou ?format=pdf. O CSV é UTF-8 com BOM e separador ';' para abrir corretamente no Excel pt-BR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo do relatório para download",
                    content = {
                            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Parâmetro inválido (userId ausente, período invertido "
                    + "ou format fora de csv/xlsx/pdf)", content = @Content)
    })
    @GetMapping("/report")
    public ResponseEntity<byte[]> report(
            @RequestParam UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format) {
        ReportFormat reportFormat = ReportFormat.from(format);
        List<ReportRow> rows = getTransactionReport.generate(userId, from, to);
        byte[] body = switch (reportFormat) {
            case CSV -> csvWriter.write(rows);
            case XLSX -> excelWriter.write(rows);
            case PDF -> pdfWriter.write(rows);
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + reportFormat.filename() + "\"")
                .contentType(MediaType.parseMediaType(reportFormat.contentType()))
                .body(body);
    }
}
