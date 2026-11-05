# Cadê o pila?
Cadê o Pila? is a RESTful API designed to manage personal finances for individual and multiple users.

Key Features:

User & Transaction Management: Register users, record income, and track expenses.

Currency Conversion: Support for multi-currency transaction handling.

Balance & Analytics: Real-time account balance updates and detailed expense analysis.

Tech Stack:

Backend: Java / Spring Boot

Containerization: Docker

> Note: "Cadê o Pila?" translates to "Where's the cash?" in Southern Brazilian Portuguese.

## Testes

Os testes do `user-service` são unitários e de fatia web (`@WebMvcTest`), sem dependência de banco de dados — os casos de uso usam Mockito e o adapter de persistência tem o repositório Spring Data mockado. **Nenhum banco precisa estar no ar para rodá-los.**

Rodar todos os módulos a partir da raiz:

```bash
./mvnw -B -ntp clean verify
```

Rodar apenas o `user-service`:

```bash
./mvnw -B -ntp -pl user-service test
```

### Cobertura (JaCoCo)

O relatório é gerado na fase `verify` e fica em:

```
user-service/target/site/jacoco/index.html
```
