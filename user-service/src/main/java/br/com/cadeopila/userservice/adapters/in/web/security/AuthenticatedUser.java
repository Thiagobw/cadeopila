package br.com.cadeopila.userservice.adapters.in.web.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/**
 * Principal do Basic Auth que carrega o {@code id} do usuário além do e-mail. Sem ele, o
 * {@code @AuthenticationPrincipal} só exporia o e-mail e os endpoints de {@code /api/v1/users/{id}} não
 * teriam como verificar se o autenticado é o dono do recurso. É assim que a autorização em nível de objeto
 * (cada um só mexe na própria conta) passa a ser possível no controller, sem vazar Spring Security para o core.
 */
public class AuthenticatedUser extends User {

    private final transient UUID id;

    public AuthenticatedUser(UUID id, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
