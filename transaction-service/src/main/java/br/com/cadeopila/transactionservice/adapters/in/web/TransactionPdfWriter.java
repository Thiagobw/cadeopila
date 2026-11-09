package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Gera o relatório em <b>PDF</b> (tabela paisagem A4) via OpenPDF. Como no CSV/Excel, toda a dependência da
 * biblioteca de PDF ({@code com.lowagie.text}) fica confinada a esta classe da camada adapters — o
 * core/application nunca a veem.
 *
 * <p>O PDF é um formato de <b>apresentação</b> (não editável/somável como o Excel): datas em {@code dd/MM/yyyy},
 * valores em pt-BR com vírgula decimal e enums com rótulo pt-BR, tudo como texto. A codificação Cp1252
 * (WINANSI) das fontes padrão cobre os acentos do português.</p>
 */
@Component
public class TransactionPdfWriter {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final String ENCODING = "Cp1252"; // WINANSI: cobre acentos pt-BR nas fontes base

    private static final String[] HEADERS = {
            "Data", "Tipo", "Categoria Nível 1", "Subcategoria", "Descrição",
            "Valor (BRL)", "Status", "Motivo Rejeição", "Valor (USD)", "Valor (EUR)"
    };
    private static final float[] COLUMN_WIDTHS = {10f, 9f, 13f, 12f, 20f, 9f, 9f, 14f, 8f, 8f};
    private static final Color HEADER_BACKGROUND = new Color(0x33, 0x33, 0x33);

    public byte[] write(List<ReportRow> rows) {
        var document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        var out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(title());
            document.add(table(rows));
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o relatório PDF", e);
        }
        return out.toByteArray();
    }

    private static Paragraph title() {
        var paragraph = new Paragraph("Relatório de Transações",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, ENCODING, 14f));
        paragraph.setSpacingAfter(10f);
        return paragraph;
    }

    private static PdfPTable table(List<ReportRow> rows) throws DocumentException {
        var table = new PdfPTable(HEADERS.length);
        table.setWidthPercentage(100f);
        table.setWidths(COLUMN_WIDTHS);
        table.setHeaderRows(1);

        for (String header : HEADERS) {
            table.addCell(headerCell(header));
        }
        for (ReportRow row : rows) {
            addRow(table, row);
        }
        return table;
    }

    private static void addRow(PdfPTable table, ReportRow row) {
        table.addCell(bodyCell(DATE.format(row.date()), Element.ALIGN_CENTER));
        table.addCell(bodyCell(ReportLabels.type(row.type()), Element.ALIGN_LEFT));
        table.addCell(bodyCell(ReportLabels.categoryLevel1(row.categoryLevel1()), Element.ALIGN_LEFT));
        table.addCell(bodyCell(nullToEmpty(row.categoryLevel2()), Element.ALIGN_LEFT));
        table.addCell(bodyCell(row.description(), Element.ALIGN_LEFT));
        table.addCell(bodyCell(money(row.amountBrl()), Element.ALIGN_RIGHT));
        table.addCell(bodyCell(ReportLabels.status(row.status()), Element.ALIGN_LEFT));
        table.addCell(bodyCell(ReportLabels.rejectionReason(row.rejectionReason()), Element.ALIGN_LEFT));
        table.addCell(bodyCell(money(row.amountUsd()), Element.ALIGN_RIGHT));
        table.addCell(bodyCell(money(row.amountEur()), Element.ALIGN_RIGHT));
    }

    private static PdfPCell headerCell(String text) {
        var font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, ENCODING, 8f, Font.NORMAL, Color.WHITE);
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4f);
        return cell;
    }

    private static PdfPCell bodyCell(String text, int alignment) {
        var font = FontFactory.getFont(FontFactory.HELVETICA, ENCODING, 8f);
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(3f);
        return cell;
    }

    /** BigDecimal → texto pt-BR com 2 casas e vírgula decimal; {@code null} → vazio. */
    private static String money(BigDecimal value) {
        return value == null ? "" : String.format(PT_BR, "%.2f", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
