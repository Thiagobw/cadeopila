package br.com.cadeopila.userservice.core.exception;

/**
 * Credenciais de autenticação inválidas (e-mail inexistente OU senha incorreta).
 * A mensagem é propositalmente genérica: não revela qual dos dois falhou, evitando
 * enumeração de usuários. Sempre traduzida para HTTP 401 no handler.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas");
    }
}
