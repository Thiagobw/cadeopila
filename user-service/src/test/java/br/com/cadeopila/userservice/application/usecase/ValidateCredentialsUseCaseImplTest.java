package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.InvalidCredentialsException;
import br.com.cadeopila.userservice.core.ports.in.ValidateCredentialsUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateCredentialsUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private PasswordEncoderPort passwordEncoder;
    @InjectMocks
    private ValidateCredentialsUseCaseImpl useCase;

    private User sampleUser() {
        var now = LocalDateTime.now();
        return new User(UUID.randomUUID(), "Maria", "maria@email.com", "12345678901", "HASH", now, now);
    }

    @Test
    @DisplayName("Deve retornar o usuário quando e-mail existe e a senha confere")
    void deveRetornarUsuarioComCredenciaisValidas() {
        var user = sampleUser();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha123", "HASH")).thenReturn(true);

        var result = useCase.validate(new ValidateCredentialsUseCase.Command("maria@email.com", "senha123"));

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("Deve normalizar o e-mail (trim + lower) antes de buscar")
    void deveNormalizarEmailAntesDeBuscar() {
        var user = sampleUser();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha123", "HASH")).thenReturn(true);

        useCase.validate(new ValidateCredentialsUseCase.Command("  Maria@Email.COM  ", "senha123"));

        verify(userRepository).findByEmail("maria@email.com");
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentials quando o e-mail não existe (sem checar a senha)")
    void deveLancarQuandoEmailNaoExiste() {
        when(userRepository.findByEmail("fantasma@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.validate(
                new ValidateCredentialsUseCase.Command("fantasma@email.com", "senha123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentials quando a senha não confere")
    void deveLancarQuandoSenhaIncorreta() {
        var user = sampleUser();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> useCase.validate(
                new ValidateCredentialsUseCase.Command("maria@email.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
