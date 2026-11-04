package br.com.cadeopila.userservice.adapters.out.persistence;

import br.com.cadeopila.userservice.adapters.out.persistence.mapper.UserPersistenceMapper;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

    @Mock
    private UserJpaRepository jpaRepository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(jpaRepository, new UserPersistenceMapper());
    }

    private User sampleUser() {
        var now = LocalDateTime.now();
        return new User(UUID.randomUUID(), "Maria", "maria@email.com", "12345678901", "HASH", now, now);
    }

    private DataIntegrityViolationException diveWithConstraint(String constraintName) {
        var hibernateCause = new org.hibernate.exception.ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key"), constraintName);
        return new DataIntegrityViolationException("conflito de integridade", hibernateCause);
    }

    @Test
    @DisplayName("Deve traduzir violação da constraint de email para conflito de email")
    void deveTraduzirViolacaoDeEmail() {
        // Given
        when(jpaRepository.saveAndFlush(any(UserJpaEntity.class)))
                .thenThrow(diveWithConstraint("uq_users_email"));

        // When / Then
        assertThatThrownBy(() -> adapter.save(sampleUser()))
                .asInstanceOf(type(UserAlreadyExistsException.class))
                .extracting(UserAlreadyExistsException::getField)
                .isEqualTo("email");
    }

    @Test
    @DisplayName("Deve traduzir violação da constraint de CPF para conflito de CPF com valor mascarado")
    void deveTraduzirViolacaoDeCpf() {
        // Given
        when(jpaRepository.saveAndFlush(any(UserJpaEntity.class)))
                .thenThrow(diveWithConstraint("uq_users_cpf"));

        // When / Then
        assertThatThrownBy(() -> adapter.save(sampleUser()))
                .asInstanceOf(type(UserAlreadyExistsException.class))
                .satisfies(ex -> {
                    assertThat(ex.getField()).isEqualTo("cpf");
                    assertThat(ex.getValue()).doesNotContain("12345678901");
                });
    }

    @Test
    @DisplayName("Deve traduzir violação de constraint desconhecida para conflito genérico")
    void deveTraduzirViolacaoDesconhecida() {
        // Given
        when(jpaRepository.saveAndFlush(any(UserJpaEntity.class)))
                .thenThrow(diveWithConstraint("qualquer_outra_constraint"));

        // When / Then
        assertThatThrownBy(() -> adapter.save(sampleUser()))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Deve retornar o domínio quando o save é bem-sucedido")
    void deveRetornarDominioQuandoSaveComSucesso() {
        // Given
        var user = sampleUser();
        when(jpaRepository.saveAndFlush(any(UserJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        var result = adapter.save(user);

        // Then
        assertThat(result.getEmail()).isEqualTo("maria@email.com");
        assertThat(result.getCpf()).isEqualTo("12345678901");
    }
}
