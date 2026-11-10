package br.com.cadeopila.transactionservice.core.ports.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Autenticação do usuário numa origem externa (o user-service / MS1): valida e-mail + senha e,
 * se válidas, devolve o id do usuário. Mantém o core agnóstico de HTTP/Spring — a implementação
 * (adapter) é quem fala com o MS1. Credenciais inválidas são {@link Optional#empty()}; falha técnica
 * da origem vira
 * {@link br.com.cadeopila.transactionservice.core.exception.AuthenticationUnavailableException}.
 */
public interface UserAuthenticationPort {

    Optional<UUID> authenticate(String email, String rawPassword);
}
