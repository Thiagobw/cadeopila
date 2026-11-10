package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
import br.com.cadeopila.userservice.core.ports.in.DeleteUserUseCase;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteUserUseCaseImpl implements DeleteUserUseCase {

    private final UserRepositoryPort userRepository;

    public DeleteUserUseCaseImpl(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
    }
}
