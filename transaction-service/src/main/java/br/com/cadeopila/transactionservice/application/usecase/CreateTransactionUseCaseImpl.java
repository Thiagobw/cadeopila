package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionPolicy;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.ExchangeRatePort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Processa a criação de uma transação. <b>Sem {@code @Transactional}</b>: a consulta HTTP à API de
 * contas não pode rodar com a conexão de banco aberta. A API de contas é <b>somente leitura</b>
 * (saldo inicial + limites), consultada a cada transação; o saldo atual é derivado somando o
 * histórico aprovado do MS2 (depósitos somam, saídas subtraem) — o saldo nunca é persistido nem
 * gravado de volta na Mock, evitando duas fontes do mesmo dado.
 *
 * <p>Fluxo: valida invariantes (antes de qualquer I/O) → cota câmbio (snapshot) → busca a conta →
 * deriva o saldo atual → decide via {@link TransactionPolicy} → persiste. Rejeição de negócio é
 * resultado válido: grava REJECTED e responde 201. A Mock fora do ar vira REJECTED com motivo
 * (sem travar a requisição, graças ao timeout do cliente).</p>
 */
@Service
public class CreateTransactionUseCaseImpl implements CreateTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateTransactionUseCaseImpl.class);

    private final AccountPort accountPort;
    private final ExchangeRatePort exchangeRatePort;
    private final TransactionRepositoryPort transactionRepository;
    private final Clock clock;
    private final ZoneId zoneId;
    private final TransactionPolicy policy = new TransactionPolicy();

    public CreateTransactionUseCaseImpl(AccountPort accountPort,
                                        ExchangeRatePort exchangeRatePort,
                                        TransactionRepositoryPort transactionRepository,
                                        Clock clock,
                                        ZoneId zoneId) {
        this.accountPort = accountPort;
        this.exchangeRatePort = exchangeRatePort;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Override
    public Result create(Command command) {
        // 1) Invariantes de domínio antes de qualquer chamada externa.
        var data = new NewTransaction(command.userId(), command.type(), command.amount(),
                command.description(), command.category(), command.destinationUserId());

        var now = clock.instant();
        var quoteDate = now.atZone(zoneId).toLocalDate();

        // 2) Câmbio é snapshot e aparece em toda transação gravada (aprovada ou rejeitada).
        var quotes = toQuotes(data.amount(), quoteDate);

        // 3) Conta do dono (somente leitura: saldo inicial + limites).
        AccountSnapshot account;
        try {
            var found = accountPort.findByUserId(data.userId());
            if (found.isEmpty()) {
                return persistRejected(data, RejectionReason.ACCOUNT_NOT_FOUND, now, quotes, null);
            }
            account = found.get();
        } catch (AccountUnavailableException ex) {
            log.warn("API de contas indisponível na consulta; transação REJECTED (userId={})", data.userId());
            return persistRejected(data, RejectionReason.ACCOUNT_SERVICE_UNAVAILABLE, now, quotes, null);
        }

        // 4) Saldo atual = saldo inicial da Mock + histórico aprovado no MS2.
        var currentBalance = account.balance().add(transactionRepository.sumApprovedSignedAmount(data.userId()));

        // 5) Decisão de negócio (depósito não passa por limites).
        var outflowsToday = data.type().isOutflow()
                ? transactionRepository.sumApprovedOutflowsOfDay(data.userId(), dayStart(quoteDate), dayEnd(quoteDate))
                : BigDecimal.ZERO;
        var rejection = policy.evaluate(data.type(), data.amount(), currentBalance,
                account.transactionLimit(), account.dailyLimit(), outflowsToday);
        if (rejection.isPresent()) {
            return persistRejected(data, rejection.get(), now, quotes, currentBalance);
        }

        // 6) Aprovada: persiste. O saldo exibido é derivado (saldo atual ± valor); não grava na Mock.
        var balanceAfter = data.type().isOutflow()
                ? currentBalance.subtract(data.amount())
                : currentBalance.add(data.amount());
        var approved = Transaction.approved(data, now, quotes, now);
        var saved = transactionRepository.save(approved);
        logCreated(saved);
        return new Result(saved, balanceAfter);
    }

    private List<ExchangeQuote> toQuotes(BigDecimal amount, LocalDate date) {
        return exchangeRatePort.quoteAll(date).stream()
                .map(rate -> rate.convert(amount))
                .toList();
    }

    private Result persistRejected(NewTransaction data, RejectionReason reason, Instant now,
                                   List<ExchangeQuote> quotes, BigDecimal balanceAfter) {
        var rejected = Transaction.rejected(data, reason, now, quotes, now);
        var saved = transactionRepository.save(rejected);
        logCreated(saved);
        return new Result(saved, balanceAfter);
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Instant dayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private void logCreated(Transaction transaction) {
        log.info("Transação criada id={} tipo={} status={}",
                transaction.getId(), transaction.getType(), transaction.getStatus());
    }
}
