package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.User;

import java.util.UUID;

public interface FindUserByIdUseCase {

    User findById(UUID id);
}
