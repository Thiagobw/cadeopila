package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.AccountSnapshot;
import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.ports.in.CreateTransactionUseCase.Command;
import br.com.cadeopila.transactionservice.core.ports.out.AccountPort;
import br.com.cadeopila.transactionservice.core.ports.out.ExchangeRatePort;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTransactionUseCaseImplTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-06T15:00:00Z");
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 11, 6);

    @Mock
    private AccountPort accountPort;
    @Mock
    private ExchangeRatePort exchangeRatePort;
    @Mock
    private TransactionRepositoryPort transactionRepository;

    private CreateTransactionUseCaseImpl useCase(Clock clock) {
        return new CreateTransactionUseCaseImpl(accountPort, exchangeRatePort, transactionRepository, clock, ZONE);
    }

    private CreateTransactionUseCaseImpl useCase() {
        return useCase(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private AccountSnapshot account(String balance, String dailyLimit, String transactionLimit) {
        return new AccountSnapshot(USER, new BigDecimal(balance), new BigDecimal(dailyLimit), new BigDecimal(transactionLimit));
    }

    private ExchangeRate usdRate() {
        return new ExchangeRate("USD", new BigDecimal("5.00"), FIXED_NOW);
    }

    private Transaction captureSaved() {
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Deve aprovar depósito e somar ao saldo derivado, gravando a transação com câmbio")
    void deveAprovarDeposito() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.DEPOSIT, new BigDecimal("500.00"),
                "Depósito", null, null));

        verify(transactionRepository, never()).sumApprovedOutflowsOfDay(any(), any(), any());
        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.balanceAfter()).isEqualByComparingTo("1500.00");
        assertThat(result.transaction().getExchangeQuotes()).hasSize(1);
        assertThat(result.transaction().getExchangeQuotes().getFirst().convertedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Deve aprovar saque dentro dos limites e devolver o saldo derivado menos o valor")
    void deveAprovarSaque() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumApprovedOutflowsOfDay(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.WITHDRAWAL, new BigDecimal("200.00"),
                "Saque", null, null));

        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.balanceAfter()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("Deve derivar o saldo atual do saldo inicial da Mock mais o histórico aprovado")
    void deveDerivarSaldoDoHistorico() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        // histórico aprovado soma +200 → saldo atual 1200
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(new BigDecimal("200.00"));
        when(transactionRepository.sumApprovedOutflowsOfDay(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.WITHDRAWAL, new BigDecimal("300.00"),
                "Saque", null, null));

        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.balanceAfter()).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("Deve rejeitar por saldo insuficiente (derivado) e gravar a transação rejeitada")
    void deveRejeitarSaldoInsuficienteEGravar() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("50.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumApprovedOutflowsOfDay(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.WITHDRAWAL, new BigDecimal("100.00"),
                "Saque", null, null));

        var saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_BALANCE);
        assertThat(result.balanceAfter()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Deve rejeitar com ACCOUNT_NOT_FOUND quando a conta não existe")
    void deveRejeitarContaNaoEncontrada() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.PURCHASE, new BigDecimal("10.00"),
                "Compra", TransactionCategory.FOOD, null));

        var saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo(RejectionReason.ACCOUNT_NOT_FOUND);
        assertThat(saved.getExchangeQuotes()).hasSize(1);
        assertThat(result.balanceAfter()).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar com ACCOUNT_SERVICE_UNAVAILABLE quando a consulta de conta falha")
    void deveRejeitarContaIndisponivel() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenThrow(new AccountUnavailableException("falha"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.DEPOSIT, new BigDecimal("500.00"),
                "Depósito", null, null));

        var saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo(RejectionReason.ACCOUNT_SERVICE_UNAVAILABLE);
        assertThat(result.balanceAfter()).isNull();
    }

    @Test
    @DisplayName("Deve propagar (500) quando a persistência falha — sem gravar saldo na Mock (read-only)")
    void devePropagarQuandoPersistenciaFalha() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenThrow(new RuntimeException("falha ao persistir"));

        assertThatThrownBy(() -> useCase().create(new Command(USER, TransactionType.DEPOSIT, new BigDecimal("500.00"),
                "Depósito", null, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Deve validar invariantes antes de qualquer I/O (transferência sem destino → 400, sem tocar portas)")
    void deveValidarAntesDeQualquerIO() {
        assertThatThrownBy(() -> useCase().create(new Command(USER, TransactionType.TRANSFER, new BigDecimal("10.00"),
                "PIX", null, null)))
                .isInstanceOf(InvalidTransactionException.class);

        verifyNoInteractions(accountPort, exchangeRatePort, transactionRepository);
    }

    @Test
    @DisplayName("Deve cotar o câmbio na data em America/Sao_Paulo, não em UTC")
    void deveUsarDataDeSaoPauloNoCambio() {
        // 2026-11-07T01:00Z = 2026-11-06 22:00 em São Paulo → data de cotação 2026-11-06
        var clock = Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC);
        when(exchangeRatePort.quoteAll(LocalDate.of(2026, 11, 6))).thenReturn(List.of(usdRate()));
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase(clock).create(new Command(USER, TransactionType.DEPOSIT, new BigDecimal("500.00"),
                "Depósito", null, null));

        verify(exchangeRatePort).quoteAll(LocalDate.of(2026, 11, 6));
    }

    @Test
    @DisplayName("Câmbio indisponível não bloqueia: aprova e grava sem cotações")
    void cambioIndisponivelNaoBloqueia() {
        when(exchangeRatePort.quoteAll(QUOTE_DATE)).thenReturn(List.of());
        when(accountPort.findByUserId(USER)).thenReturn(Optional.of(account("1000.00", "10000.00", "5000.00")));
        when(transactionRepository.sumApprovedSignedAmount(USER)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase().create(new Command(USER, TransactionType.DEPOSIT, new BigDecimal("500.00"),
                "Depósito", null, null));

        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.transaction().getExchangeQuotes()).isEmpty();
    }
}
