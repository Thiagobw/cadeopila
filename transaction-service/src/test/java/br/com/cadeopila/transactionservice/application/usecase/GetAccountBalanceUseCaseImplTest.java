package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.exception.AccountNotFoundException;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceUseCaseImplTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountPort accountPort;
    @Mock
    private TransactionRepositoryPort transactionRepository;

    private GetAccountBalanceUseCaseImpl useCase() {
        return new GetAccountBalanceUseCaseImpl(accountPort, transactionRepository, CLOCK, ZONE);
    }

    @Test
    @DisplayName("Deve derivar o saldo (Mock + histórico) e calcular gasto/disponível do dia")
    void deveCalcularSaldoDerivado() {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(
                new AccountSnapshot(USER, new BigDecimal("1000.00"), new BigDecimal("10000.00"), new BigDecimal("5000.00"))));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(new BigDecimal("200.00"));
        when(transactionRepository.sumApprovedOutflowsOfDay(any(), any(), any())).thenReturn(new BigDecimal("300.00"));

        var balance = useCase().get(USER);

        assertThat(balance.balance()).isEqualByComparingTo("1200.00");
        assertThat(balance.dailyLimit()).isEqualByComparingTo("10000.00");
        assertThat(balance.transactionLimit()).isEqualByComparingTo("5000.00");
        assertThat(balance.spentToday()).isEqualByComparingTo("300.00");
        assertThat(balance.availableToday()).isEqualByComparingTo("9700.00");
    }

    @Test
    @DisplayName("Deve lançar 404 quando a conta não existe")
    void deveLancar404() {
        when(accountPort.findByUserId(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().get(USER)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Deve propagar indisponibilidade da API de contas (vira 503 no web)")
    void devePropagarIndisponibilidade() {
        when(accountPort.findByUserId(USER)).thenThrow(new AccountUnavailableException("fora do ar"));

        assertThatThrownBy(() -> useCase().get(USER)).isInstanceOf(AccountUnavailableException.class);
    }
}
