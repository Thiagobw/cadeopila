package br.com.cadeopila.transactionservice.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha do relatório de transações (Pedido 7) — uma visão achatada e tipada de uma {@link Transaction}
 * para exportação. É domínio puro: só tipos do JDK ({@link LocalDate}, {@link BigDecimal}, enums). <b>Não</b>
 * sabe o que é CSV, Excel, BOM, separador ou vírgula decimal — a serialização para bytes de arquivo mora
 * exclusivamente nos <i>writers</i> da camada {@code adapters/in/web}.
 *
 * <p>A seleção das colunas e dos valores derivados (data no fuso da aplicação, valores convertidos do
 * <i>snapshot</i> de câmbio) é regra de negócio e acontece no caso de uso, não nos controllers.</p>
 *
 * @param date           data da ocorrência já no fuso {@code app.time-zone} (America/Sao_Paulo)
 * @param type           tipo da transação (depósito, saque, transferência, compra)
 * @param categoryLevel1 grau de necessidade (nível 1 da hierarquia de categorias)
 * @param categoryLevel2 subcategoria livre (nível 2); pode ser {@code null}
 * @param description    descrição informada pelo usuário
 * @param amountBrl      valor original em BRL
 * @param status         situação final (APPROVED ou REJECTED)
 * @param rejectionReason motivo da rejeição; {@code null} quando APPROVED
 * @param amountUsd      valor convertido em USD no momento da transação ({@code null} se não houve cotação)
 * @param amountEur      valor convertido em EUR no momento da transação ({@code null} se não houve cotação)
 */
public record ReportRow(
        LocalDate date,
        TransactionType type,
        CategoryLevel1 categoryLevel1,
        String categoryLevel2,
        String description,
        BigDecimal amountBrl,
        TransactionStatus status,
        RejectionReason rejectionReason,
        BigDecimal amountUsd,
        BigDecimal amountEur
) {}
