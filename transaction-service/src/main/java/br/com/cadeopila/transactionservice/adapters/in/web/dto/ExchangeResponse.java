package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Bloco de câmbio (conversão apenas para exibição; o valor da transação é sempre em BRL)")
public record ExchangeResponse(

        @Schema(description = "Se há cotação disponível", example = "true") boolean available,
        @Schema(description = "Valor da transação em BRL", example = "150.00") BigDecimal amountBrl,
        @Schema(description = "Mensagem quando o câmbio está indisponível", example = "null") String message,
        List<ExchangeQuoteResponse> quotes
) {}
