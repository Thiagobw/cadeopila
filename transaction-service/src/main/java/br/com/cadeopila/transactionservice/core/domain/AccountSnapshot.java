package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Retrato da conta do dono vindo da porta de conta (API externa, somente leitura):
 * o {@code balance} é o <b>saldo inicial</b> e {@code dailyLimit}/{@code transactionLimit} são os limites.
 * O saldo atual é derivado pelo caso de uso somando o histórico de transações aprovadas do MS2.
 */
public record AccountSnapshot(
        UUID userId,
        BigDecimal balance,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit
) {}
