package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cotação gravada como snapshot no momento da criação da transação: taxa usada, valor já convertido
 * e a origem da taxa ({@code source}). A leitura posterior não depende de API externa.
 */
public record ExchangeQuote(
        String currency,
        BigDecimal rate,
        BigDecimal convertedAmount,
        String source,
        Instant quotedAt
) {}
