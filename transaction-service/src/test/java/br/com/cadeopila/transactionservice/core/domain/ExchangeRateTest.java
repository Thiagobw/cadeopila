package br.com.cadeopila.transactionservice.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRateTest {

    private static final Instant QUOTED_AT = Instant.parse("2026-11-06T18:05:00Z");

    @Test
    @DisplayName("Deve converter BRL dividindo pela taxa, com duas casas decimais")
    void deveConverterComDuasCasas() {
        var rate = new ExchangeRate("USD", new BigDecimal("5.4321"), QUOTED_AT);

        var quote = rate.convert(new BigDecimal("150.00"));

        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.rate()).isEqualByComparingTo("5.4321");
        assertThat(quote.convertedAmount()).isEqualByComparingTo("27.61");
        assertThat(quote.convertedAmount().scale()).isEqualTo(2);
        assertThat(quote.quotedAt()).isEqualTo(QUOTED_AT);
    }

    @Test
    @DisplayName("Deve arredondar a conversão com HALF_EVEN nos empates")
    void deveArredondarComHalfEven() {
        var rate = new ExchangeRate("EUR", new BigDecimal("8.00"), QUOTED_AT);

        // 5.00 / 8.00 = 0.625 → empate em 0.62/0.63; HALF_EVEN escolhe o par: 0.62
        assertThat(rate.convert(new BigDecimal("5.00")).convertedAmount()).isEqualByComparingTo("0.62");
        // 7.00 / 8.00 = 0.875 → empate em 0.87/0.88; HALF_EVEN escolhe o par: 0.88
        assertThat(rate.convert(new BigDecimal("7.00")).convertedAmount()).isEqualByComparingTo("0.88");
    }
}
