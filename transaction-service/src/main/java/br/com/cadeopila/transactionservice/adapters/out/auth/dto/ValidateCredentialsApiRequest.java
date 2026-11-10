package br.com.cadeopila.transactionservice.adapters.out.auth.dto;

/** Corpo enviado ao user-service (MS1) para validar as credenciais do Basic Auth. */
public record ValidateCredentialsApiRequest(String email, String password) {}
