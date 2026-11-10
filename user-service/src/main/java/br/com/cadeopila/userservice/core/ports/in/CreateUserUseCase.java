package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.User;

public interface CreateUserUseCase {

    User create(Command command);

    record Command(String name, String email, String cpf, String rawPassword) {}
}
