package br.com.cadeopila.userservice.adapters.in.web.security;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("Carrega o usuário por e-mail e devolve UserDetails com o hash gravado")
    void carregaUsuarioExistente() {
        var now = LocalDateTime.now();
        var user = new User(UUID.randomUUID(), "Maria", "maria@email.com", "12345678901", "HASH_BCRYPT", now, now);
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("maria@email.com");

        assertThat(details.getUsername()).isEqualTo("maria@email.com");
        assertThat(details.getPassword()).isEqualTo("HASH_BCRYPT");
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Normaliza o e-mail (trim + minúsculas) antes de buscar")
    void normalizaEmailAntesDeBuscar() {
        var now = LocalDateTime.now();
        when(userRepository.findByEmail("maria@email.com"))
                .thenReturn(Optional.of(new User(UUID.randomUUID(), "Maria", "maria@email.com",
                        "12345678901", "HASH", now, now)));

        service.loadUserByUsername("  Maria@Email.COM  ");

        verify(userRepository).findByEmail("maria@email.com");
    }

    @Test
    @DisplayName("E-mail inexistente lança UsernameNotFoundException genérica")
    void emailInexistenteLanca() {
        when(userRepository.findByEmail("nao@existe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nao@existe.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
