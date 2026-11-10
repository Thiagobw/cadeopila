package br.com.cadeopila.userservice.core.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Usuário não encontrado com id: " + id);
    }
}
