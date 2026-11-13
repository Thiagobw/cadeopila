package br.com.cadeopila.userservice.adapters.in.web.dto;

import java.util.UUID;

/** Identidade do usuário autenticado devolvida ao MS2 — apenas o id, sem dados sensíveis. */
public record AuthenticatedUserResponse(UUID userId) {}
