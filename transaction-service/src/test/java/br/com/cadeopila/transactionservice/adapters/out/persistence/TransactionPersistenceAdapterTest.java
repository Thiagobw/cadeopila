package br.com.cadeopila.transactionservice.adapters.out.persistence;

import br.com.cadeopila.transactionservice.adapters.out.persistence.mapper.TransactionPersistenceMapper;
import br.com.cadeopila.transactionservice.core.domain.AnalyticsRow;
import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.NewTransaction;
import br.com.cadeopila.transactionservice.core.domain.PageQuery;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionQuery;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionPersistenceAdapter.class, TransactionPersistenceMapper.class,
        TransactionPersistenceAdapterTest.TestConfig.class})
class TransactionPersistenceAdapterTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ZoneId zoneId() {
            return ZoneId.of("America/Sao_Paulo");
        }
    }

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID DESTINATION = UUID.randomUUID();

    private static final Instant DAY_START = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-11-07T00:00:00Z");
    private static final Instant IN_WINDOW = Instant.parse("2026-11-06T13:00:00Z");
    private static final Instant BEFORE_WINDOW = Instant.parse("2026-11-05T13:00:00Z");
    private static final Instant AFTER_WINDOW = Instant.parse("2026-11-07T13:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T13:00:01Z");

    @Autowired
    private TransactionRepositoryPort adapter;

    @Autowired
    private TransactionJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionQuery query(TransactionType type, TransactionStatus status,
                                   java.time.LocalDate from, java.time.LocalDate to, int size) {
        return new TransactionQuery(USER_A, type, status, from, to, new PageQuery(0, size));
    }

    @Test
    @DisplayName("save deve gerar id e versão e persistir a transação com suas cotações (ida e volta)")
    void saveDevePersistirComCotacoes() {
        var quote = new ExchangeQuote("USD", new BigDecimal("5.4321"), new BigDecimal("27.61"), "BrasilAPI", NOW);
        var data = new NewTransaction(USER_A, TransactionType.PURCHASE, new BigDecimal("150.00"),
                "Mercado", TransactionCategory.FOOD, null);
        var toSave = Transaction.approved(data, IN_WINDOW, List.of(quote), NOW);

        var saved = adapter.save(toSave);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        entityManager.flush();
        entityManager.clear();

        var reloaded = jpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("150.00");
        assertThat(reloaded.getExchangeQuotes()).hasSize(1);
        assertThat(reloaded.getExchangeQuotes().getFirst().getCurrency()).isEqualTo("USD");
        assertThat(reloaded.getExchangeQuotes().getFirst().getSource()).isEqualTo("BrasilAPI");
    }

    @Test
    @DisplayName("findActiveById deve retornar a transação ativa")
    void findActiveByIdDeveRetornarAtiva() {
        var saved = adapter.save(approvedDeposit(USER_A, "100.00", IN_WINDOW));

        assertThat(adapter.findActiveById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("findActiveById deve ignorar transação excluída")
    void findActiveByIdDeveIgnorarExcluida() {
        var saved = adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));

        assertThat(adapter.findActiveById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("findActiveById deve retornar vazio quando não existe")
    void findActiveByIdVazioQuandoNaoExiste() {
        assertThat(adapter.findActiveById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("search deve trazer só as transações ativas do usuário, excluindo apagadas e de outros")
    void searchDeveFiltrarPorUsuarioEExcluirApagadas() {
        adapter.save(approvedDeposit(USER_A, "100.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "40.00", IN_WINDOW));
        adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));
        adapter.save(approvedDeposit(USER_B, "500.00", IN_WINDOW));

        var result = adapter.search(query(null, null, null, null, 20));

        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search deve filtrar por tipo e por status")
    void searchDeveFiltrarPorTipoEStatus() {
        adapter.save(approvedDeposit(USER_A, "100.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "40.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));

        assertThat(adapter.search(query(TransactionType.DEPOSIT, null, null, null, 20)).totalElements()).isEqualTo(1);
        assertThat(adapter.search(query(null, TransactionStatus.REJECTED, null, null, 20)).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("search deve filtrar por período (datas inclusivas no fuso configurado)")
    void searchDeveFiltrarPorPeriodo() {
        adapter.save(approvedDeposit(USER_A, "10.00", BEFORE_WINDOW));
        adapter.save(approvedDeposit(USER_A, "20.00", IN_WINDOW));
        adapter.save(approvedDeposit(USER_A, "30.00", AFTER_WINDOW));

        var day = java.time.LocalDate.of(2026, 11, 6);
        var result = adapter.search(query(null, null, day, day, 20));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().getOccurredAt()).isEqualTo(IN_WINDOW);
    }

    @Test
    @DisplayName("search deve ordenar por occurredAt decrescente")
    void searchDeveOrdenarPorOccurredAtDesc() {
        adapter.save(approvedDeposit(USER_A, "10.00", BEFORE_WINDOW));
        adapter.save(approvedDeposit(USER_A, "20.00", IN_WINDOW));

        var result = adapter.search(query(null, null, null, null, 20));

        assertThat(result.content()).extracting(Transaction::getOccurredAt)
                .containsExactly(IN_WINDOW, BEFORE_WINDOW);
    }

    @Test
    @DisplayName("search deve paginar")
    void searchDevePaginar() {
        adapter.save(approvedDeposit(USER_A, "10.00", BEFORE_WINDOW));
        adapter.save(approvedDeposit(USER_A, "20.00", IN_WINDOW));

        var result = adapter.search(query(null, null, null, null, 1));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("sumApprovedOutflowsOfDay soma apenas saídas APROVADAS, ativas, do usuário e no período")
    void sumApprovedOutflowsOfDayDeveSomarApenasSaidasRelevantes() {
        adapter.save(approvedWithdrawal(USER_A, "100.00", IN_WINDOW));
        adapter.save(approvedPurchase(USER_A, "50.00", IN_WINDOW));
        adapter.save(approvedDeposit(USER_A, "999.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "40.00", BEFORE_WINDOW));
        adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_B, "70.00", IN_WINDOW));

        assertThat(adapter.sumApprovedOutflowsOfDay(USER_A, DAY_START, DAY_END)).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("sumApprovedOutflowsOfDay retorna zero (não nulo) quando não há saídas")
    void sumApprovedOutflowsOfDayDeveRetornarZeroSemSaidas() {
        assertThat(adapter.sumApprovedOutflowsOfDay(UUID.randomUUID(), DAY_START, DAY_END)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sumApprovedSignedAmount soma depósitos e subtrai saídas aprovadas, ignorando rejeitadas e excluídas")
    void sumApprovedSignedAmountDeveSomarComSinal() {
        adapter.save(approvedDeposit(USER_A, "1000.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "300.00", IN_WINDOW));
        adapter.save(approvedPurchase(USER_A, "200.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "50.00", IN_WINDOW));
        adapter.save(deletedApprovedTransfer(USER_A, "100.00", IN_WINDOW));
        adapter.save(approvedDeposit(USER_B, "500.00", IN_WINDOW));

        assertThat(adapter.sumApprovedSignedAmount(USER_A)).isEqualByComparingTo("500.00");
        assertThat(adapter.sumApprovedSignedAmount(UUID.randomUUID())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("findApprovedByPeriod traz depósitos e saídas APROVADAS e ativas do usuário no período")
    void findApprovedByPeriodDeveTrazerSoAprovadasAtivasDoUsuarioNoPeriodo() {
        adapter.save(approvedDeposit(USER_A, "500.00", IN_WINDOW));
        adapter.save(approvedWithdrawal(USER_A, "100.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));        // rejeitada → fora
        adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));   // excluída → fora
        adapter.save(approvedDeposit(USER_A, "999.00", BEFORE_WINDOW));      // fora do período
        adapter.save(approvedWithdrawal(USER_B, "70.00", IN_WINDOW));        // outro usuário

        var rows = adapter.findApprovedByPeriod(USER_A, DAY_START, DAY_END);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AnalyticsRow::type)
                .containsExactlyInAnyOrder(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);
    }

    @Test
    @DisplayName("findApprovedByPeriod devolve a classificação inteligente (nível 1, nível 2, fixa) de cada linha")
    void findApprovedByPeriodDeveDevolverClassificacao() {
        adapter.save(approvedRichPurchase(USER_A, "250.00", CategoryLevel1.ESTILO_DE_VIDA, "Delivery", true, IN_WINDOW));

        var rows = adapter.findApprovedByPeriod(USER_A, DAY_START, DAY_END);

        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.categoryLevel1()).isEqualTo(CategoryLevel1.ESTILO_DE_VIDA);
        assertThat(row.categoryLevel2()).isEqualTo("Delivery");
        assertThat(row.fixed()).isTrue();
        assertThat(row.amount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("findApprovedByPeriod retorna lista vazia (não nula) quando não há transações")
    void findApprovedByPeriodDeveRetornarVazio() {
        assertThat(adapter.findApprovedByPeriod(UUID.randomUUID(), DAY_START, DAY_END)).isEmpty();
    }

    @Test
    @DisplayName("findActiveForReport traz APPROVED e REJECTED do usuário no período, excluindo apagadas, de outros e fora do período")
    void findActiveForReportDeveTrazerAprovadasERejeitadasAtivasDoUsuarioNoPeriodo() {
        adapter.save(approvedDeposit(USER_A, "500.00", IN_WINDOW));
        adapter.save(rejectedWithdrawal(USER_A, "30.00", IN_WINDOW));          // rejeitada → entra (tem status/motivo)
        adapter.save(deletedApprovedTransfer(USER_A, "20.00", IN_WINDOW));     // excluída → fora
        adapter.save(approvedDeposit(USER_A, "999.00", BEFORE_WINDOW));        // fora do período
        adapter.save(approvedWithdrawal(USER_B, "70.00", IN_WINDOW));          // outro usuário

        var day = java.time.LocalDate.of(2026, 11, 6);
        var transactions = adapter.findActiveForReport(USER_A, day, day);

        assertThat(transactions).hasSize(2);
        assertThat(transactions).extracting(Transaction::getStatus)
                .containsExactlyInAnyOrder(TransactionStatus.APPROVED, TransactionStatus.REJECTED);
    }

    @Test
    @DisplayName("findActiveForReport ordena por occurredAt crescente (cronológico)")
    void findActiveForReportDeveOrdenarCronologicamente() {
        var earlier = Instant.parse("2026-11-06T09:00:00Z");
        var later = Instant.parse("2026-11-06T17:00:00Z");
        adapter.save(approvedDeposit(USER_A, "20.00", later));
        adapter.save(approvedDeposit(USER_A, "10.00", earlier));

        var day = java.time.LocalDate.of(2026, 11, 6);
        var transactions = adapter.findActiveForReport(USER_A, day, day);

        assertThat(transactions).extracting(Transaction::getOccurredAt).containsExactly(earlier, later);
    }

    @Test
    @DisplayName("findActiveForReport retorna lista vazia (não nula) quando não há transações")
    void findActiveForReportDeveRetornarVazio() {
        var day = java.time.LocalDate.of(2026, 11, 6);
        assertThat(adapter.findActiveForReport(UUID.randomUUID(), day, day)).isEmpty();
    }

    private Transaction approvedRichPurchase(UUID user, String amount, CategoryLevel1 level1,
                                             String level2, boolean fixed, Instant occurredAt) {
        var data = new NewTransaction(user, TransactionType.PURCHASE, new BigDecimal(amount), "Compra teste",
                TransactionCategory.SHOPPING, null, level1, level2, fixed, false);
        return Transaction.approved(data, occurredAt, List.of(), NOW);
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
        return Transaction.rejected(data, RejectionReason.INSUFFICIENT_BALANCE, occurredAt, List.of(), NOW);
    }

    private Transaction deletedApprovedTransfer(UUID user, String amount, Instant occurredAt) {
        return Transaction.rehydrate(null, user, DESTINATION, TransactionType.TRANSFER, new BigDecimal(amount),
                "Transferência", TransactionCategory.OTHER, TransactionStatus.APPROVED, null, occurredAt,
                List.of(), NOW, NOW, NOW, 0L);
    }
}
