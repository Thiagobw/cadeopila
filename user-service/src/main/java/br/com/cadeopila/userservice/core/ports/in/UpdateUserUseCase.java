package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.User;

import java.util.UUID;

public interface UpdateUserUseCase {

    User update(Command command);

    record Command(UUID id, String name, String email, String newRawPassword) {}
}
