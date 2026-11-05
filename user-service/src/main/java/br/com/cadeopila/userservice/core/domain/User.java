package br.com.cadeopila.userservice.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final String name;
    private final String email;
    private final String cpf;
    private final String passwordHash;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public User(UUID id, String name, String email, String cpf,
                String passwordHash, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.cpf = cpf;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCpf() { return cpf; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String maskedCpf() {
        if (cpf == null || cpf.length() < 11) return "***.***.***-**";
        return "***.***.***-" + cpf.substring(9);
    }
}
