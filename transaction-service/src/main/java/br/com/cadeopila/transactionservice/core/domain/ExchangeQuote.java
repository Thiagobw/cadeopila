package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cotação gravada como snapshot no momento da criação da transação: taxa usada
 * e valor já convertido. A leitura posterior não depende de API externa.
 */
public record ExchangeQuote(
        String currency,
        BigDecimal rate,
        BigDecimal convertedAmount,
        Instant quotedAt
) {}
