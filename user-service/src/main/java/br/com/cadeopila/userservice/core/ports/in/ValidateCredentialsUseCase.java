package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.User;

/**
 * Valida as credenciais (e-mail + senha) de um usuário. Usado na autenticação server-to-server:
 * o transaction-service (MS2) repassa o Basic Auth recebido e, se válido, recebe o usuário
 * autenticado (de onde extrai o id). A senha em texto puro só existe durante a validação.
 */
public interface ValidateCredentialsUseCase {

    User validate(Command command);

    record Command(String email, String rawPassword) {}
}
