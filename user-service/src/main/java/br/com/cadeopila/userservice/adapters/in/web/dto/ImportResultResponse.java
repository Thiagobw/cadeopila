package br.com.cadeopila.userservice.adapters.in.web.dto;

import br.com.cadeopila.userservice.core.domain.ImportResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Corpo da resposta do import em massa. Mapeado a partir do {@link ImportResult} de domínio. */
@Schema(description = "Resumo da importação em massa. Sempre 200: linhas rejeitadas são reportadas, não erro da chamada.")
public record ImportResultResponse(

        @Schema(description = "Total de linhas processadas", example = "3") int totalProcessed,
        @Schema(description = "Total criado com sucesso", example = "2") int totalSuccess,
        @Schema(description = "Total de linhas rejeitadas", example = "1") int totalFailed,

        @Schema(description = "Usuários criados (linha de origem, id gerado e e-mail)")
        List<CreatedResponse> created,

        @Schema(description = "Erros por linha (linha de origem e motivo)")
        List<LineErrorResponse> errors
) {
    /** Usuário criado: linha de origem, id gerado e e-mail — o suficiente para semear a MockAPI. */
    @Schema(description = "Usuário criado na importação")
    public record CreatedResponse(
            @Schema(description = "Linha do CSV (1-based, sem contar o cabeçalho)", example = "1") int line,
            @Schema(description = "ID (UUID) gerado", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479") UUID id,
            @Schema(example = "ana.souza@email.com") String email) {}

    @Schema(description = "Linha rejeitada na importação")
    public record LineErrorResponse(
            @Schema(description = "Linha do CSV (1-based)", example = "3") int line,
            @Schema(description = "Motivo da rejeição", example = "Email deve ser válido") String reason) {}

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
