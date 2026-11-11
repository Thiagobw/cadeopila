package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.Email;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.InvalidCredentialsException;
import br.com.cadeopila.userservice.core.ports.in.ValidateCredentialsUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidateCredentialsUseCaseImpl implements ValidateCredentialsUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;

    public ValidateCredentialsUseCaseImpl(UserRepositoryPort userRepository,
                                          PasswordEncoderPort passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public User validate(Command command) {
        // Mesma normalização usada na criação (trim + lower): o e-mail é gravado normalizado.
        var normalizedEmail = Email.normalize(command.email());

        var user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        // A senha nunca deixa o MS1: comparamos o texto puro com o hash BCrypt já gravado.
        if (!passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
