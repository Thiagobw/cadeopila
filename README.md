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
- `.env` na raiz (copie de `.env.example`) com as senhas e, para o MS2, o `ACCOUNT_API_BASE_URL`.

### Subir com Docker Compose (do zero)
```bash
cp .env.example .env            # preencha as senhas e o ACCOUNT_API_BASE_URL (MS2)
docker compose up --build -d    # sobe PostgreSQL + MS1 + MS2 em segundo plano
```
Um único comando sobe os 3 containers. Acompanhe os logs com `docker compose logs -f` e derrube com
`docker compose down`. Após o start, o **Swagger UI** de cada serviço:

| Serviço | Swagger UI | OpenAPI JSON |
|---|---|---|
| **MS1 — user-service** | `http://localhost:8081/swagger-ui.html` (ou `/swagger-ui/index.html`) | `http://localhost:8081/v3/api-docs` |
| **MS2 — transaction-service** | `http://localhost:8082/swagger-ui.html` (ou `/swagger-ui/index.html`) | `http://localhost:8082/v3/api-docs` |

### Base de demonstração (seed)
Para apresentar com histórico já pronto, popule **3 usuários demo** (senha `Senha@Teste123`) com ~51 transações
cada (aprovadas e rejeitadas, espalhadas por 2026, com transferências entre eles). Com os serviços `UP`:
```powershell
pwsh ./infra/seed/Seed-Demo.ps1
```
Passo a passo, pré-requisitos e **guia de preenchimento dos campos do Swagger** (rotas de **relatório** e
**análise de despesas**) em
[`transaction-service/docs/DEMO-SEED-E-SWAGGER.md`](transaction-service/docs/DEMO-SEED-E-SWAGGER.md).

### Build e testes
Os testes do `user-service` são unitários e de fatia web (`@WebMvcTest`), **sem dependência de banco de
dados** — os casos de uso usam Mockito e o adapter de persistência tem o repositório Spring Data mockado.
**Nenhum banco precisa estar no ar para rodá-los.**

Todos os módulos, a partir da raiz:
```bash
./mvnw -B -ntp clean verify
```
Só os testes unitários (Maven wrapper), sem subir banco nem empacotar:
```bash
./mvnw -B -ntp clean test                  # todos os módulos
./mvnw -B -ntp -pl user-service clean test # só o MS1 (user-service)
```

### Cobertura (JaCoCo)
`./mvnw clean test` já grava os dados de cobertura (`jacoco.exec`) ao rodar os testes; o **relatório HTML**
é emitido na fase `verify`:
```bash
./mvnw -B -ntp clean verify
```
Abra o relatório de cada módulo em:
- `user-service/target/site/jacoco/index.html`
- `transaction-service/target/site/jacoco/index.html`
