package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindUserByIdUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private FindUserByIdUseCaseImpl useCase;

    @Test
    @DisplayName("Deve retornar o usuário quando encontrado pelo id")
    void deveRetornarUsuarioQuandoEncontrado() {
        // Given
        var id = UUID.randomUUID();
        var now = LocalDateTime.now();
        var user = new User(id, "Maria", "maria@email.com", "12345678901", "HASH", now, now);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // When
        var result = useCase.findById(id);

        // Then
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("Deve lançar not found quando o usuário não existe")
    void deveLancarNotFoundQuandoNaoEncontrado() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> useCase.findById(id))
                .isInstanceOf(UserNotFoundException.class);
    }
}
