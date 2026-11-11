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

    /**
     * Hash BCrypt real e fixo ($2a$, custo 10), usado só como "isca de tempo" quando o e-mail não existe.
     * Rodar o BCrypt contra ele faz o caminho "usuário inexistente" gastar o mesmo trabalho de CPU do
     * caminho "usuário existe, senha errada", equalizando o tempo de resposta e impedindo que um atacante
     * descubra, por diferença de tempo, quais e-mails estão cadastrados (timing attack / enumeração).
     * Precisa ser um hash VÁLIDO: um valor malformado faria o BCrypt retornar na hora, sem gastar CPU.
     * Nenhuma senha de usuário corresponde a ele — o resultado do matches é sempre descartado.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$waNsZGPCkqLgZh8985rK6uNY4jG1dk3by4Kf/KX1GRE2qFb1rZgti";

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

        var user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            // Defesa contra timing attack: roda o BCrypt contra um hash fixo (descartando o resultado)
            // para gastar o mesmo tempo de uma verificação real antes de recusar. Sem isso, o e-mail
            // inexistente responderia na hora e denunciaria, por timing, quais e-mails existem.
            passwordEncoder.matches(command.rawPassword(), DUMMY_PASSWORD_HASH);
            throw new InvalidCredentialsException();
        }

        // A senha nunca deixa o MS1: comparamos o texto puro com o hash BCrypt já gravado.
        if (!passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
