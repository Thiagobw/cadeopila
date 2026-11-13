package br.com.cadeopila.transactionservice.integration;

import br.com.cadeopila.transactionservice.adapters.out.persistence.TransactionJpaRepository;
import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criação ponta a ponta. O dono da transação vem do usuário autenticado (principal {@code USER}),
 * não mais do corpo. Filtros desligados no MockMvc ({@code addFilters = false}); o Basic Auth real é
 * coberto por {@code SecurityIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CreateTransactionIntegrationTest {

    private static final UUID USER = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final UUID DESTINATION = UUID.fromString("9f1c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionJpaRepository jpaRepository;

    @MockitoBean
    private AccountPort accountPort;

    @MockitoBean
    private ExchangeRatePort exchangeRatePort;

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

    private void accountWith(String balance, String dailyLimit, String transactionLimit) {
        when(accountPort.findByUserId(USER)).thenReturn(java.util.Optional.of(
                new AccountSnapshot(USER, new BigDecimal(balance),
                        new BigDecimal(dailyLimit), new BigDecimal(transactionLimit))));
    }

    private void withUsdQuote() {
        when(exchangeRatePort.quoteAll(any())).thenReturn(List.of(
                new ExchangeRate("USD", new BigDecimal("5.00"), "BrasilAPI", Instant.parse("2026-11-06T18:00:00Z"))));
    }

    @Test
    @DisplayName("Compra aprovada é gravada e devolve câmbio e saldo derivado")
    void compraAprovada() throws Exception {
        accountWith("1000.00", "10000.00", "5000.00");
        withUsdQuote();

        var body = """
                {"type":"PURCHASE","amount":150.00,"description":"Mercado","category":"FOOD"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.exchange.available").value(true))
                .andExpect(jsonPath("$.exchange.quotes[0].convertedAmount").value(30.00))
                .andExpect(jsonPath("$.account.balanceAfter").value(850.00));

        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Saque acima do saldo é REJEITADO, gravado e não altera o mock (critério do PO)")
    void saqueAcimaDoSaldoRejeitado() throws Exception {
        accountWith("50.00", "10000.00", "5000.00");
        withUsdQuote();

        var body = """
                {"type":"WITHDRAWAL","amount":100.00,"description":"Saque"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.account.balanceAfter").value(50.00));

        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("API de contas fora do ar: REJECTED/ACCOUNT_SERVICE_UNAVAILABLE, gravado, com câmbio disponível")
    void contasForaDoAr() throws Exception {
        when(accountPort.findByUserId(USER)).thenThrow(new AccountUnavailableException("fora do ar"));
        withUsdQuote();

        var body = """
                {"type":"DEPOSIT","amount":500.00,"description":"Depósito"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("ACCOUNT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.exchange.available").value(true))
                .andExpect(jsonPath("$.account.balanceAfter").doesNotExist());

        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Os quatro tipos são criados via API e gravados")
    void criaOsQuatroTipos() throws Exception {
        accountWith("100000.00", "1000000.00", "500000.00");
        withUsdQuote();

        post4("DEPOSIT", "\"amount\":500.00,\"description\":\"Depósito\"");
        post4("WITHDRAWAL", "\"amount\":100.00,\"description\":\"Saque\"");
        post4("PURCHASE", "\"amount\":150.00,\"description\":\"Compra\",\"category\":\"FOOD\"");
        post4("TRANSFER", "\"amount\":200.00,\"description\":\"PIX\",\"destinationUserId\":\"" + DESTINATION + "\"");

        assertThat(jpaRepository.count()).isEqualTo(4);
    }

    private void post4(String type, String extraFields) throws Exception {
        var body = "{\"type\":\"" + type + "\"," + extraFields + "}";
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Entrada inválida devolve 400 com ProblemDetail e não grava")
    void entradaInvalidaDevolve400() throws Exception {
        var body = """
                {"type":"DEPOSIT","amount":-1,"description":"x"}
                """;

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("amount"));

        assertThat(jpaRepository.count()).isZero();
    }
}
