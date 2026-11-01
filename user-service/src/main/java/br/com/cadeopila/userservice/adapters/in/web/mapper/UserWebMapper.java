package br.com.cadeopila.userservice.adapters.in.web.mapper;

import br.com.cadeopila.userservice.adapters.in.web.dto.CreateUserRequest;
import br.com.cadeopila.userservice.adapters.in.web.dto.UpdateUserRequest;
import br.com.cadeopila.userservice.adapters.in.web.dto.UserResponse;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserWebMapper {

    public CreateUserUseCase.Command toCommand(CreateUserRequest request) {
        return new CreateUserUseCase.Command(
                request.name(),
                request.email(),
                request.cpf(),
                request.password()
        );
    }

    public UpdateUserUseCase.Command toCommand(UUID id, UpdateUserRequest request) {
        return new UpdateUserUseCase.Command(
                id,
                request.name(),
                request.email(),
                request.password()
        );
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.maskedCpf(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
