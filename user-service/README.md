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
core/            domínio puro (JDK only): User, Email (VO), CpfMasker, PageQuery, ImportResult, ports in/out, exceções
application/     casos de uso: criar, buscar por id, listar, atualizar, excluir e importar em massa (CSV)
adapters/
  in/web/        controllers REST (inclui UserBatchController + UserCsvParser), DTOs (Bean Validation),
                 mappers, GlobalExceptionHandler (ProblemDetail)
  in/web/security/ Basic Auth (SecurityConfig, CustomUserDetailsService) + gate M2M (InternalApiKeyFilter)
  out/persistence/ entidade JPA, repositório Spring Data, mapper
  out/security/  BcryptPasswordEncoderAdapter (hash de senha)
```

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/v1/users` | 🔓 pública | Cria usuário (201 com `Location`); e-mail/CPF duplicado → **409** (CPF **mascarado**) |
| POST | `/api/v1/users/batch` | 🔓 pública | **Cadastro em massa via CSV** (`multipart/form-data`); importação resiliente linha a linha |
| GET | `/api/v1/users` | 🔒 Basic | Lista paginada (`page` ≥ 0, `size` entre 1 e 100) |
| GET | `/api/v1/users/{id}` | 🔒 Basic | Busca por id — 404 se inexistente |
| PUT | `/api/v1/users/{id}` | 🔒 Basic | Substituição completa; senha opcional (mantém a atual se omitida) |
| DELETE | `/api/v1/users/{id}` | 🔒 Basic | Remove o usuário (204) |

## Segurança (Basic Auth)

O CRUD de usuários é protegido por **HTTP Basic Auth** (Spring Security). A estratégia de credenciais é a
**mesma** de `POST /api/v1/auth/validate`: o usuário se autentica com o **próprio e-mail + senha**, validados
contra o banco (hash **BCrypt**) pelo `CustomUserDetailsService`. Não há usuário/senha fixos no código.

- **Rotas públicas:** `POST /api/v1/users` e `POST /api/v1/users/batch` (cadastro individual e em massa —
  abertos para novos usuários se registrarem), Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) e `/actuator/health`.
- **Rotas protegidas:** todo o restante de `/api/v1/users/**` (listar, buscar, atualizar, excluir) → **401**
  (`application/problem+json`) sem credenciais válidas.
- **Rota interna M2M** `/api/v1/auth/validate`: continua protegida pelo `InternalApiKeyFilter` (header
  `X-Internal-Key`), que roda **antes** do Spring Security — a configuração de segurança não interfere nele.

```bash
# 1) Cria um usuário (público) — guarde o e-mail e a senha usados
curl -s -X POST http://localhost:8081/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana Souza","email":"ana.souza@email.com","cpf":"11122233344","password":"Senha@Teste123"}'

# 2) Lista usuários usando Basic Auth com essas credenciais
curl -s -u 'ana.souza@email.com:Senha@Teste123' http://localhost:8081/api/v1/users

# Sem credenciais → 401
curl -i http://localhost:8081/api/v1/users
```

## Regras de validação de entrada

- **Nome:** obrigatório, até 100 caracteres.
- **E-mail:** obrigatório, formato válido, normalizado (trim + minúsculas), até 150 caracteres, **único**.
- **CPF:** obrigatório, exatamente 11 dígitos numéricos, **único**.
- **Senha:** entre **8 e 72 caracteres** — o teto de 72 respeita o limite do BCrypt (72 bytes) e troca o
  antigo erro 500 por um **400** claro; é persistida apenas como hash BCrypt.

## Cadastro em massa (CSV)

`POST /api/v1/users/batch` recebe `multipart/form-data` com o campo **`file`** (um CSV). O parsing fica na
camada web (`UserCsvParser`): tolera **BOM UTF-8** e separador **`;` ou `,`**; o cabeçalho obrigatório é
`name,email,cpf,password` (em qualquer ordem).

A importação é **resiliente**: cada linha é validada (Bean Validation) e criada na sua própria transação —
uma linha inválida (CPF/e-mail inválido ou duplicado) é **rejeitada individualmente** e **não interrompe** as
demais. A resposta (200) traz o resumo:

```json
{
  "totalProcessed": 3,
  "totalSuccess": 2,
  "totalFailed": 1,
  "errors": [ { "line": 4, "reason": "Email deve ser válido" } ]
}
```

Arquivo de exemplo pronto para a demo (3 usuários, 1 com e-mail inválido de propósito):
[`usuarios_mock_teste.csv`](../usuarios_mock_teste.csv).

```bash
curl -s -X POST http://localhost:8081/api/v1/users/batch \
  -F 'file=@usuarios_mock_teste.csv;type=text/csv'
```

Swagger UI: `http://localhost:8081/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.
