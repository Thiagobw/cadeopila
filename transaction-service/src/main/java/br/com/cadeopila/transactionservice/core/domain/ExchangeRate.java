package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Taxa de câmbio de uma moeda numa data (cotação de venda), com a origem ({@code source}).
 * A conversão do valor em BRL é cálculo de domínio: {@code amountBrl / rate}, 2 casas, HALF_EVEN.
 */
public record ExchangeRate(
        String currency,
        BigDecimal rate,
        String source,
        Instant quotedAt
) {
    public ExchangeQuote convert(BigDecimal amountBrl) {
        var convertedAmount = amountBrl.divide(rate, 2, RoundingMode.HALF_EVEN);
        return new ExchangeQuote(currency, rate, convertedAmount, source, quotedAt);
    }
}
