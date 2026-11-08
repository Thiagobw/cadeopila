package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.exception.AccountNotFoundException;
import br.com.cadeopila.transactionservice.core.ports.in.GetAccountBalanceUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class GetAccountBalanceUseCaseImpl implements GetAccountBalanceUseCase {

    private final AccountPort accountPort;
    private final TransactionRepositoryPort transactionRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public GetAccountBalanceUseCaseImpl(AccountPort accountPort,
                                        TransactionRepositoryPort transactionRepository,
                                        Clock clock,
                                        ZoneId zoneId) {
        this.accountPort = accountPort;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Override
    @Transactional(readOnly = true)
    public Balance get(UUID userId) {
        var account = accountPort.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));

        // Saldo atual = saldo inicial da Mock + histórico aprovado do MS2 (a Mock é somente leitura).
        var currentBalance = account.balance().add(transactionRepository.sumApprovedSignedAmount(userId));

        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        var spentToday = transactionRepository.sumApprovedOutflowsOfDay(userId,
                today.atStartOfDay(zoneId).toInstant(),
                today.plusDays(1).atStartOfDay(zoneId).toInstant());
        var availableToday = account.dailyLimit().subtract(spentToday);

        return new Balance(currentBalance, account.dailyLimit(), account.transactionLimit(), spentToday, availableToday);
    }
}
