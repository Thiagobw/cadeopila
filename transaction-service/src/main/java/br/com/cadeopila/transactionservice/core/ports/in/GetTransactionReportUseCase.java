package br.com.cadeopila.transactionservice.core.ports.in;

import br.com.cadeopila.transactionservice.core.domain.ReportRow;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Relatório de transações de um usuário num período (Pedido 7). Devolve as linhas de domínio já
 * selecionadas e derivadas ({@link ReportRow}); a transformação dessas linhas em CSV/Excel é
 * responsabilidade dos <i>writers</i> da camada web, nunca deste contrato.
 */
public interface GetTransactionReportUseCase {

    /**
     * Linhas do relatório do usuário no período {@code [from, to]} (datas inclusivas, no fuso da
     * aplicação). Inclui transações APPROVED e REJECTED e exclui as que sofreram <i>soft delete</i>;
     * ordena por data de ocorrência. {@code from}/{@code to} nulos usam o mês atual. Nunca retorna
     * {@code null}.
     *
     * @throws br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException
     *         se {@code from} for depois de {@code to}
     */
    List<ReportRow> generate(UUID userId, LocalDate from, LocalDate to);
}
