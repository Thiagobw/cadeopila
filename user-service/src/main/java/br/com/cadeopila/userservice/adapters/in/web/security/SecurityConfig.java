package br.com.cadeopila.userservice.adapters.in.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Basic Auth do CRUD de usuários (GAP #2).
 *
 * <p>Estratégia de credenciais: as <b>mesmas</b> de {@code POST /api/v1/auth/validate} — o usuário
 * se autentica com o próprio e-mail + senha, validados contra o banco pelo
 * {@link CustomUserDetailsService} (hash BCrypt). Não há usuário/senha fixos.</p>
 *
 * <p><b>Rotas públicas</b> (sem credencial):
 * <ul>
 *   <li>{@code POST /api/v1/users} e {@code POST /api/v1/users/batch} — cadastro (individual e em massa);
 *       são o <i>bootstrap</i> que cria os primeiros usuários.</li>
 *   <li>{@code /api/v1/auth/**} — já protegido pelo {@link InternalApiKeyFilter} (gate M2M nativo que roda
 *       <i>antes</i> desta cadeia); aqui apenas liberamos para não exigir Basic Auth em cima do gate.</li>
 *   <li>Swagger / OpenAPI e Actuator/health.</li>
 * </ul>
 * Todo o restante de {@code /api/v1/users/**} (listar, buscar, atualizar, excluir) exige Basic Auth.</p>
 *
 * <p>Sem ciclo de dependências: esta classe NÃO injeta {@code AuthenticationManager} nem
 * {@code UserDetailsService}; o {@code DaoAuthenticationProvider} é montado pelo Spring a partir dos beans
 * {@link CustomUserDetailsService} e {@link #passwordEncoder()}. O Spring Security não vaza para o core.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                // API REST stateless: sem CSRF (senão o POST público de cadastro quebraria) e sem sessão.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Cadastro público (individual + em massa). Mantêm a ordem: permits ANTES do catch-all.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/batch").permitAll()
                        // Rota interna M2M: o gate real é o InternalApiKeyFilter (nativo, roda antes).
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Documentação e health.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Resto do CRUD de usuários: autenticado.
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /**
     * Encoder usado pelo fluxo de autenticação do Spring Security para conferir a senha do Basic Auth
     * contra o hash BCrypt gravado. É independente do {@code PasswordEncoderPort} do core (que continua
     * cuidando do hashing na criação do usuário) — ambos usam BCrypt e são interoperáveis.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
