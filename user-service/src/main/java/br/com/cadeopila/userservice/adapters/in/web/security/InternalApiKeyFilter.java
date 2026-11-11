package br.com.cadeopila.userservice.adapters.in.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gate de comunicação máquina-a-máquina (M2M) do endpoint interno {@code POST /api/v1/auth/validate}.
 * Exige o header {@code X-Internal-Key} idêntico à chave configurada ({@code INTERNAL_API_KEY}); ausente,
 * vazia ou diferente → 401, sem repassar a requisição adiante. É registrado apenas nessa rota pelo
 * {@link InternalApiSecurityConfig} — o restante da API (CRUD, Swagger, health) não é afetado.
 *
 * <p>Secure-by-default: se a chave não estiver configurada no serviço, o endpoint fica <b>fechado</b>
 * (nega tudo) em vez de aberto.</p>
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String UNAUTHORIZED_BODY = "{\"status\":401,\"title\":\"Não autenticado\","
            + "\"detail\":\"Chave interna ausente ou inválida.\"}";

    private final String expectedKey;

    public InternalApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isAuthorized(request.getHeader(INTERNAL_KEY_HEADER))) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String providedKey) {
        if (expectedKey == null || expectedKey.isBlank() || providedKey == null) {
            return false;
        }
        // Comparação em tempo constante: não vaza, por timing, quantos caracteres da chave conferem.
        return MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
