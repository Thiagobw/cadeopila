package br.com.cadeopila.userservice.adapters.in.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link InternalApiKeyFilter} exclusivamente na rota interna de validação de credenciais,
 * sem tocar no resto da API. A chave vem de {@code INTERNAL_API_KEY} (a mesma que o MS2 envia ao chamar
 * este endpoint). Como o MS1 não usa Spring Security, o filtro é registrado como servlet filter nativo.
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
