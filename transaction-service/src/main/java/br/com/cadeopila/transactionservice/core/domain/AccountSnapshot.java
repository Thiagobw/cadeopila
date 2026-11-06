package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Retrato da conta do dono vindo da porta de conta: saldo e limites usados pela
 * {@link TransactionPolicy}. {@code accountId} é o identificador na origem (para gravar o novo saldo).
 */
public record AccountSnapshot(
        String accountId,
        UUID userId,
        BigDecimal balance,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit
) {}
