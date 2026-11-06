package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à conta do dono (saldo e limites) na origem externa. Implementações traduzem
 * falhas técnicas para {@code AccountUnavailableException}; conta ausente é {@code Optional.empty()}.
 */
public interface AccountPort {

    Optional<AccountSnapshot> findByUserId(UUID userId);

    void updateBalance(String accountId, BigDecimal newBalance);
}
