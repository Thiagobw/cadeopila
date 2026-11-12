package br.com.cadeopila.userservice.core.domain;

import java.util.List;

/**
 * Resumo do import em massa (GAP #1): quantos foram processados, quantos entraram e os erros por linha.
 * Domínio puro (JDK only) — não conhece HTTP nem CSV.
 */
public record ImportResult(int totalProcessed, int totalSuccess, int totalFailed, List<LineError> errors) {

    public ImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Conveniência: deriva {@code totalFailed} da lista de erros. */
    public static ImportResult of(int totalProcessed, int totalSuccess, List<LineError> errors) {
        var safe = errors == null ? List.<LineError>of() : errors;
        return new ImportResult(totalProcessed, totalSuccess, safe.size(), safe);
    }

    /** Erro de uma linha do CSV: o número da linha no arquivo e o motivo legível. */
    public record LineError(int line, String reason) {}
}
