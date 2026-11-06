package br.com.cadeopila.transactionservice.core.ports.out;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à conta do dono (saldo e limites) na origem externa. Implementações traduzem falhas
 * técnicas para {@link br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException};
 * conta ausente é {@code Optional.empty()}.
 */
public interface AccountPort {

    Optional<AccountSnapshot> findByUserId(UUID userId);

    /**
     * Grava o saldo de {@code account} na origem. Os demais campos do snapshot são reenviados
     * para que a atualização não descarte os limites da conta.
     */
    void updateBalance(AccountSnapshot account);
}
