package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionExcelWriterTest {

    private final TransactionExcelWriter writer = new TransactionExcelWriter();

    private static ReportRow sample() {
        return new ReportRow(LocalDate.of(2026, 11, 5), TransactionType.PURCHASE,
                CategoryLevel1.ESSENCIAL, "Alimentação", "Mercado",
                new BigDecimal("1500.00"), TransactionStatus.APPROVED, null,
                new BigDecimal("300.00"), null);
    }

    @Test
    @DisplayName("Gera um .xlsx válido (assinatura ZIP 'PK') com a aba Transações e o cabeçalho")
    void geraXlsxValido() throws IOException {
        byte[] bytes = writer.write(List.of(sample()));

        assertThat(bytes).startsWith((byte) 0x50, (byte) 0x4B); // "PK" — contêiner OOXML (zip)

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Transações");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
        }
    }

    @Test
    @DisplayName("Data e moedas são células tipadas (não texto) para permitir somas no arquivo final")
    void celulasTipadasParaSoma() throws IOException {
        byte[] bytes = writer.write(List.of(sample()));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheet("Transações").getRow(1);

            // Data: célula numérica com formato de data.
            var dateCell = row.getCell(0);
            assertThat(dateCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(dateCell)).isTrue();
            assertThat(dateCell.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 5));

            // Valor BRL: numérico (somável).
            var brl = row.getCell(5);
            assertThat(brl.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(brl.getNumericCellValue()).isEqualTo(1500.00);

            // Valor USD: numérico; EUR: sem valor (célula ausente ou em branco quando não houve cotação).
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(300.00);
            var eur = row.getCell(9);
            if (eur != null) {
                assertThat(eur.getCellType()).isEqualTo(CellType.BLANK);
            }

            // Rótulo pt-BR como texto.
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Compra");
        }
    }
}
