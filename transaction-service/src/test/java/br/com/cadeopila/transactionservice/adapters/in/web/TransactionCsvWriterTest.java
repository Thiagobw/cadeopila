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

class TransactionCsvWriterTest {

    private final TransactionCsvWriter writer = new TransactionCsvWriter();

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

    /** Lê os bytes como UTF-8 pulando o BOM, para inspecionar o conteúdo textual. */
    private String textAfterBom(byte[] bytes) {
        return new String(bytes, TransactionCsvWriter.UTF8_BOM.length,
                bytes.length - TransactionCsvWriter.UTF8_BOM.length, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Começa com o BOM de UTF-8 (EF BB BF) para o Excel pt-BR ler os acentos")
    void deveComecarComBom() {
        byte[] out = writer.write(List.of(approved()));

        assertThat(out).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("Cabeçalho pt-BR com separador ';' e fim de linha CRLF")
    void cabecalhoComSeparadorECrlf() {
        String content = textAfterBom(writer.write(List.of()));

        assertThat(content).startsWith(
                "Data;Tipo;Categoria Nível 1;Subcategoria;Descrição;Valor (BRL);Status;Motivo Rejeição;Valor (USD);Valor (EUR)\r\n");
    }

    @Test
    @DisplayName("Linha com data dd/MM/yyyy, rótulos pt-BR e decimal com vírgula; USD vazio quando nulo")
    void linhaDeDados() {
        String content = textAfterBom(writer.write(List.of(approved())));
        String[] lines = content.split("\r\n");

        assertThat(lines[1]).isEqualTo(
                "05/11/2026;Compra;Essencial;Alimentação;Pão & café;1500,00;Aprovada;;300,00;");
    }

    @Test
    @DisplayName("Transação rejeitada traz o motivo traduzido e subcategoria/valores vazios")
    void linhaRejeitada() {
        String content = textAfterBom(writer.write(List.of(rejected())));
        String[] lines = content.split("\r\n");

        assertThat(lines[1]).isEqualTo("06/11/2026;Saque;Dívidas e imprevistos;;Saque;40,00;Rejeitada;Saldo insuficiente;;");
    }

    @Test
    @DisplayName("Descrições com ';', aspas e quebra de linha são escapadas (aspas duplas, aspas internas dobradas)")
    void deveEscaparCamposDificeis() {
        var row = new ReportRow(LocalDate.of(2026, 11, 7), TransactionType.PURCHASE,
                CategoryLevel1.ESTILO_DE_VIDA, "Lazer", "Compra; \"especial\"\ncom quebra",
                new BigDecimal("10.00"), TransactionStatus.APPROVED, null, null, null);

        String content = textAfterBom(writer.write(List.of(row)));

        // O campo inteiro é envolto por aspas e as aspas internas viram "" — as colunas não desalinham.
        assertThat(content).contains("\"Compra; \"\"especial\"\"\ncom quebra\"");
        // Ainda há exatamente 1 linha de cabeçalho + 1 de dados (a quebra interna está dentro de aspas).
    }

    @Test
    @DisplayName("Acentos preservados (UTF-8): o conteúdo decodificado mantém os caracteres")
    void acentosPreservados() {
        String content = textAfterBom(writer.write(List.of(approved())));

        assertThat(content).contains("Alimentação").contains("Pão & café").contains("Nível");
    }
}
