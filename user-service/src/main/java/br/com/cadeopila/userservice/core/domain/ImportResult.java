package br.com.cadeopila.userservice.core.domain;

import java.util.List;
import java.util.UUID;

/**
 * Resumo do import em massa (GAP #1): quantos foram processados, quantos entraram, <b>os usuários
 * criados</b> (linha + id + e-mail) e os erros por linha. Domínio puro (JDK only) — não conhece HTTP nem CSV.
 *
 * <p>A lista {@code created} fecha o ovo-e-galinha com a MockAPI: o UUID de cada usuário importado volta
 * na própria resposta do import, sem exigir um {@code GET /users} autenticado só para semear as contas.</p>
 */
public record ImportResult(int totalProcessed, int totalSuccess, int totalFailed,
                           List<Created> created, List<LineError> errors) {

    public ImportResult {
        created = created == null ? List.of() : List.copyOf(created);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Conveniência: deriva {@code totalFailed} da lista de erros. */
    public static ImportResult of(int totalProcessed, int totalSuccess,
                                  List<Created> created, List<LineError> errors) {
        var safeErrors = errors == null ? List.<LineError>of() : errors;
        return new ImportResult(totalProcessed, totalSuccess, safeErrors.size(), created, safeErrors);
    }

    /** Usuário criado com sucesso: a linha de origem no arquivo, o id gerado e o e-mail. */
    public record Created(int line, UUID id, String email) {}

    /** Erro de uma linha do CSV: o número da linha no arquivo e o motivo legível. */
    public record LineError(int line, String reason) {}
}
