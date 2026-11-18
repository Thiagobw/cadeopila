package br.com.cadeopila.userservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Representação de um usuário. Nunca inclui a senha/hash.")
public record UserResponse(

        @Schema(description = "ID (UUID) do usuário — é o mesmo userId usado nas transações",
                example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        UUID id,

        @Schema(example = "Ana Souza") String name,
        @Schema(example = "ana.souza@email.com") String email,
        @Schema(example = "11122233344") String cpf,
        @Schema(description = "Data de criação", example = "2026-11-06T18:00:00") LocalDateTime createdAt,
        @Schema(description = "Data da última atualização", example = "2026-11-06T18:00:00") LocalDateTime updatedAt
) {}
