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

## Como rodar

Sobe o ambiente inteiro — PostgreSQL 17 + `user-service` (MS1, porta **8081**) + `transaction-service`
(MS2, porta **8082**) — com um comando, via Docker Compose.

### Pré-requisitos
- Docker e Docker Compose.
- Para build/testes locais sem Docker: JDK 25.
- `.env` na raiz (copie de `.env.example`) com as senhas e, para o MS2, as URLs de integração
  (`ACCOUNT_API_BASE_URL`, `EXCHANGE_API_BASE_URL`).

### Subir com Docker Compose (do zero)
```bash
cp .env.example .env      # preencha as senhas e o ACCOUNT_API_BASE_URL (MS2)
docker compose up --build
```
Sobe os 3 containers. Swagger: MS1 em `http://localhost:8081/swagger-ui.html`,
MS2 em `http://localhost:8082/swagger-ui.html`.

### Configurar a API Mock (MS2 — saldo e limites)
`ACCOUNT_API_BASE_URL` aponta para um recurso `accounts` no [mockapi.io](https://mockapi.io) (URL base, **sem**
`/accounts`), com um registro por usuário: `userId`, `balance`, `dailyLimit`, `transactionLimit`. Detalhes no
[README do transaction-service](transaction-service/README.md).

### Build e testes
Todos os módulos, a partir da raiz:
```bash
./mvnw -B -ntp clean verify
```
Por serviço:
```bash
./mvnw -B -ntp -pl user-service verify
./mvnw -B -ntp -pl transaction-service verify
```
Os testes do `user-service` são unitários e de fatia web (`@WebMvcTest`), **sem banco no ar**. Os do
`transaction-service` incluem fatia (`@WebMvcTest`/`@DataJpaTest` com H2 em modo PostgreSQL), integração
(`@SpringBootTest` com as portas externas mockadas) e arquitetura (ArchUnit).

### Cobertura (JaCoCo)
Gerada na fase `verify`:
- `user-service/target/site/jacoco/index.html`
- `transaction-service/target/site/jacoco/index.html`
