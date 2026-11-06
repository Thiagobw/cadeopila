package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.adapters.out.persistence.mapper.TransactionPersistenceMapper;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionPersistenceAdapter.class, TransactionPersistenceMapper.class})
class TransactionPersistenceAdapterTest {

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID DESTINATION = UUID.randomUUID();

    private static final Instant DAY_START = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-11-07T00:00:00Z");
    private static final Instant IN_WINDOW = Instant.parse("2026-11-06T13:00:00Z");
    private static final Instant BEFORE_WINDOW = Instant.parse("2026-11-05T13:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T13:00:01Z");

    @Autowired
    private TransactionRepositoryPort adapter;

    @Autowired
    private TransactionJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("save deve gerar id e versão e persistir a transação com suas cotações (ida e volta)")
    void saveDevePersistirComCotacoes() {
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), NOW);
        var data = new NewTransaction(USER_A, TransactionType.PURCHASE, new BigDecimal("150.00"),
                "Mercado", TransactionCategory.FOOD, null);
        var toSave = Transaction.approved(data, IN_WINDOW, List.of(quote), NOW);

        var saved = adapter.save(toSave);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(saved.getExchangeQuotes()).hasSize(1);

        entityManager.flush();
        entityManager.clear();

        var reloaded = jpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(USER_A);
        assertThat(reloaded.getType()).isEqualTo(TransactionType.PURCHASE);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("150.00");
        assertThat(reloaded.getCategory()).isEqualTo(TransactionCategory.FOOD);
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(reloaded.getRejectionReason()).isNull();
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getExchangeQuotes()).hasSize(1);
        assertThat(reloaded.getExchangeQuotes().getFirst().getCurrency()).isEqualTo("USD");
        assertThat(reloaded.getExchangeQuotes().getFirst().getConvertedAmount()).isEqualByComparingTo("27.61");
    }

    @Test
    @DisplayName("sumApprovedOutflowsOfDay soma apenas saídas APROVADAS, ativas, do usuário e no período")
    void sumApprovedOutflowsOfDayDeveSomarApenasSaidasRelevantes() {
        // Contam: saque e compra aprovados, do usuário A, no período
        adapter.save(approvedWithdrawal(USER_A, "100.00", IN_WINDOW));
        adapter.save(approvedPurchase(USER_A, "50.00", IN_WINDOW));
        // Não contam:
        adapter.save(approvedDeposit(USER_A, "999.00", IN_WINDOW));              // depósito não é saída
        adapter.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));            // rejeitada
        adapter.save(approvedWithdrawal(USER_A, "40.00", BEFORE_WINDOW));        // fora do período
        adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));       // excluída
        adapter.save(approvedWithdrawal(USER_B, "70.00", IN_WINDOW));            // outro usuário

        var total = adapter.sumApprovedOutflowsOfDay(USER_A, DAY_START, DAY_END);

        assertThat(total).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("sumApprovedOutflowsOfDay retorna zero (não nulo) quando não há saídas")
    void sumApprovedOutflowsOfDayDeveRetornarZeroSemSaidas() {
        var total = adapter.sumApprovedOutflowsOfDay(UUID.randomUUID(), DAY_START, DAY_END);

        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sumApprovedSignedAmount soma depósitos e subtrai saídas aprovadas, ignorando rejeitadas e excluídas")
    void sumApprovedSignedAmountDeveSomarComSinal() {
        adapter.save(approvedDeposit(USER_A, "1000.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "300.00", IN_WINDOW));
        adapter.save(approvedPurchase(USER_A, "200.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "50.00", IN_WINDOW));        // ignora (rejeitada)
        adapter.save(deletedApprovedTransfer(USER_A, "100.00", IN_WINDOW));  // ignora (excluída)
        adapter.save(approvedDeposit(USER_B, "500.00", IN_WINDOW));          // outro usuário

        assertThat(adapter.sumApprovedSignedAmount(USER_A)).isEqualByComparingTo("500.00");
        assertThat(adapter.sumApprovedSignedAmount(UUID.randomUUID())).isEqualByComparingTo("0");
    }

    private Transaction approvedWithdrawal(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
    }

    private Transaction approvedPurchase(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.PURCHASE, new BigDecimal(amount), "Compra",
                TransactionCategory.FOOD, null);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
    }

    private Transaction approvedDeposit(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.DEPOSIT, new BigDecimal(amount), "Depósito", null, null);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
    }

    private Transaction rejectedWithdrawal(UUID user, String amount, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.WITHDRAWAL, new BigDecimal(amount), "Saque", null, null);
        return Transaction.rejected(data, br.com.cadeopila.transactionservice.core.domain.RejectionReason.INSUFFICIENT_BALANCE,
                occurredAt, List.of(), NOW);
    }

    private Transaction deletedApprovedTransfer(UUID user, String amount, Instant occurredAt) {
        return Transaction.rehydrate(null, user, DESTINATION, TransactionType.TRANSFER, new BigDecimal(amount),
                "Transferência", TransactionCategory.OTHER, TransactionStatus.APPROVED, null, occurredAt,
                List.of(), NOW, NOW, NOW, 0L);
    }
}
