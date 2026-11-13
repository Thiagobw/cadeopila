package br.com.cadeopila.userservice.adapters.in.web.security;

import br.com.cadeopila.userservice.core.domain.Email;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Ponte entre o Spring Security e o nosso modelo: carrega o usuário por e-mail (reaproveitando a port de
 * persistência {@link UserRepositoryPort#findByEmail}) e devolve um {@link UserDetails} com o hash BCrypt já
 * gravado. A comparação da senha é feita pelo {@code DaoAuthenticationProvider} com o {@code PasswordEncoder}.
 *
 * <p>Fica na borda (adapters/in/web/security): o core não conhece Spring Security. O e-mail é normalizado
 * (trim + minúsculas) com a MESMA regra da criação/validação, para o login casar com o valor persistido.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepositoryPort userRepository;

    public CustomUserDetailsService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var normalizedEmail = Email.normalize(username);
        var user = userRepository.findByEmail(normalizedEmail)
                // Mensagem genérica: não distingue "e-mail inexistente" de "senha errada".
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.emptyList()) // sem papéis: basta estar autenticado
                .build();
    }
}
