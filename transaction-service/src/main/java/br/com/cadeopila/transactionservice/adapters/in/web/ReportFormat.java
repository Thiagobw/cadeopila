package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;

import java.util.Locale;

/**
 * Formato de exportação do relatório (content negotiation via parâmetro {@code ?format=}). Carrega o
 * nome do arquivo de download e o <i>content type</i> de cada formato; a seleção do <i>writer</i> fica
 * no controller.
 */
public enum ReportFormat {

    CSV("relatorio.csv", "text/csv; charset=UTF-8"),
    XLSX("relatorio.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("relatorio.pdf", "application/pdf");

    private final String filename;
    private final String contentType;

    ReportFormat(String filename, String contentType) {
        this.filename = filename;
        this.contentType = contentType;
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    /** {@code null}/em branco → CSV (padrão). Valor desconhecido → 400. Aceita {@code excel} como alias de xlsx. */
    public static ReportFormat from(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx", "excel" -> XLSX;
            case "pdf" -> PDF;
            default -> throw new InvalidTransactionFilterException("format deve ser 'csv', 'xlsx' ou 'pdf'");
        };
    }
}
