package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPdfWriterTest {

    private final TransactionPdfWriter writer = new TransactionPdfWriter();

    private static ReportRow approved() {
        return new ReportRow(LocalDate.of(2026, 11, 5), TransactionType.PURCHASE,
                CategoryLevel1.ESSENCIAL, "Alimentação", "Pão & café",
                new BigDecimal("1500.00"), TransactionStatus.APPROVED, null,
                new BigDecimal("300.00"), null);
    }

    private static ReportRow rejected() {
        return new ReportRow(LocalDate.of(2026, 11, 6), TransactionType.WITHDRAWAL,
                CategoryLevel1.DIVIDAS_IMPREVISTOS, null, "Saque",
                new BigDecimal("40.00"), TransactionStatus.REJECTED, RejectionReason.INSUFFICIENT_BALANCE,
                null, null);
    }

    @Test
    @DisplayName("Gera um PDF válido: começa com %PDF- e termina com o marcador %%EOF")
    void geraPdfValido() {
        byte[] bytes = writer.write(List.of(approved(), rejected()));

        assertThat(bytes).isNotEmpty();
        var text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("%PDF-");
        assertThat(text).contains("%%EOF");
    }

    @Test
    @DisplayName("Relatório vazio (sem linhas) ainda gera um PDF válido com o cabeçalho")
    void relatorioVazioGeraPdf() {
        byte[] bytes = writer.write(List.of());

        var text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("%PDF-");
        assertThat(text).contains("%%EOF");
    }
}
