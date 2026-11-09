package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
import br.com.cadeopila.transactionservice.core.ports.in.GetTransactionReportUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionReportController.class)
@Import({TransactionCsvWriter.class, TransactionExcelWriter.class, TransactionPdfWriter.class})
class TransactionReportControllerTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTransactionReportUseCase getTransactionReport;

    private static ReportRow row() {
        return new ReportRow(LocalDate.of(2026, 11, 5), TransactionType.PURCHASE,
                CategoryLevel1.ESSENCIAL, "Alimentação", "Mercado",
                new BigDecimal("150.00"), TransactionStatus.APPROVED, null, null, null);
    }

    @Test
    @DisplayName("Sem format: baixa CSV (text/csv), com Content-Disposition de anexo e BOM no corpo")
    void padraoBaixaCsv() throws Exception {
        when(getTransactionReport.generate(any(), any(), any())).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/v1/transactions/report").param("userId", USER.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.csv\""))
                .andExpect(content().bytes(expectedCsvBytes()));
    }

    @Test
    @DisplayName("format=xlsx: baixa Excel (OOXML) com filename relatorio.xlsx")
    void formatXlsxBaixaExcel() throws Exception {
        when(getTransactionReport.generate(any(), any(), any())).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/v1/transactions/report")
                        .param("userId", USER.toString())
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX_MEDIA_TYPE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.xlsx\""));
    }

    @Test
    @DisplayName("format=pdf: baixa PDF (application/pdf) com filename relatorio.pdf e assinatura %PDF")
    void formatPdfBaixaPdf() throws Exception {
        when(getTransactionReport.generate(any(), any(), any())).thenReturn(List.of(row()));

        var result = mockMvc.perform(get("/api/v1/transactions/report")
                        .param("userId", USER.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.pdf\""))
                .andReturn();

        // %PDF- em bytes: 0x25 0x50 0x44 0x46 0x2D
        assertThat(result.getResponse().getContentAsByteArray())
                .startsWith((byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D);
    }

    @Test
    @DisplayName("userId ausente → 400 indicando o campo")
    void semUserId400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/report"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("format desconhecido → 400 (ProblemDetail do domínio)")
    void formatInvalido400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/report")
                        .param("userId", USER.toString())
                        .param("format", "txt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Período invertido propaga 400 do caso de uso")
    void periodoInvertido400() throws Exception {
        when(getTransactionReport.generate(any(), any(), any()))
                .thenThrow(new InvalidTransactionFilterException("from não pode ser depois de to"));

        mockMvc.perform(get("/api/v1/transactions/report")
                        .param("userId", USER.toString())
                        .param("from", "2026-11-30")
                        .param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    /** Corpo CSV esperado (inclui o BOM): monta com o writer real para comparar byte a byte. */
    private static byte[] expectedCsvBytes() {
        return new TransactionCsvWriter().write(List.of(row()));
    }
}
