package br.com.cadeopila.transactionservice.core.exception;

import java.util.UUID;

/**
 * Conta inexistente na API externa para o usuário consultado. Mapeada para 404 na rota de saldo.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId) {
        super("Conta não encontrada para o usuário: " + userId);
    }
}
