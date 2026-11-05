package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.Email;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;

    public CreateUserUseCaseImpl(UserRepositoryPort userRepository,
                                 PasswordEncoderPort passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User create(Command command) {
        var normalizedEmail = new Email(command.email()).value();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("email", normalizedEmail);
        }
        if (userRepository.existsByCpf(command.cpf())) {
            throw new UserAlreadyExistsException("cpf", command.cpf());
        }

        var now = LocalDateTime.now();
        var user = new User(
                null,
                command.name(),
                normalizedEmail,
                command.cpf(),
                passwordEncoder.encode(command.rawPassword()),
                now,
                now
        );

        return userRepository.save(user);
    }
}
