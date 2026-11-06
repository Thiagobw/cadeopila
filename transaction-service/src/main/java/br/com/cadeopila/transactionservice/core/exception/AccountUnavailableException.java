package br.com.cadeopila.transactionservice.core.exception;

/**
 * Falha técnica ao comunicar com a API de contas (timeout, erro de rede, 5xx, resposta ambígua).
 * O caso de uso a traduz para uma transação REJECTED com motivo ACCOUNT_SERVICE_UNAVAILABLE — nunca
 * chega ao handler HTTP.
 *
 * <p>Carrega apenas mensagem genérica, sem causa: a mensagem de exceções HTTP pode conter a URL da
 * API de contas (o token fica no subdomínio). Não registre a URL em log, exceção ou ProblemDetail.</p>
 */
public class AccountUnavailableException extends RuntimeException {

    public AccountUnavailableException(String message) {
        super(message);
    }
}
