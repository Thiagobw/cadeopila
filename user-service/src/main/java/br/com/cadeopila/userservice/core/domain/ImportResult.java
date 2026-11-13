package br.com.cadeopila.userservice.core.domain;

import java.util.List;
import java.util.UUID;

/**
 * Resumo do import em massa (GAP #1): quantos foram processados, quantos entraram, os usuários
 * efetivamente criados (id + e-mail) e os erros por linha.
 * Domínio puro (JDK only) — não conhece HTTP nem CSV.
 */
public record ImportResult(
        int totalProcessed,
        int totalSuccess,
        int totalFailed,
        List<CreatedUser> createdUsers,
        List<LineError> errors) {

    public ImportResult {
        createdUsers = createdUsers == null ? List.of() : List.copyOf(createdUsers);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Conveniência: deriva {@code totalFailed} da lista de erros. */
    public static ImportResult of(int totalProcessed, int totalSuccess,
                                  List<CreatedUser> createdUsers, List<LineError> errors) {
        var safeErrors = errors == null ? List.<LineError>of() : errors;
        var safeCreated = createdUsers == null ? List.<CreatedUser>of() : createdUsers;
        return new ImportResult(totalProcessed, totalSuccess, safeErrors.size(), safeCreated, safeErrors);
    }

    /** Erro de uma linha do CSV: o número da linha no arquivo e o motivo legível. */
    public record LineError(int line, String reason) {}

    /**
     * Usuário criado com sucesso no lote. Carrega o {@code id} (UUID gerado) e o {@code email} normalizado
     * para que o operador consiga espelhar a conta na MockAPI sem precisar de outra consulta (resolve o
     * "ovo-e-galinha" do setup da demo).
     */
    public record CreatedUser(UUID id, String email) {}
}
