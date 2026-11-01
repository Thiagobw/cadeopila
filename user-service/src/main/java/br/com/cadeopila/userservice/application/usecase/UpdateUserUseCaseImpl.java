package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UpdateUserUseCaseImpl implements UpdateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;

    public UpdateUserUseCaseImpl(UserRepositoryPort userRepository,
                                 PasswordEncoderPort passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User update(Command command) {
        var existing = userRepository.findById(command.id())
                .orElseThrow(() -> new UserNotFoundException(command.id()));

        if (userRepository.existsByEmailAndIdNot(command.email(), command.id())) {
            throw new UserAlreadyExistsException("email", command.email());
        }

        boolean changePassword = command.newRawPassword() != null
                && !command.newRawPassword().isBlank();

        var updated = new User(
                existing.getId(),
                command.name(),
                command.email(),
                existing.getCpf(),
                changePassword
                        ? passwordEncoder.encode(command.newRawPassword())
                        : existing.getPasswordHash(),
                existing.getCreatedAt(),
                LocalDateTime.now()
        );

        return userRepository.save(updated);
    }
}
