package br.com.cadeopila.transactionservice.adapters.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Resposta 401 em JSON (formato ProblemDetail) quando a requisição chega a um endpoint protegido sem
 * autenticação válida — mantém o mesmo formato de erro dos demais endpoints do serviço. Escreve o corpo
 * à mão (sem {@code ObjectMapper}) para não acoplar a cadeia de segurança a um bean de Jackson.
 * Instanciado pelo {@link SecurityConfig} (não é um {@code @Component}).
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = "{\"status\":401,\"title\":\"Não autenticado\","
            + "\"detail\":\"Autenticação Basic (e-mail e senha) é obrigatória.\"}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
