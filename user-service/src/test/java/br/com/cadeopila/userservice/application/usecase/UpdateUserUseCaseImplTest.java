package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import br.com.cadeopila.userservice.core.ports.out.PasswordEncoderPort;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @InjectMocks
    private UpdateUserUseCaseImpl useCase;

    private User existingUser(UUID id) {
        var createdAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        return new User(id, "Maria", "maria@email.com", "12345678901", "HASH_ANTIGO", createdAt, createdAt);
    }

    @Test
    @DisplayName("Deve atualizar o usuário salvando o email normalizado")
    void deveAtualizarSalvandoEmailNormalizado() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id)));
        when(userRepository.existsByEmailAndIdNot("nova.maria@email.com", id)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var command = new UpdateUserUseCase.Command(id, "Maria Nova", "  Nova.Maria@Email.COM ", null);

        // When
        useCase.update(command);

        // Then
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nova.maria@email.com");
    }

    @Test
    @DisplayName("Deve hashear a nova senha quando informada")
    void deveHashearNovaSenhaQuandoInformada() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id)));
        when(userRepository.existsByEmailAndIdNot("maria@email.com", id)).thenReturn(false);
        when(passwordEncoder.encode("novaSenha")).thenReturn("HASH_NOVO");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var command = new UpdateUserUseCase.Command(id, "Maria", "maria@email.com", "novaSenha");

        // When
        useCase.update(command);

        // Then
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASH_NOVO");
    }

    @Test
    @DisplayName("Deve manter a senha atual quando a nova senha não é informada")
    void deveManterSenhaAtualQuandoSenhaNaoInformada() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id)));
        when(userRepository.existsByEmailAndIdNot("maria@email.com", id)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var command = new UpdateUserUseCase.Command(id, "Maria", "maria@email.com", "   ");

        // When
        useCase.update(command);

        // Then
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASH_ANTIGO");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Deve aceitar o próprio email em outra caixa sem gerar conflito")
    void deveAceitarProprioEmailEmOutraCaixa() {
        // Given — usuário tem "maria@email.com"; atualiza para "MARIA@EMAIL.COM"
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id)));
        when(userRepository.existsByEmailAndIdNot("maria@email.com", id)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var command = new UpdateUserUseCase.Command(id, "Maria", "MARIA@EMAIL.COM", null);

        // When
        useCase.update(command);

        // Then — a consulta de duplicidade usa o email normalizado
        verify(userRepository).existsByEmailAndIdNot("maria@email.com", id);
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("maria@email.com");
    }

    @Test
    @DisplayName("Deve lançar conflito quando o email pertence a outro usuário")
    void deveLancarConflitoQuandoEmailDeOutroUsuario() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id)));
        when(userRepository.existsByEmailAndIdNot("outro@email.com", id)).thenReturn(true);
        var command = new UpdateUserUseCase.Command(id, "Maria", "Outro@Email.com", null);

        // When / Then
        assertThatThrownBy(() -> useCase.update(command))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar not found quando o usuário a atualizar não existe")
    void deveLancarNotFoundQuandoNaoEncontrado() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        var command = new UpdateUserUseCase.Command(id, "Maria", "maria@email.com", null);

        // When / Then
        assertThatThrownBy(() -> useCase.update(command))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }
}
