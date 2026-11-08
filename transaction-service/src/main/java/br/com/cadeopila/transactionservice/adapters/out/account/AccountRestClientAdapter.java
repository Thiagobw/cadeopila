package br.com.cadeopila.transactionservice.adapters.out.account;

import br.com.cadeopila.transactionservice.adapters.out.account.dto.AccountApiModel;
import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter da {@link AccountPort} sobre o MockAPI de contas — somente leitura (saldo inicial e limites).
 * Nunca deixa uma exceção HTTP escapar: conta ausente → {@code Optional.empty()};
 * falha técnica → {@link AccountUnavailableException}. Os logs não registram a URL (token sensível).
 */
@Component
public class AccountRestClientAdapter implements AccountPort {

    private static final Logger log = LoggerFactory.getLogger(AccountRestClientAdapter.class);

    private final RestClient restClient;

    public AccountRestClientAdapter(@Qualifier("accountRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<AccountSnapshot> findByUserId(UUID userId) {
        AccountApiModel[] accounts;
        try {
            accounts = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/accounts").queryParam("userId", userId).build())
                    .retrieve()
                    .body(AccountApiModel[].class);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Falha ao consultar a API de contas (userId={}, erro={})", userId, ex.getClass().getSimpleName());
            throw new AccountUnavailableException("Falha ao consultar a API de contas");
        }

        // O filtro do MockAPI pode não ser exato: garantimos a igualdade do userId aqui.
        List<AccountApiModel> matches = Arrays.stream(accounts == null ? new AccountApiModel[0] : accounts)
                .filter(account -> userId.toString().equals(account.userId()))
                .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            log.warn("API de contas retornou mais de uma conta para o usuário (userId={})", userId);
            throw new AccountUnavailableException("Resposta ambígua da API de contas");
        }
        return Optional.of(toSnapshot(matches.getFirst()));
    }

    private AccountSnapshot toSnapshot(AccountApiModel model) {
        return new AccountSnapshot(
                UUID.fromString(model.userId()),
                model.balance(),
                model.dailyLimit(),
                model.transactionLimit());
    }
}
