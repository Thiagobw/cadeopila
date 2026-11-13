package br.com.cadeopila.transactionservice.core.ports.in;

import java.math.BigDecimal;
import java.util.UUID;

public interface GetAccountBalanceUseCase {

    Balance get(UUID userId);

    /**
     * Saldo atual (derivado do saldo inicial da Mock + histórico aprovado do MS2) e limites vindos da
     * Mock, mais o consumo do dia. {@code availableToday = dailyLimit − spentToday}.
     */
    record Balance(
            BigDecimal balance,
            BigDecimal dailyLimit,
            BigDecimal transactionLimit,
            BigDecimal spentToday,
            BigDecimal availableToday
    ) {}
}
