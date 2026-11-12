package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.ImportResult;

import java.util.List;

/** Corpo da resposta do import em massa. Mapeado a partir do {@link ImportResult} de domínio. */
public record ImportResultResponse(
        int totalProcessed,
        int totalSuccess,
        int totalFailed,
        List<LineErrorResponse> errors
) {
    public record LineErrorResponse(int line, String reason) {}

    public static ImportResultResponse from(ImportResult result) {
        var errors = result.errors().stream()
                .map(e -> new LineErrorResponse(e.line(), e.reason()))
                .toList();
        return new ImportResultResponse(
                result.totalProcessed(), result.totalSuccess(), result.totalFailed(), errors);
    }
}
