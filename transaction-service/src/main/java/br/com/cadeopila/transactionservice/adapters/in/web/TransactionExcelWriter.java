package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Gera o relatório como planilha <b>.xlsx nativa</b> (bônus do Pedido 7), via Apache POI. Toda a dependência
 * do {@code org.apache.poi} fica confinada a esta classe (camada adapters): o core/application nunca a veem.
 *
 * <p>Diferente do CSV, as colunas de <b>data</b> e <b>moeda</b> são células com tipo nativo (data e número)
 * e formato de exibição aplicado por estilo — não texto — para que o usuário consiga <b>somar</b> e filtrar
 * no arquivo final. As colunas são auto-dimensionadas para legibilidade e o cabeçalho é congelado.</p>
 */
@Component
public class TransactionExcelWriter {

    private static final String SHEET_NAME = "Transações";
    private static final String DATE_FORMAT = "dd/mm/yyyy";
    private static final String MONEY_FORMAT = "#,##0.00";

    private static final String[] HEADERS = {
            "Data", "Tipo", "Categoria Nível 1", "Subcategoria", "Descrição",
            "Valor (BRL)", "Status", "Motivo Rejeição", "Valor (USD)", "Valor (EUR)"
    };

    public byte[] write(List<ReportRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(SHEET_NAME);
            var styles = new Styles(workbook);

            writeHeader(sheet, styles);
            int rowIndex = 1;
            for (ReportRow row : rows) {
                writeRow(sheet.createRow(rowIndex++), row, styles);
            }

            sheet.createFreezePane(0, 1);
            autoSize(sheet);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o relatório Excel", e);
        }
    }

    private static void writeHeader(Sheet sheet, Styles styles) {
        var header = sheet.createRow(0);
        for (int c = 0; c < HEADERS.length; c++) {
            var cell = header.createCell(c);
            cell.setCellValue(HEADERS[c]);
            cell.setCellStyle(styles.header);
        }
    }

    private static void writeRow(Row row, ReportRow data, Styles styles) {
        dateCell(row.createCell(0), data, styles);
        text(row.createCell(1), ReportLabels.type(data.type()));
        text(row.createCell(2), ReportLabels.categoryLevel1(data.categoryLevel1()));
        text(row.createCell(3), data.categoryLevel2());
        text(row.createCell(4), data.description());
        money(row.createCell(5), data.amountBrl(), styles);
        text(row.createCell(6), ReportLabels.status(data.status()));
        text(row.createCell(7), ReportLabels.rejectionReason(data.rejectionReason()));
        money(row.createCell(8), data.amountUsd(), styles);
        money(row.createCell(9), data.amountEur(), styles);
    }

    private static void dateCell(Cell cell, ReportRow data, Styles styles) {
        cell.setCellValue(data.date()); // célula de data nativa (LocalDate), não texto
        cell.setCellStyle(styles.date);
    }

    private static void money(Cell cell, BigDecimal value, Styles styles) {
        if (value == null) {
            return; // deixa a célula em branco; não quebra somas no arquivo
        }
        cell.setCellValue(value.doubleValue()); // número nativo para permitir somas
        cell.setCellStyle(styles.money);
    }

    private static void text(Cell cell, String value) {
        cell.setCellValue(value == null ? "" : value);
    }

    private static void autoSize(Sheet sheet) {
        for (int c = 0; c < HEADERS.length; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    /** Estilos reaproveitados entre as linhas (cabeçalho em negrito, data e moeda com formato nativo). */
    private static final class Styles {
        private final CellStyle header;
        private final CellStyle date;
        private final CellStyle money;

        private Styles(Workbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();

            Font bold = workbook.createFont();
            bold.setBold(true);
            this.header = workbook.createCellStyle();
            this.header.setFont(bold);

            this.date = workbook.createCellStyle();
            this.date.setDataFormat(helper.createDataFormat().getFormat(DATE_FORMAT));

            this.money = workbook.createCellStyle();
            this.money.setDataFormat(helper.createDataFormat().getFormat(MONEY_FORMAT));
        }
    }
}
