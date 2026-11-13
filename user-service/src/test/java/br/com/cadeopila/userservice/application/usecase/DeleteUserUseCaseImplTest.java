package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.exception.UserNotFoundException;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private DeleteUserUseCaseImpl useCase;

    @Test
    @DisplayName("Deve excluir o usuário quando ele existe")
    void deveExcluirUsuarioQuandoExiste() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        // When
        useCase.delete(id);

        // Then
        verify(userRepository).deleteById(id);
    }

    @Test
    @DisplayName("Deve lançar not found e não excluir quando o usuário não existe")
    void deveLancarNotFoundQuandoNaoExiste() {
        // Given
        var id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> useCase.delete(id))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).deleteById(id);
    }
}
