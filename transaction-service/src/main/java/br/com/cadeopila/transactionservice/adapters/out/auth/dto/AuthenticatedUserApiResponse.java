package br.com.cadeopila.transactionservice.adapters.out.auth.dto;

import java.util.UUID;

/** Resposta do user-service (MS1) com o id do usuário autenticado. */
public record AuthenticatedUserApiResponse(UUID userId) {}
