package br.com.cadeopila.userservice.core.domain;

import java.util.Locale;
import java.util.Objects;

public record Email(String value) {

    public Email {
        Objects.requireNonNull(value, "Email não pode ser nulo");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) throw new IllegalArgumentException("Email não pode ser vazio");
    }

    /** Normaliza sem lançar exceção para null/vazio — para uso nos adapters de entrada antes da validação de Bean. */
    public static String normalize(String raw) {
        return raw != null ? raw.trim().toLowerCase(Locale.ROOT) : null;
    }
}
