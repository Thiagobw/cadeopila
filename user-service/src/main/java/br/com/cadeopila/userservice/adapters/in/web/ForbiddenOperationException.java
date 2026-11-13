package br.com.cadeopila.userservice.adapters.in.web;

/**
 * Autorização em nível de objeto negada: o usuário autenticado tentou acessar ou alterar um recurso que
 * não é seu (ex.: editar/excluir a conta de outro usuário trocando o {@code id} do path). Mapeada para
 * {@code 403 Forbidden} pelo {@code GlobalExceptionHandler}.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
