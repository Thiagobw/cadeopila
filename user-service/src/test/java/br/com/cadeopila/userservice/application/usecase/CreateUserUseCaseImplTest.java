package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @InjectMocks
    private CreateUserUseCaseImpl useCase;

    @Test
    @DisplayName("Deve criar usuário com senha hasheada, email normalizado e sem persistir a senha crua")
    void deveCriarUsuarioComSenhaHasheadaEEmailNormalizado() {
        // Given
        var command = new CreateUserUseCase.Command(
                "Maria Souza", "  Maria.Souza@Email.COM  ", "12345678901", "senhaCrua");
        when(userRepository.existsByEmail("maria.souza@email.com")).thenReturn(false);
        when(userRepository.existsByCpf("12345678901")).thenReturn(false);
        when(passwordEncoder.encode("senhaCrua")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        useCase.create(command);

        // Then
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("maria.souza@email.com");
        assertThat(saved.getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getPasswordHash()).isNotEqualTo("senhaCrua");
        verify(passwordEncoder).encode("senhaCrua");
    }

    @Test
    @DisplayName("Deve consultar a duplicidade de email usando o valor normalizado")
    void deveConsultarDuplicidadeDeEmailNormalizado() {
        // Given
        var command = new CreateUserUseCase.Command(
                "Maria Souza", "  Maria.Souza@Email.COM  ", "12345678901", "senhaCrua");
        when(userRepository.existsByEmail("maria.souza@email.com")).thenReturn(false);
        when(userRepository.existsByCpf("12345678901")).thenReturn(false);
        when(passwordEncoder.encode("senhaCrua")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        useCase.create(command);

        // Then
        verify(userRepository).existsByEmail("maria.souza@email.com");
    }

    @Test
    @DisplayName("Deve lançar conflito e não salvar quando o email já existe")
    void deveLancarConflitoQuandoEmailJaExiste() {
        // Given
        var command = new CreateUserUseCase.Command(
                "Maria Souza", "maria@email.com", "12345678901", "senhaCrua");
        when(userRepository.existsByEmail("maria@email.com")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Deve lançar conflito e não salvar quando o CPF já existe")
    void deveLancarConflitoQuandoCpfJaExiste() {
        // Given
        var command = new CreateUserUseCase.Command(
                "Maria Souza", "maria@email.com", "12345678901", "senhaCrua");
        when(userRepository.existsByEmail("maria@email.com")).thenReturn(false);
        when(userRepository.existsByCpf("12345678901")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }
}
