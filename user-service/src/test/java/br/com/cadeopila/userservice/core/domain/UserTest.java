package br.com.cadeopila.userservice.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("Deve mascarar o CPF expondo apenas os dois últimos dígitos")
    void deveMascararCpf() {
        // Given
        var user = new User(UUID.randomUUID(), "Maria", "maria@email.com",
                "12345678901", "HASH", LocalDateTime.now(), LocalDateTime.now());

        // When / Then
        assertThat(user.maskedCpf()).isEqualTo("***.***.***-01");
    }

    @Test
    @DisplayName("Deve devolver máscara genérica quando o CPF é nulo")
    void deveMascararCpfNulo() {
        // Given
        var user = new User(UUID.randomUUID(), "Maria", "maria@email.com",
                null, "HASH", LocalDateTime.now(), LocalDateTime.now());

        // When / Then
        assertThat(user.maskedCpf()).isEqualTo("***.***.***-**");
    }

    @Test
    @DisplayName("Deve devolver máscara genérica quando o CPF tem menos de 11 dígitos")
    void deveMascararCpfCurto() {
        // Given
        var user = new User(UUID.randomUUID(), "Maria", "maria@email.com",
                "123", "HASH", LocalDateTime.now(), LocalDateTime.now());

        // When / Then
        assertThat(user.maskedCpf()).isEqualTo("***.***.***-**");
    }

    @Test
    @DisplayName("Deve preservar os valores fornecidos no construtor (imutabilidade)")
    void devePreservarValoresDoConstrutor() {
        // Given
        var id = UUID.randomUUID();
        var createdAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        var updatedAt = LocalDateTime.of(2021, 2, 2, 0, 0);

        // When
        var user = new User(id, "Maria", "maria@email.com", "12345678901", "HASH", createdAt, updatedAt);

        // Then
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getName()).isEqualTo("Maria");
        assertThat(user.getEmail()).isEqualTo("maria@email.com");
        assertThat(user.getCpf()).isEqualTo("12345678901");
        assertThat(user.getPasswordHash()).isEqualTo("HASH");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
