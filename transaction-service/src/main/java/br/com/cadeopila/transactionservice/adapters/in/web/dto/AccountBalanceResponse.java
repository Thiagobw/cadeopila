package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Saldo atual e limites da conta")
public record AccountBalanceResponse(

        @Schema(description = "Saldo atual (saldo inicial da Mock + histórico aprovado do MS2)", example = "1234.56")
        BigDecimal balance,
        @Schema(example = "2000.00") BigDecimal dailyLimit,
        @Schema(example = "5000.00") BigDecimal transactionLimit,
        @Schema(description = "Saídas aprovadas no dia corrente", example = "350.00") BigDecimal spentToday,
        @Schema(description = "dailyLimit − spentToday", example = "1650.00") BigDecimal availableToday
) {}
