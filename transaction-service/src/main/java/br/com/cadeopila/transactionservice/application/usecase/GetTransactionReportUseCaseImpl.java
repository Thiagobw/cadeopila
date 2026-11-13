package br.com.cadeopila.transactionservice.application.usecase;

import br.com.cadeopila.transactionservice.core.domain.ExchangeQuote;
import br.com.cadeopila.transactionservice.core.domain.ReportRow;
import br.com.cadeopila.transactionservice.core.domain.Transaction;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
import br.com.cadeopila.transactionservice.core.ports.in.GetTransactionReportUseCase;
import br.com.cadeopila.transactionservice.core.ports.out.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Monta o relatório de transações (Pedido 7). Toda a <b>seleção e derivação</b> das colunas vive aqui:
 * resolve o período (padrão = mês atual, no fuso {@code app.time-zone}, consistente com a análise de
 * despesas), busca as transações ativas (APPROVED e REJECTED, sem as excluídas) e as achata em
 * {@link ReportRow} tipadas — data no fuso, valores convertidos USD/EUR lidos do <i>snapshot</i> de
 * câmbio gravado na criação.
 *
 * <p>Não conhece CSV, Excel, BOM nem separador: devolve linhas de domínio e deixa a formatação para os
 * <i>writers</i> da camada web.</p>
 */
@Service
public class GetTransactionReportUseCaseImpl implements GetTransactionReportUseCase {

    private static final String USD = "USD";
    private static final String EUR = "EUR";

    private final TransactionRepositoryPort repository;
    private final Clock clock;
    private final ZoneId zoneId;

    public GetTransactionReportUseCaseImpl(TransactionRepositoryPort repository, Clock clock, ZoneId zoneId) {
        this.repository = repository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportRow> generate(UUID userId, LocalDate from, LocalDate to) {
        YearMonth currentMonth = YearMonth.from(clock.instant().atZone(zoneId));
        LocalDate periodStart = from != null ? from : currentMonth.atDay(1);
        LocalDate periodEnd = to != null ? to : currentMonth.atEndOfMonth();
        if (periodStart.isAfter(periodEnd)) {
            throw new InvalidTransactionFilterException("from não pode ser depois de to");
        }
        return repository.findActiveForReport(userId, periodStart, periodEnd).stream()
                .map(this::toRow)
                .toList();
    }

    private ReportRow toRow(Transaction tx) {
        return new ReportRow(
                tx.getOccurredAt().atZone(zoneId).toLocalDate(),
                tx.getType(),
                tx.getCategoryLevel1(),
                tx.getCategoryLevel2(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getRejectionReason(),
                convertedAmount(tx, USD),
                convertedAmount(tx, EUR));
    }

    /** Valor já convertido na moeda, lido do snapshot de câmbio da transação; {@code null} se não existir. */
    private static BigDecimal convertedAmount(Transaction tx, String currency) {
        return tx.getExchangeQuotes().stream()
                .filter(quote -> currency.equalsIgnoreCase(quote.currency()))
                .map(ExchangeQuote::convertedAmount)
                .findFirst()
                .orElse(null);
    }
}
