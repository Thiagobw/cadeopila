package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Serializa as linhas do relatório em CSV compatível com o <b>Excel pt-BR</b> (critério explícito do PO).
 * Toda a decisão de formato mora aqui, na camada de adapters — o core/application não sabem o que é CSV:
 *
 * <ul>
 *   <li><b>UTF-8 com BOM</b> ({@code EF BB BF}): sem o BOM o Excel pt-BR lê o arquivo em Latin-1 e quebra
 *       os acentos. O BOM é invisível e faz o Excel reconhecer UTF-8.</li>
 *   <li>Separador de campo <b>{@code ;}</b> e fim de linha <b>CRLF</b> (padrão que o Excel pt-BR espera).</li>
 *   <li>Decimal com <b>vírgula</b> (Locale pt-BR), sem separador de milhar, para o Excel ler como número.</li>
 *   <li><b>Escape</b> RFC-4180: campos com {@code ;}, aspas ou quebra de linha são envoltos em aspas duplas,
 *       com as aspas internas dobradas — descrições "difíceis" não desalinham as colunas.</li>
 *   <li><b>Anti CSV Injection</b> (CWE-1236): campos livres (descrição/subcategoria) que começam com
 *       {@code = + - @} recebem um apóstrofo, para o Excel não executá-los como fórmula.</li>
 * </ul>
 */
@Component
public class TransactionCsvWriter {

    /** Byte Order Mark de UTF-8: faz o Excel pt-BR abrir o arquivo como UTF-8 (acentos corretos). */
    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);

    private static final String[] HEADERS = {
            "Data", "Tipo", "Categoria Nível 1", "Subcategoria", "Descrição",
            "Valor (BRL)", "Status", "Motivo Rejeição", "Valor (USD)", "Valor (EUR)"
    };

    public byte[] write(List<ReportRow> rows) {
        var sb = new StringBuilder();
        appendLine(sb, HEADERS);
        for (ReportRow row : rows) {
            appendLine(sb, cells(row));
        }
        return withBom(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String[] cells(ReportRow row) {
        return new String[]{
                DATE.format(row.date()),
                ReportLabels.type(row.type()),
                ReportLabels.categoryLevel1(row.categoryLevel1()),
                sanitizeAgainstInjection(nullToEmpty(row.categoryLevel2())),
                sanitizeAgainstInjection(row.description()),
                money(row.amountBrl()),
                ReportLabels.status(row.status()),
                ReportLabels.rejectionReason(row.rejectionReason()),
                money(row.amountUsd()),
                money(row.amountEur())
        };
    }

    private static void appendLine(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(escape(cells[i]));
        }
        sb.append(LINE_END);
    }

    /** Envolve em aspas e dobra as internas quando o valor contém o separador, aspas ou quebra de linha. */
    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Caracteres que, no início de uma célula, fazem o Excel/Sheets interpretá-la como fórmula. */
    private static final String FORMULA_TRIGGERS = "=+-@";

    /**
     * Prevenção de CSV Injection (CWE-1236): se um campo controlado pelo usuário começa com
     * {@code =}, {@code +}, {@code -} ou {@code @}, prefixa um apóstrofo para o Excel/Sheets tratarem a
     * célula como texto, não fórmula. Aplicado só aos campos livres (descrição e subcategoria); datas,
     * rótulos e valores numéricos são gerados pelo sistema. O {@link #escape} seguinte cuida do RFC-4180.
     */
    private static String sanitizeAgainstInjection(String value) {
        if (value != null && !value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    /** BigDecimal → texto pt-BR com 2 casas e vírgula decimal (ex.: {@code 1500,00}); {@code null} → vazio. */
    private static String money(BigDecimal value) {
        return value == null ? "" : String.format(PT_BR, "%.2f", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static byte[] withBom(byte[] body) {
        var out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }
}
