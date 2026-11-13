package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.ImportResult;

import java.util.List;
import java.util.UUID;

/** Corpo da resposta do import em massa. Mapeado a partir do {@link ImportResult} de domínio. */
public record ImportResultResponse(
        int totalProcessed,
        int totalSuccess,
        int totalFailed,
        List<CreatedUserResponse> createdUsers,
        List<LineErrorResponse> errors
) {
    public record LineErrorResponse(int line, String reason) {}

    /**
     * Resumo de um usuário criado no lote: o {@code id} (UUID gerado) e o {@code email}. Permite ao
     * operador copiar os UUIDs direto do retorno do upload e cadastrá-los na MockAPI, sem outra chamada.
     */
    public record CreatedUserResponse(UUID id, String email) {}

    public static ImportResultResponse from(ImportResult result) {
        var createdUsers = result.createdUsers().stream()
                .map(u -> new CreatedUserResponse(u.id(), u.email()))
                .toList();
        var errors = result.errors().stream()
                .map(e -> new LineErrorResponse(e.line(), e.reason()))
                .toList();
        return new ImportResultResponse(
                result.totalProcessed(), result.totalSuccess(), result.totalFailed(), createdUsers, errors);
    }
}
