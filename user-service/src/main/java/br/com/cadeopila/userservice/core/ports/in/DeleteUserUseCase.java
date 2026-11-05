package br.com.cadeopila.userservice.core.ports.in;

import java.util.UUID;

public interface DeleteUserUseCase {

    void delete(UUID id);
}
