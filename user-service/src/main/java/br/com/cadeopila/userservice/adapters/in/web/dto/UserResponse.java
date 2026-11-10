package br.com.cadeopila.userservice.adapters.in.web.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String cpf,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
