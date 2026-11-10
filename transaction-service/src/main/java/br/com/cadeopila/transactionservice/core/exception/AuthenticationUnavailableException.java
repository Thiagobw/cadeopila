package br.com.cadeopila.transactionservice.core.exception;

/** O serviço de usuários (MS1) não respondeu à validação de credenciais; traduzido para HTTP 503. */
public class AuthenticationUnavailableException extends RuntimeException {

    public AuthenticationUnavailableException(String message) {
        super(message);
    }
}
