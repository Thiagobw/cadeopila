package br.com.cadeopila.userservice.adapters.in.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link InternalApiKeyFilter} exclusivamente na rota interna de validação de credenciais,
 * sem tocar no resto da API. A chave vem de {@code INTERNAL_API_KEY} (a mesma que o MS2 envia ao chamar
 * este endpoint).
 *
 * <p>O gate M2M é um servlet filter <i>nativo</i> (não um filtro da cadeia do Spring Security), registrado
 * com {@link Ordered#HIGHEST_PRECEDENCE} para rodar ANTES da cadeia de segurança do {@link SecurityConfig}
 * (Basic Auth do CRUD, GAP #2). Dessa forma a validação da chave interna permanece independente do Basic
 * Auth: a rota {@code /api/v1/auth/validate} é liberada no {@link SecurityConfig} ({@code permitAll}) e
 * protegida, de fato, por esta chave M2M.</p>
 */
@Configuration
public class InternalApiSecurityConfig {

    private static final String VALIDATE_PATH = "/api/v1/auth/validate";

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter(
            @Value("${app.internal-api.key:}") String internalApiKey) {
        var registration = new FilterRegistrationBean<>(new InternalApiKeyFilter(internalApiKey));
        registration.addUrlPatterns(VALIDATE_PATH);
        registration.setName("internalApiKeyFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
