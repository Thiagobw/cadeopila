package br.com.cadeopila.transactionservice.adapters.in.web;

import br.com.cadeopila.transactionservice.adapters.in.web.mapper.ExpenseAnalysisWebMapper;
import br.com.cadeopila.transactionservice.adapters.in.web.security.SecurityConfig;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.CategoryBreakdown;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.DailyTotal;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.ExpenseSummary;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.Overview;
import br.com.cadeopila.transactionservice.core.ports.in.GetExpenseAnalysisUseCase.SubcategoryTotal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseAnalyticsController.class)
@Import({ExpenseAnalysisWebMapper.class, SecurityConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class ExpenseAnalyticsControllerTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetExpenseAnalysisUseCase getExpenseAnalysis;
    @MockitoBean
    private UserAuthenticationPort userAuthenticationPort;

    @BeforeEach
    void authenticate() {
        var authentication = new UsernamePasswordAuthenticationToken(
                USER, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ExpenseSummary sampleSummary() {
        return new ExpenseSummary(
                YearMonth.of(2026, 11),
                new Overview(new BigDecimal("5000.00"), new BigDecimal("2100.00"), new BigDecimal("2900.00")),
                List.of(new CategoryBreakdown(CategoryLevel1.ESSENCIAL, new BigDecimal("1100.00"), new BigDecimal("22.00"))),
                List.of(new SubcategoryTotal("Delivery", new BigDecimal("800.00"))),
                new BigDecimal("1100.00"),
                List.of(new DailyTotal(LocalDate.of(2026, 11, 5), new BigDecimal("800.00"))),
                true);
    }

    @Test
    @DisplayName("GET summary deve retornar 200 com os quatro blocos de decisão")
    void summaryDeveRetornar200() throws Exception {
        when(getExpenseAnalysis.summarize(any(), any())).thenReturn(sampleSummary());

        mockMvc.perform(get("/api/v1/analytics/expenses/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("11-2026"))
                .andExpect(jsonPath("$.overview.totalIncome").value(5000.00))
                .andExpect(jsonPath("$.overview.totalExpenses").value(2100.00))
                .andExpect(jsonPath("$.overview.freeBalance").value(2900.00))
                .andExpect(jsonPath("$.xray[0].level1").value("ESSENCIAL"))
                .andExpect(jsonPath("$.xray[0].percentOfIncome").value(22.00))
                .andExpect(jsonPath("$.topCuts[0].subcategory").value("Delivery"))
                .andExpect(jsonPath("$.fixedOperationalCost").value(1100.00))
                .andExpect(jsonPath("$.byDay[0].date").value("2026-11-05"))
                .andExpect(jsonPath("$.showBlackHoleWarning").value(true));
    }

    @Test
    @DisplayName("GET summary com month fora do formato MM-YYYY deve retornar 400")
    void summaryComMonthInvalidoDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/expenses/summary").param("month", "2026/11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("month deve estar no formato MM-YYYY (ex.: 11-2026)"));
    }
}
