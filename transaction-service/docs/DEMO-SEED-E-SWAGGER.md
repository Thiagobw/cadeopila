# Base de demonstração (seed) + guia dos campos do Swagger

Este documento cobre duas coisas para a apresentação:

1. **Como popular uma base de demonstração** (3 usuários com ~51 transações cada, aprovadas e rejeitadas,
   com transferências entre eles) para que o ambiente já suba com histórico.
2. **Como preencher os campos do Swagger** nas rotas de **Relatório** e **Análise de Despesas** para executar
   sem erro.

> As instruções de "como subir o ambiente" (Docker, `.env`, MockAPI) ficam no
> [README da raiz](../../README.md). Aqui tratamos só do seed de demonstração e do uso das rotas.

---

## 1. Seed de demonstração

### O que o seed cria

- **3 usuários** (senha de todos: `Senha@Teste123`):
  - Ana Souza — `ana.souza@email.com`
  - Bruno Lima — `bruno.lima@email.com`
  - Carol Dias — `carol.dias@email.com`
- Para cada um, uma **conta na MockAPI** (saldo 3000; limite diário 5000; limite por transação 5000).
- Para cada um, **~51 transações** espalhadas ao longo de **2026** (jan–nov), com:
  - **depósitos** mensais (receita), **compras** variadas (categorias e graus de necessidade diferentes,
    algumas fixas/recorrentes), **saques** e **transferências entre os 3 usuários**;
  - **~7 rejeitadas** com motivos variados (`TRANSACTION_LIMIT_EXCEEDED`, `INSUFFICIENT_BALANCE`,
    `DAILY_LIMIT_EXCEEDED`), que ficam no histórico com o motivo;
  - em **agosto/2026**, gastos em `OUTROS` concentrados para disparar o alerta do **"buraco negro"** na análise.

### Pré-requisitos

1. Ambiente no ar: `docker compose up --build` (ver README da raiz), com `.env` preenchido
   (inclui `ACCOUNT_API_BASE_URL` apontando para a MockAPI).
2. Os dois serviços com health `UP` (MS1 em `:8081`, MS2 em `:8082`).
3. Docker acessível pela linha de comando (o seed usa `docker exec` no container `cadeopila-postgres`).

> **Base limpa do zero:** se quiser recomeçar sem resíduos, derrube os volumes antes de subir:
> `docker compose down -v` e depois `docker compose up --build`.

### Como rodar

No PowerShell, a partir da raiz do projeto:

```powershell
pwsh ./infra/seed/Seed-Demo.ps1
# ou, no Windows PowerShell 5.1:
powershell -ExecutionPolicy Bypass -File ./infra/seed/Seed-Demo.ps1
```

O script é **idempotente**: pode rodar várias vezes. Antes de inserir, ele apaga o seed anterior dos 3 usuários
(não duplica). Ao final, imprime os e-mails, a senha, os UUIDs e a contagem de transações por usuário.

### Como funciona (resumo técnico)

- Cadastra os usuários pela **API pública** do MS1 (`POST /api/v1/users`) e captura o UUID de cada um.
- Garante a **conta na MockAPI** de cada UUID (`GET/POST /accounts`).
- Insere as transações **direto no `transactions_db`** (via `docker exec … psql`). Isso é necessário porque a
  API sempre grava `occurredAt = agora`; para espalhar as datas pelo ano, o insert é feito no banco, já com o
  snapshot de câmbio USD/EUR e o status/motivo coerentes com as constraints do schema.

> Observação: na apresentação, ao **criar um usuário novo ao vivo** pelo Swagger do MS1, lembre que ele só terá
> conta na MockAPI (e, portanto, saldo/limites para transacionar) se você também criar a conta dele — os 3
> usuários do seed já vêm com conta pronta.

---

## 2. Guia dos campos do Swagger (rodar sem erro)

> **Pré-requisito comum às duas rotas:** elas exigem **autenticação (Basic Auth)**. No Swagger do MS2
> (`http://localhost:8082/swagger-ui.html`) clique em **Authorize** e informe **e-mail + senha** de um usuário
> demo (ex.: `ana.souza@email.com` / `Senha@Teste123`). **Sem Authorize → 401.**
> O usuário do relatório/análise é **sempre o autenticado** — não existe campo de "userId" para preencher.

### 2.1 Relatório — `GET /api/v1/transactions/report`

Exporta as transações do usuário (inclui **APROVADAS e REJEITADAS**; exclui as removidas) para download.

| Campo    | Obrigatório | Formato / valores            | Observações |
|----------|-------------|------------------------------|-------------|
| `from`   | Não         | Data **`yyyy-MM-dd`** (ISO)  | Início do período (inclusivo). Deve ser **≤ `to`**. |
| `to`     | Não         | Data **`yyyy-MM-dd`** (ISO)  | Fim do período (inclusivo). |
| `format` | Não         | **`csv`** \| `xlsx` \| `pdf` | Padrão `csv`. Qualquer outro valor → 400. |

**Como executar sem erro:**
- **Mais simples:** deixe os três campos **vazios** → usa o **mês atual** e formato `csv`.
- **Para ver o ano todo do seed:** `format = pdf` (ou `xlsx`/`csv`), `from = 2026-01-01`, `to = 2026-12-31`.

**Erros comuns:**
- `401` — esqueceu o **Authorize**.
- `400` — `from` depois de `to`; `format` fora de `csv/xlsx/pdf`; data fora do padrão `yyyy-MM-dd`.

> Dica: como o seed **espalha as transações pelo ano**, o padrão (mês atual) mostra só novembro. Para o
> relatório vir "cheio", informe `from=2026-01-01` e `to=2026-12-31`.

### 2.2 Análise de Despesas — `GET /api/v1/analytics/expenses/summary`

Resumo inteligente do mês (só **APROVADAS**): visão geral, raio-X por grau de necessidade, onde cortar (top 5),
custo fixo, total por dia e alerta do "buraco negro".

| Campo   | Obrigatório | Formato / valores        | Observações |
|---------|-------------|--------------------------|-------------|
| `month` | Não         | **`MM-YYYY`** (ex.: `11-2026`) | Sem o campo → **mês atual** (fuso America/Sao_Paulo). |

**Como executar sem erro:**
- **Mais simples:** deixe `month` **vazio** → usa o **mês atual** (novembro tem dados no seed).
- **Para ver o alerta do "buraco negro":** `month = 08-2026` (agosto concentra gastos em `OUTROS`).
- Outros meses com dados: `01-2026` … `11-2026`.

**Erros comuns:**
- `401` — esqueceu o **Authorize**.
- `400` — `month` fora de `MM-YYYY`. **Atenção à ordem:** é **mês-ano** (`08-2026`), **não** `2026-08`.
- `200` com blocos vazios — mês sem transações aprovadas (ex.: dezembro no seed).

---

## 3. Checklist rápido para a demo

1. `docker compose up --build` → aguardar health `UP` dos dois serviços.
2. `pwsh ./infra/seed/Seed-Demo.ps1` → conferir o resumo impresso (3 usuários, ~51 tx cada).
3. Swagger MS2 → **Authorize** como `ana.souza@email.com` / `Senha@Teste123`.
4. `GET /analytics/expenses/summary?month=08-2026` → 200 com `showBlackHoleWarning: true`.
5. `GET /transactions/report?format=pdf&from=2026-01-01&to=2026-12-31` → baixa o PDF com linhas aprovadas e rejeitadas.
6. `GET /transactions?status=REJECTED` → lista as rejeitadas com o motivo.
7. `GET /accounts/balance` → saldo derivado positivo.
