package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Leitura da conta do dono (saldo inicial e limites) na origem externa — somente leitura, consultada
 * a cada transação. Implementações traduzem falhas técnicas para
 * {@link br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException};
 * conta ausente é {@code Optional.empty()}.
 */
public interface AccountPort {

    Optional<AccountSnapshot> findByUserId(UUID userId);
}
