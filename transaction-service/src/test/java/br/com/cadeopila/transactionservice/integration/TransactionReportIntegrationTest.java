package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.ExchangeRatePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class TransactionReportIntegrationTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountPort accountPort;

    @MockitoBean
    private ExchangeRatePort exchangeRatePort;

    @BeforeEach
    void setUp() {
        var authentication = new UsernamePasswordAuthenticationToken(
                USER, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(
                new AccountSnapshot(USER, new BigDecimal("100000.00"),
                        new BigDecimal("1000000.00"), new BigDecimal("5000.00"))));
        when(exchangeRatePort.quoteAll(any())).thenReturn(List.of(
                new ExchangeRate("USD", new BigDecimal("5.00"), "BrasilAPI", Instant.parse("2026-11-06T18:00:00Z"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void postCreated(String body) throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Fim a fim (CSV): baixa o relatório com BOM, cabeçalho pt-BR, acentos, escaping e linhas APROVADA/REJEITADA")
    void relatorioCsvPontaAPonta() throws Exception {
        // Aprovada com descrição "difícil" (contém ';' e aspas) e acento.
        postCreated("{\"type\":\"PURCHASE\",\"amount\":150.00,"
                + "\"description\":\"Compra; \\\"caça\\\"\",\"category\":\"SHOPPING\"}");
        // Rejeitada: saque acima do limite por transação (5000) → fica gravada com o motivo.
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WITHDRAWAL\",\"amount\":10000.00,"
                                + "\"description\":\"Saque alto\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var result = mockMvc.perform(get("/api/v1/transactions/report"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.csv\""))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF); // BOM UTF-8

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("Data;Tipo;Categoria Nível 1;Subcategoria;Descrição;Valor (BRL);"
                + "Status;Motivo Rejeição;Valor (USD);Valor (EUR)");
        assertThat(csv).contains("\"Compra; \"\"caça\"\"\""); // ';' e aspas escapados, acento preservado
        assertThat(csv).contains("Aprovada");
        assertThat(csv).contains("Rejeitada");
    }

    @Test
    @DisplayName("Fim a fim (Excel): format=xlsx devolve um .xlsx nativo (assinatura PK) para download")
    void relatorioExcelPontaAPonta() throws Exception {
        postCreated("{\"type\":\"DEPOSIT\",\"amount\":500.00,"
                + "\"description\":\"Salário\"}");

        var result = mockMvc.perform(get("/api/v1/transactions/report").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.xlsx\""))
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(XLSX_MEDIA_TYPE);
        assertThat(result.getResponse().getContentAsByteArray()).startsWith((byte) 0x50, (byte) 0x4B); // "PK"
    }

    @Test
    @DisplayName("Fim a fim (PDF): format=pdf devolve um PDF (assinatura %PDF) para download")
    void relatorioPdfPontaAPonta() throws Exception {
        postCreated("{\"type\":\"DEPOSIT\",\"amount\":500.00,"
                + "\"description\":\"Salário\"}");

        var result = mockMvc.perform(get("/api/v1/transactions/report").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.pdf\""))
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("application/pdf");
        // %PDF- em bytes: 0x25 0x50 0x44 0x46 0x2D
        assertThat(result.getResponse().getContentAsByteArray())
                .startsWith((byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D);
    }

    @Test
    @DisplayName("A rota literal /report resolve para o relatório (não cai no handler de /{id})")
    void reportNaoColideComFindById() throws Exception {
        // Se caísse em GET /{id}, "report" não é UUID → 400. 200 com CSV prova o roteamento correto.
        mockMvc.perform(get("/api/v1/transactions/report"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio.csv\""));
    }
}
