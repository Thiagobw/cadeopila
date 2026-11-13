package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.PageQuery;
import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseImplTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private ListUsersUseCaseImpl useCase;

    @Test
    @DisplayName("Deve repassar os parâmetros de paginação ao repositório")
    void deveRepassarParametrosDePaginacao() {
        // Given
        var expected = new PageResult<User>(List.of(), 0, 0, 2, 10);
        when(userRepository.findAll(2, 10)).thenReturn(expected);

        // When
        var result = useCase.list(new PageQuery(2, 10));

        // Then
        verify(userRepository).findAll(2, 10);
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("Deve retornar resultado vazio quando não há usuários")
    void deveRetornarResultadoVazio() {
        // Given
        var empty = new PageResult<User>(List.of(), 0, 0, 0, 20);
        when(userRepository.findAll(0, 20)).thenReturn(empty);

        // When
        var result = useCase.list(new PageQuery(0, 20));

        // Then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
