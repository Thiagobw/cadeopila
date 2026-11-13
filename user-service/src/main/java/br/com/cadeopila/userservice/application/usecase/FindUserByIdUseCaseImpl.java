package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
import br.com.cadeopila.userservice.core.ports.in.FindUserByIdUseCase;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FindUserByIdUseCaseImpl implements FindUserByIdUseCase {

    private final UserRepositoryPort userRepository;

    public FindUserByIdUseCaseImpl(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
