package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.CategoryLevel1;
import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.RejectionReason;
import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.domain.TransactionCategory;
import br.com.cadeopila.transactionservice.core.domain.TransactionStatus;
import br.com.cadeopila.transactionservice.core.domain.TransactionType;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionReportUseCaseImplTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T13:00:00Z");

    @Mock
    private TransactionRepositoryPort repository;

    private GetTransactionReportUseCaseImpl useCase() {
        return new GetTransactionReportUseCaseImpl(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), ZONE);
    }

    private static Transaction approvedPurchase(Instant occurredAt, List<ExchangeQuote> quotes) {
        return Transaction.rehydrate(UUID.randomUUID(), USER, null, TransactionType.PURCHASE,
                new BigDecimal("150.00"), "Mercado", TransactionCategory.FOOD,
                CategoryLevel1.ESTILO_DE_VIDA, "Delivery", false, false,
                TransactionStatus.APPROVED, null, occurredAt, quotes, NOW, NOW, null, 0L);
    }

    private static Transaction rejectedWithdrawal(Instant occurredAt) {
        return Transaction.rehydrate(UUID.randomUUID(), USER, null, TransactionType.WITHDRAWAL,
                new BigDecimal("40.00"), "Saque", TransactionCategory.OTHER,
                CategoryLevel1.DIVIDAS_IMPREVISTOS, null, false, false,
                TransactionStatus.REJECTED, RejectionReason.INSUFFICIENT_BALANCE, occurredAt,
                List.of(), NOW, NOW, null, 0L);
    }

    @Test
    @DisplayName("Mapeia Transaction → ReportRow: data no fuso, valores convertidos do snapshot e colunas tipadas")
    void mapeiaTransacaoEmReportRow() {
        var quotes = List.of(
                new ExchangeQuote("USD", new BigDecimal("5.00"), new BigDecimal("30.00"), "BrasilAPI", NOW),
                new ExchangeQuote("EUR", new BigDecimal("6.00"), new BigDecimal("25.00"), "BrasilAPI", NOW));
        // 2026-11-06T02:00Z = 2026-11-05 23:00 em São Paulo → data 05/11.
        var occurredAt = Instant.parse("2026-11-06T02:00:00Z");
        when(repository.findActiveForReport(eq(USER), any(), any()))
                .thenReturn(List.of(approvedPurchase(occurredAt, quotes)));

        List<ReportRow> rows = useCase().generate(USER, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(rows).hasSize(1);
        ReportRow row = rows.getFirst();
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(row.type()).isEqualTo(TransactionType.PURCHASE);
        assertThat(row.categoryLevel1()).isEqualTo(CategoryLevel1.ESTILO_DE_VIDA);
        assertThat(row.categoryLevel2()).isEqualTo("Delivery");
        assertThat(row.description()).isEqualTo("Mercado");
        assertThat(row.amountBrl()).isEqualByComparingTo("150.00");
        assertThat(row.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(row.rejectionReason()).isNull();
        assertThat(row.amountUsd()).isEqualByComparingTo("30.00");
        assertThat(row.amountEur()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("REJECTED carrega o motivo; sem cotação, os valores convertidos ficam nulos")
    void rejeitadaSemCambio() {
        when(repository.findActiveForReport(eq(USER), any(), any()))
                .thenReturn(List.of(rejectedWithdrawal(NOW)));

        ReportRow row = useCase().generate(USER, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).getFirst();

        assertThat(row.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(row.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_BALANCE);
        assertThat(row.amountUsd()).isNull();
        assertThat(row.amountEur()).isNull();
    }

    @Test
    @DisplayName("Sem período, usa o mês atual no fuso (01→fim do mês)")
    void semPeriodoUsaMesAtual() {
        when(repository.findActiveForReport(eq(USER), any(), any())).thenReturn(List.of());

        useCase().generate(USER, null, null);

        var fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        var toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findActiveForReport(eq(USER), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 1)); // FIXED_NOW = 2026-11-15
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("from depois de to é período inválido → 400 (InvalidTransactionFilterException)")
    void periodoInvertidoDeveFalhar() {
        assertThatThrownBy(() ->
                useCase().generate(USER, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(InvalidTransactionFilterException.class)
                .hasMessage("from não pode ser depois de to");
    }
}
