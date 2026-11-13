package br.com.cadeopila.transactionservice.adapters.in.web.security;

import br.com.cadeopila.transactionservice.core.exception.AuthenticationUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autenticação Basic delegada ao user-service (MS1). Lê {@code Authorization: Basic ...}, decodifica
 * {@code e-mail:senha} e valida via {@link UserAuthenticationPort}. Credenciais válidas colocam o
 * <b>userId</b> como principal no {@link SecurityContextHolder} — o core/application nunca enxerga o
 * Spring Security; os controllers extraem esse id via {@code @AuthenticationPrincipal}. Credenciais
 * ausentes, malformadas ou inválidas seguem sem autenticar (o {@link RestAuthenticationEntryPoint}
 * responde 401); se o MS1 estiver indisponível, responde 503 aqui mesmo — o erro não chega ao controller.
 *
 * <p>Não é um {@code @Component} de propósito: é instanciado pelo {@link SecurityConfig} e entra apenas
 * na cadeia do Spring Security, evitando o auto-registro como servlet filter (que o executaria em dobro).</p>
 */
public class BasicAuthenticationViaUserServiceFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";
    private static final SimpleGrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");
    private static final String SERVICE_UNAVAILABLE_BODY = "{\"status\":503,\"title\":\"Serviço indisponível\","
            + "\"detail\":\"Não foi possível validar as credenciais no serviço de usuários. "
            + "Tente novamente mais tarde.\"}";

    private final UserAuthenticationPort userAuthenticationPort;

    public BasicAuthenticationViaUserServiceFilter(UserAuthenticationPort userAuthenticationPort) {
        this.userAuthenticationPort = userAuthenticationPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var credentials = extractCredentials(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (credentials.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                userAuthenticationPort.authenticate(credentials.get().email(), credentials.get().password())
                        .ifPresent(userId -> authenticate(userId, request));
            } catch (AuthenticationUnavailableException ex) {
                writeServiceUnavailable(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(UUID userId, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of(ROLE_USER));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<Credentials> extractCredentials(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BASIC_PREFIX)) {
            return Optional.empty();
        }
        try {
            var decoded = new String(
                    Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(new Credentials(decoded.substring(0, separator), decoded.substring(separator + 1)));
        } catch (IllegalArgumentException malformedBase64) {
            return Optional.empty();
        }
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(SERVICE_UNAVAILABLE_BODY);
    }

    private record Credentials(String email, String password) {}
}
