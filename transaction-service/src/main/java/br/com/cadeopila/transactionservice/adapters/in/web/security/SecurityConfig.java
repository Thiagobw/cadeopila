package br.com.cadeopila.transactionservice.adapters.in.web.security;

import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Segurança do MS2: autenticação via Basic Auth delegada ao user-service (MS1) pelo
 * {@link BasicAuthenticationViaUserServiceFilter}. API stateless (sem sessão) e sem CSRF (REST sem
 * cookies). A documentação (Swagger/OpenAPI) e o health ficam abertos; todo o resto exige autenticação.
 *
 * <p>O filtro e o entry point são construídos aqui (não são beans component-scanned): assim os slices
 * de teste de controller não precisam deles, e o filtro não é auto-registrado como servlet filter.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserAuthenticationPort userAuthenticationPort) throws Exception {
        var authenticationFilter = new BasicAuthenticationViaUserServiceFilter(userAuthenticationPort);
        var authenticationEntryPoint = new RestAuthenticationEntryPoint();
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
