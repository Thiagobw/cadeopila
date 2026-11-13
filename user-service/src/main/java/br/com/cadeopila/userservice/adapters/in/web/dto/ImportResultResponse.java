package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.ImportResult;

import java.util.List;
import java.util.UUID;

/** Corpo da resposta do import em massa. Mapeado a partir do {@link ImportResult} de domínio. */
public record ImportResultResponse(
        int totalProcessed,
        int totalSuccess,
        int totalFailed,
        List<CreatedResponse> created,
        List<LineErrorResponse> errors
) {
    /** Usuário criado: linha de origem, id gerado e e-mail — o suficiente para semear a MockAPI. */
    public record CreatedResponse(int line, UUID id, String email) {}

    public record LineErrorResponse(int line, String reason) {}

    public static ImportResultResponse from(ImportResult result) {
        var created = result.created().stream()
                .map(c -> new CreatedResponse(c.line(), c.id(), c.email()))
                .toList();
        var errors = result.errors().stream()
                .map(e -> new LineErrorResponse(e.line(), e.reason()))
                .toList();
        return new ImportResultResponse(
                result.totalProcessed(), result.totalSuccess(), result.totalFailed(), created, errors);
    }
}
