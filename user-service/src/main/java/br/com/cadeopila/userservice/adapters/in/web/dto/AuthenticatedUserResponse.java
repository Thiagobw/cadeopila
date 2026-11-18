package br.com.cadeopila.userservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Identidade do usuário autenticado devolvida ao MS2 — apenas o id, sem dados sensíveis. */
@Schema(description = "Identidade validada: só o id do usuário, sem dados sensíveis.")
public record AuthenticatedUserResponse(

        @Schema(description = "ID (UUID) do usuário autenticado", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        UUID userId
) {}
