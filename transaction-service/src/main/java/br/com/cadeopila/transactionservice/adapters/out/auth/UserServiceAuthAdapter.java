package br.com.cadeopila.transactionservice.adapters.out.auth;

import br.com.cadeopila.transactionservice.adapters.out.auth.dto.AuthenticatedUserApiResponse;
import br.com.cadeopila.transactionservice.adapters.out.auth.dto.ValidateCredentialsApiRequest;
import br.com.cadeopila.transactionservice.core.exception.AuthenticationUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.UserAuthenticationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter da {@link UserAuthenticationPort} sobre o endpoint de validação do user-service (MS1):
 * {@code POST /api/v1/auth/validate}. Autentica-se nessa chamada M2M com o header {@code X-Internal-Key}
 * ({@code INTERNAL_API_KEY}). 200 → userId; 401 → credenciais inválidas ({@code Optional.empty()});
 * falha técnica (timeout/5xx) → {@link AuthenticationUnavailableException}. A senha só trafega nesta
 * chamada e nunca é registrada em log.
 */
@Component
public class UserServiceAuthAdapter implements UserAuthenticationPort {

    static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final Logger log = LoggerFactory.getLogger(UserServiceAuthAdapter.class);

    private final RestClient restClient;
    private final String internalApiKey;

    public UserServiceAuthAdapter(@Qualifier("userRestClient") RestClient restClient,
                                  @Value("${app.user.internal-api-key:}") String internalApiKey) {
        this.restClient = restClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<UUID> authenticate(String email, String rawPassword) {
        try {
            var response = restClient.post()
                    .uri("/api/v1/auth/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .body(new ValidateCredentialsApiRequest(email, rawPassword))
                    .retrieve()
                    .body(AuthenticatedUserApiResponse.class);
            if (response == null || response.userId() == null) {
                return Optional.empty();
            }
            return Optional.of(response.userId());
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Falha ao validar credenciais no user-service (erro={})", ex.getClass().getSimpleName());
            throw new AuthenticationUnavailableException("Falha ao validar credenciais no user-service");
        }
    }
}
