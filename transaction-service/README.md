# transaction-service (MS2)

Serviço de transações financeiras (depósito, retirada, transferência, compra) em **arquitetura hexagonal**
(Java 25 + Spring Boot 4.1.2 + PostgreSQL 17). Cada transação termina `APPROVED` ou `REJECTED` — a rejeitada
também é gravada, com o motivo.

## Como rodar

O ambiente completo (PostgreSQL + MS1 + MS2 via Docker Compose, `.env`, build e testes) está no
**[README da raiz](../README.md) → seção "Como rodar"**. Abaixo, só o que é específico do MS2.

- **Swagger / porta:** `http://localhost:8082/swagger-ui.html` · OpenAPI JSON em `/v3/api-docs`.
- **API Mock (saldo e limites):** `ACCOUNT_API_BASE_URL` aponta para um recurso `accounts` no
  [mockapi.io](https://mockapi.io) (URL base, **sem** `/accounts`), um registro por usuário:
  ```json
  { "userId": "<UUID>", "balance": 1000.00, "dailyLimit": 2000.00, "transactionLimit": 800.00 }
  ```
  A URL tem token sensível no subdomínio — nunca é registrada em log, exceção ou resposta.
- **Rodar só este módulo:** `./mvnw -B -ntp -pl transaction-service -am verify` (137 testes; cobertura em `target/site/jacoco/index.html`).
- **Roteiro de demonstração:** [`docs/DEMO-E-ROADMAP.md`](docs/DEMO-E-ROADMAP.md).

## Arquitetura hexagonal (pacotes)

```
core/            domínio puro (JDK only): entidades, VOs, TransactionPolicy, ports in/out, exceções
application/     casos de uso: orquestram ports, centralizam o cálculo de saldo/limites e as regras de aprovação
adapters/
  in/web/        controllers REST, DTOs (Bean Validation), mappers, GlobalExceptionHandler (ProblemDetail)
  out/account/   cliente HTTP da API de contas (MockAPI), com timeout e tradução de falha
  out/exchange/  cliente HTTP da BrasilAPI (câmbio PTAX), tolerante a falha
  out/persistence/ entidades JPA, repositório Spring Data, specifications, mapper
  config/        AppProperties, Clock/ZoneId, RestClient (timeouts), OpenAPI
```

O teste de arquitetura (`architecture/HexagonalArchitectureTest`, ArchUnit) garante as fronteiras: o **core** não
importa Spring/JPA/HTTP/Jackson/Swagger; a **application** só usa `core` + `org.springframework.stereotype`/`.transaction`.

## Saldo e limites: a Mock é somente leitura (parágrafo obrigatório)

A cada transação, o MS2 consulta a **API de contas (MockAPI)** para obter o **saldo inicial** e os **limites**
(`dailyLimit`, `transactionLimit`). **O saldo não é gravado de volta na Mock nem persistido no banco do MS2.**

Motivo: a Mock não se atualiza sozinha quando aprovamos uma transação, e manter uma cópia do saldo no nosso banco
criaria **duas fontes do mesmo dado**, uma delas fadada a ficar desatualizada. Então o saldo é **derivado em tempo
real** usando a MockAPI como ponto de partida:

- **Saldo atual** = saldo inicial da Mock **+** histórico de transações **aprovadas** do MS2
  (depósitos somam, saídas — saque/transferência/compra — subtraem) **+ transferências recebidas**
  (quando o usuário é o **destinatário**), ignorando as excluídas.
- **Limite diário** = soma das **saídas aprovadas do dia** (dia no fuso `app.time-zone`, padrão `America/Sao_Paulo`).
- A política de aprovação (`TransactionPolicy`) usa esse saldo atual derivado, nunca o saldo bruto da Mock.
- `account.balanceAfter` na resposta do POST é **derivado** (saldo atual ± valor); não é persistido.
- A rota `GET /api/v1/accounts/balance` (usuário via **Basic Auth**) expõe esse saldo derivado com `dailyLimit`/`transactionLimit`
  (da Mock) e `spentToday`/`availableToday`; conta inexistente → **404**, Mock fora do ar → **503** (sem a URL na mensagem).

## Resiliência da integração

- O cliente HTTP da Mock tem **timeout de conexão (2s) e de leitura (3s)** — a requisição nunca fica travada
  (comprovado por `AccountRestClientReadTimeoutTest`: uma resposta artificialmente lenta de **4s** é abortada bem antes).
- **Mock fora do ar** (timeout, erro de rede, 5xx) → a transação é gravada como **`REJECTED`** com motivo
  `ACCOUNT_SERVICE_UNAVAILABLE` e a API responde **201** (dois estados apenas e registro auditável).
- **Conta inexistente** → `REJECTED` com `ACCOUNT_NOT_FOUND`.
- O câmbio (BrasilAPI) é **tolerante a falha**: se indisponível, a transação é criada mesmo assim e o bloco
  `exchange` sinaliza `available: false` com uma mensagem de indisponibilidade.
- A taxa exibida é o **último fechamento PTAX publicado — dia útil anterior (D-1 em `America/Sao_Paulo`)**:
  a BrasilAPI **não publica a cotação do dia corrente** (responde 400 `NO_TODAY_DATE`). Fins de semana e
  feriados são resolvidos pela própria API (devolve o último dia útil); o recuo de até 7 dias cobre "sem dados".
  `quotedAt` vem do `data_hora_cotacao` do boletim usado. A cotação é gravada como **snapshot** no momento da
  criação, de modo que a leitura posterior da transação não depende de nova chamada externa.

## Endpoints

Todas as rotas exigem **Basic Auth** (`Authorization: Basic <base64(email:senha)>`, validado no MS1): o usuário é o
**autenticado**, então **nenhuma rota recebe `userId` por query string**. Ex.: `curl -u email:senha http://localhost:8082/api/v1/accounts/balance`.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/transactions` | Cria a transação (201 em APPROVED **e** REJECTED, com `Location`) |
| GET | `/api/v1/transactions/{id}` | Consulta por id, com bloco `exchange` (USD/EUR) — 404 se inexistente/excluída |
| GET | `/api/v1/transactions` | Lista paginada do usuário autenticado; filtros `type`, `status`, `from`, `to` (datas inclusivas) |
| PATCH | `/api/v1/transactions/{id}` | Edita metadados (`description`, `category`); valor/tipo/status imutáveis |
| DELETE | `/api/v1/transactions/{id}` | Exclusão **lógica** (apenas REJECTED; preserva o histórico) — 422 se APPROVED |
| GET | `/api/v1/accounts/balance` | Saldo atual derivado + limites + consumo do dia |
| GET | `/api/v1/analytics/expenses/summary?month=MM-YYYY` | Análise de despesas do mês (padrão = mês atual): visão geral, raio-X por grau de necessidade, onde cortar (top 5), custo fixo, total por dia e alerta do "buraco negro" |
| GET | `/api/v1/transactions/report?from=&to=&format=csv\|xlsx\|pdf` | **Relatório** das transações do período (padrão = mês atual) para **download** — CSV (UTF-8 com BOM, `;`), Excel `.xlsx` nativo ou PDF |

> "Corrigir sem apagar" (critério do PO): a correção é feita por **nova transação** ou por edição de metadados;
> a "remoção" é **soft delete** (`deleted_at`), restrita a transações rejeitadas, preservando o histórico.

Swagger UI: `http://localhost:8082/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

## Rotas e relatórios (exportação CSV/Excel)

A rota `GET /api/v1/transactions/report` baixa as transações do usuário num período (datas inclusivas no fuso
`America/Sao_Paulo`; sem `from`/`to` usa o **mês atual**). Inclui transações **APPROVED e REJECTED** (com as
colunas de status e motivo) e **exclui** as removidas (_soft delete_). Colunas: data, tipo, categoria nível 1,
subcategoria, descrição, valor em BRL, status, motivo da rejeição e valores convertidos USD/EUR do _snapshot_
de câmbio. O formato é escolhido por **content negotiation** no parâmetro `?format=`:

- `?format=csv` (padrão): **UTF-8 com BOM** + separador `;` + decimal com vírgula, para **abrir no Excel pt-BR
  sem quebrar acento**; descrições com `;`/aspas/quebra de linha são escapadas (RFC-4180).
- `?format=xlsx`: planilha **`.xlsx` nativa** (Apache POI) com datas e moedas em **células tipadas** (somáveis),
  cabeçalho congelado e colunas auto-dimensionadas.
- `?format=pdf`: documento **PDF** (OpenPDF) em tabela paisagem A4 — formato de apresentação para impressão/anexo.

Todos respondem com `Content-Disposition: attachment; filename="relatorio.csv|.xlsx|.pdf"`. A **formatação do
arquivo mora na camada de adapters** (`TransactionCsvWriter`/`TransactionExcelWriter`/`TransactionPdfWriter`): o
core/application só lidam com `ReportRow` tipado e não conhecem CSV, BOM, Apache POI nem OpenPDF (ArchUnit verde).
Detalhes de decisão em [`docs/PEDIDO-7-RELATORIO.md`](docs/PEDIDO-7-RELATORIO.md).

> **Autenticação:** o relatório é sempre do **usuário autenticado** (Basic Auth); não há `userId` por parâmetro.

## Variáveis de ambiente

| Variável | Significado | Exemplo |
|---|---|---|
| `ACCOUNT_API_BASE_URL` | URL base da API de contas (MockAPI), **sem** `/accounts`. **Sensível** (token no subdomínio) | `https://SEU-TOKEN.mockapi.io/api/v1` |
| `EXCHANGE_API_BASE_URL` | URL base da BrasilAPI, **sem** `/cambio/v1/cotacao/...` | `https://brasilapi.com.br/api` |
| `SPRING_DATASOURCE_*` | Conexão com `transactions_db` | ver `.env.example` |

Configuração do MockAPI (recurso `accounts`, um por usuário): campos `userId`, `balance`, `dailyLimit`,
`transactionLimit`. A URL da Mock **nunca** é registrada em log, exceção ou `ProblemDetail`.
(Como subir e configurar: ver a seção **Como rodar**, no topo.)

## Limitações conhecidas (sem correção nesta entrega)

- **Sem transação distribuída** entre a Mock e o banco. Como a Mock é somente leitura, não há escrita externa a
  compensar; uma falha de persistência apenas resulta em 500, sem efeito colateral na Mock.
- **Leitura concorrente do saldo (sem trava — limitação aceita, D4):** o saldo é **derivado por leitura** e só então
  a transação é persistida, **sem lock pessimista nem checagem atômica**. Duas saídas **simultâneas** do mesmo usuário
  podem ler o **mesmo saldo** (antes de qualquer uma gravar) e ambas serem aprovadas, ultrapassando saldo/limite
  (condição de corrida _read-then-write_). É **conhecida e aceita** nesta entrega; mitigá-la exigiria trava
  otimista/pessimista ou serialização por usuário, fora do escopo atual.
