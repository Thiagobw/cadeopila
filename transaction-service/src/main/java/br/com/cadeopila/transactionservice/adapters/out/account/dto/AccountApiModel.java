package br.com.cadeopila.transactionservice.adapters.out.account.dto;

import java.math.BigDecimal;

/**
 * Representação da conta no MockAPI (recurso {@code accounts}). {@code userId} é string (UUID do MS1).
 * Usada tanto na leitura (GET) quanto no corpo da atualização (PUT).
 */
public record AccountApiModel(
        String id,
        String userId,
        BigDecimal balance,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit
) {}
