# user-service (MS1)

Serviço de **cadastro e gestão de usuários** (CRUD) em **arquitetura hexagonal**
(Java 25 + Spring Boot 4.1.2 + PostgreSQL 17). A senha é persistida **apenas como hash BCrypt** e o
**CPF nunca é devolvido em texto puro** — respostas e erros expõem só os dois últimos dígitos
(`***.***.***-01`).

## Como rodar

O ambiente completo (PostgreSQL + MS1 + MS2 via Docker Compose, `.env`, build e testes) está no
**[README da raiz](../README.md) → seção "Como rodar"**. Abaixo, só o que é específico do MS1.

- **Subir tudo (a partir da raiz):** `docker compose up --build -d`.
- **Swagger / porta:** `http://localhost:8081/swagger-ui.html` (ou `/swagger-ui/index.html`) ·
  OpenAPI JSON em `http://localhost:8081/v3/api-docs`.
- **Rodar só este módulo (testes unitários + fatia web):**
  ```bash
  ./mvnw -B -ntp -pl user-service clean test
  ```
- **Cobertura (JaCoCo):** `./mvnw -B -ntp -pl user-service verify` gera o relatório HTML em
  `target/site/jacoco/index.html`.

## Arquitetura hexagonal (pacotes)

```
core/            domínio puro (JDK only): User, Email (VO), CpfMasker, PageQuery, ports in/out, exceções
application/     casos de uso: criar, buscar por id, listar (paginado), atualizar e excluir usuário
adapters/
  in/web/        controllers REST, DTOs (Bean Validation), mappers, GlobalExceptionHandler (ProblemDetail)
  out/persistence/ entidade JPA, repositório Spring Data, mapper
  out/security/  BcryptPasswordEncoderAdapter (hash de senha)
```

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/users` | Cria usuário (201 com `Location`); e-mail/CPF duplicado → **409** (CPF **mascarado**) |
| GET | `/api/v1/users` | Lista paginada (`page` ≥ 0, `size` entre 1 e 100) |
| GET | `/api/v1/users/{id}` | Busca por id — 404 se inexistente |
| PUT | `/api/v1/users/{id}` | Substituição completa; senha opcional (mantém a atual se omitida) |
| DELETE | `/api/v1/users/{id}` | Remove o usuário (204) |

## Regras de validação de entrada

- **Nome:** obrigatório, até 100 caracteres.
- **E-mail:** obrigatório, formato válido, normalizado (trim + minúsculas), até 150 caracteres, **único**.
- **CPF:** obrigatório, exatamente 11 dígitos numéricos, **único**.
- **Senha:** entre **8 e 72 caracteres** — o teto de 72 respeita o limite do BCrypt (72 bytes) e troca o
  antigo erro 500 por um **400** claro; é persistida apenas como hash BCrypt.

Swagger UI: `http://localhost:8081/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.
