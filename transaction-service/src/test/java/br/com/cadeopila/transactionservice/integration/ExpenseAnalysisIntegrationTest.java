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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ExpenseAnalysisIntegrationTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");

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
    @DisplayName("Fim a fim: POSTs geram a análise do mês com os quatro blocos e o alerta do buraco negro")
    void analiseDoMesPontaAPonta() throws Exception {
        // Receita
        postCreated(json("DEPOSIT", "5000.00", "Salário mensal", "\"category\":\"SALARY\""));
        // Despesa fixa essencial
        postCreated(json("PURCHASE", "1000.00", "Aluguel do mês",
                "\"category\":\"HOUSING\",\"categoryLevel1\":\"ESSENCIAL\",\"categoryLevel2\":\"Aluguel\",\"isFixed\":true"));
        // Despesa discricionária (estilo de vida)
        postCreated(json("PURCHASE", "500.00", "Comida por app",
                "\"category\":\"FOOD\",\"categoryLevel1\":\"ESTILO_DE_VIDA\",\"categoryLevel2\":\"Delivery\""));
        // Buraco negro (OUTROS) com descrição suficiente
        postCreated(json("WITHDRAWAL", "200.00", "Gasto nao identificado",
                "\"categoryLevel1\":\"DIVIDAS_IMPREVISTOS\",\"categoryLevel2\":\"OUTROS\""));
        // Rejeitada (acima do limite por transação) — não deve entrar na análise
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(json("WITHDRAWAL", "10000.00", "Saque alto", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/analytics/expenses/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalIncome").value(5000.00))
                .andExpect(jsonPath("$.overview.totalExpenses").value(1700.00)) // 1000 + 500 + 200 (rejeitada fora)
                .andExpect(jsonPath("$.overview.freeBalance").value(3300.00))
                .andExpect(jsonPath("$.xray[0].level1").value("ESSENCIAL"))
                .andExpect(jsonPath("$.xray[0].spent").value(1000.00))
                .andExpect(jsonPath("$.topCuts[0].subcategory").value("Delivery"))
                .andExpect(jsonPath("$.topCuts[1].subcategory").value("OUTROS"))
                .andExpect(jsonPath("$.fixedOperationalCost").value(1000.00))
                .andExpect(jsonPath("$.showBlackHoleWarning").value(true));
    }

    @Test
    @DisplayName("Regra de fricção fim a fim: OUTROS com descrição curta devolve 400")
    void outrosComDescricaoCurtaDevolve400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(json("WITHDRAWAL", "50.00", "abc",
                                "\"categoryLevel2\":\"OUTROS\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "description é obrigatória e deve ter no mínimo 5 caracteres quando a subcategoria é OUTROS"));
    }

    private String json(String type, String amount, String description, String extraFields) {
        var base = "{\"type\":\"" + type + "\",\"amount\":" + amount
                + ",\"description\":\"" + description + "\"";
        if (extraFields != null && !extraFields.isBlank()) {
            base += "," + extraFields;
        }
        return base + "}";
    }
}
