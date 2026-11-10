package br.com.cadeopila.transactionservice.adapters.out.account.dto;

import java.math.BigDecimal;

/**
 * Representação da conta no MockAPI (recurso {@code accounts}), somente leitura.
 * {@code userId} é string (UUID do MS1); {@code balance} é o saldo inicial.
 */
public record AccountApiModel(
        String id,
        String userId,
        BigDecimal balance,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit
) {}
