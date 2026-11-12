package br.com.cadeopila.userservice.adapters.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Resposta 401 no mesmo formato ProblemDetail do resto da API (e do {@link InternalApiKeyFilter}),
 * em vez do HTML/popup padrão do Basic. Sem {@code WWW-Authenticate} para não disparar o prompt do browser.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_BODY = "{\"status\":401,\"title\":\"Não autenticado\","
            + "\"detail\":\"Credenciais ausentes ou inválidas.\"}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
