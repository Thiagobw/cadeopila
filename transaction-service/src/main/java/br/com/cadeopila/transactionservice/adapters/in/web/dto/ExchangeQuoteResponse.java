package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Cotação de câmbio aplicada (snapshot)")
public record ExchangeQuoteResponse(

        @Schema(example = "USD") String currency,
        @Schema(example = "5.4321") BigDecimal rate,
        @Schema(description = "Valor convertido (amountBrl / rate)", example = "27.61") BigDecimal convertedAmount,
        @Schema(description = "Origem da taxa", example = "BrasilAPI") String source,
        @Schema(example = "2026-11-06T18:05:00Z") Instant quotedAt
) {}
