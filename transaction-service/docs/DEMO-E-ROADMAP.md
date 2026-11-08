# MS2 (transaction-service) — Roteiro de demo e handoff

Estado em 2026-11-08. Pedidos **1, 2, 3 e 5** entregues e verdes (`mvnw -pl transaction-service verify` →
163 testes, 0 falhas; ArchUnit verde).

> **Como subir, buildar e configurar a API Mock:** ver a seção **"Como rodar"** do
> [`../README.md`](../README.md) (convenção: instruções de execução ficam no README). Este documento é só o
> **roteiro de demonstração** (o que clicar/chamar) e o **roadmap** das próximas partes.

---

## 1. Roteiro de demonstração (13/11)

Pré-requisito: ambiente no ar conforme o README (`docker compose up --build`, `.env` preenchido, recurso
`accounts` no MockAPI). Portas: MS1 `8081`, MS2 `8082`. Use pelo Swagger (`:8082/swagger-ui.html`) ou Postman.
Mapeado ao roteiro do PO, recortado para o MS2. Use o `<UUID>` cadastrado no MockAPI como `userId`.

1. **Saldo (MockAPI):** `GET /api/v1/accounts/balance?userId=<UUID>` → 200 com `balance` (derivado), `dailyLimit`,
   `transactionLimit`, `spentToday`, `availableToday`.
2. **Transação aprovada:** `POST /api/v1/transactions`
   ```json
   {"userId":"<UUID>","type":"PURCHASE","amount":150.00,"description":"Mercado","category":"FOOD"}
   ```
   → **201** `status=APPROVED`, header `Location`, bloco `exchange` com USD/EUR, `account.balanceAfter`.
3. **Transação rejeitada:** `POST /api/v1/transactions` com saque acima do saldo
   ```json
   {"userId":"<UUID>","type":"WITHDRAWAL","amount":999999.00,"description":"Saque"}
   ```
   → **201** `status=REJECTED`, `rejectionReason=INSUFFICIENT_BALANCE` (fica **gravada**).
4. **Consulta com câmbio (BrasilAPI):** `GET /api/v1/transactions/{id}` → 200 com valor em BRL e conversão USD/EUR,
   taxa e `quotedAt` (último fechamento PTAX, D-1). Se a BrasilAPI cair, `exchange.available=false` e a consulta **não quebra**.
5. **Listagem:** `GET /api/v1/transactions?userId=<UUID>&type=PURCHASE&from=2026-11-01&to=2026-11-30` → página ordenada por `occurredAt desc`.
6. **Correção (PATCH):** `PATCH /api/v1/transactions/{id}` `{"description":"Mercado (corrigido)","category":"SHOPPING"}` → 200; valor/tipo/status intactos.
7. **Resiliência (MockAPI fora do ar):** no `.env`, troque `ACCOUNT_API_BASE_URL` por uma URL inválida e `docker compose up -d transaction-service`.
   - `POST` saída → **201** `REJECTED` / `ACCOUNT_SERVICE_UNAVAILABLE` (não aprova às cegas).
   - `GET /accounts/balance` → **503** (sem a URL na mensagem).
8. **Validação (400):** `POST` com corpo inválido (`amount:-1`, transferência sem `destinationUserId`, compra sem
   `category`) → **400** `ProblemDetail` com a lista de `violations` (campo + mensagem).
9. **Qualidade:** Swagger; `target/site/jacoco/index.html`; `README.md`.

---

## 2. O que já está pronto (resumo)

- **Pedido 1 (DBTB-8/57/58):** 4 tipos; APPROVED/REJECTED+motivo sempre 201; GET por id + lista paginada c/ filtros
  (type/status/período); PATCH metadados; DELETE lógico (só REJECTED → 422 se APPROVED). Domínio puro (ArchUnit verde).
- **Pedido 2 (DBTB-9):** saldo derivado (Mock = saldo inicial + histórico aprovado do MS2); rota de saldo; Mock fora do ar → 503/REJECTED; timeouts 2s/3s.
- **Pedido 3 (DBTB-10):** câmbio USD/EUR snapshot na criação, exibido no GET; fallback tolerante a falha; taxa = fechamento PTAX D-1 (America/Sao_Paulo).
- **Pedido 5 (análise de despesas):** `GET /api/v1/analytics/expenses/summary?month=MM-YYYY` (padrão = mês atual).
  Hierarquia de categorias (nível 1 = grau de necessidade: ESSENCIAL/ESTILO_DE_VIDA/SAUDE_FINANCEIRA/DIVIDAS_IMPREVISTOS;
  nível 2 livre) + flags `isFixed`/`isRecurring` na criação; regra de fricção do "OUTROS" (descrição ≥ 5) validada no
  domínio → 400. Payload com 4 blocos (visão geral, raio-X por nível 1 c/ % da receita, onde cortar top 5, custo fixo),
  total por dia e alerta do "buraco negro" (OUTROS > 5%). **Agregação em Java no fuso `America/Sao_Paulo`** (sem `GROUP BY` de data).
- **Pedido 8 (qualidade):** Bean Validation + ProblemDetail; Swagger; README técnico; JaCoCo; sem segredos no repo.

Arquitetura: `core` (JDK) → `application` (casos de uso) → `adapters/{in.web, out.account, out.exchange, out.persistence, config}`.
Build: Spring Boot 4.1.2, Java 25, Flyway V1+V2, springdoc 3.2.0, JaCoCo 0.8.15, ArchUnit 1.5.1.

---

## 3. Handoff — continuar à parte (seções futuras)

> Estas partes carregam complexidade e serão feitas em sessões separadas. Ordem combinada:
> **(A) Análise de despesas → (B) Relatório CSV → (C) Segurança (auth) por último.**

### (A) Pedido 5 — Análise de despesas (MS2) — ✅ CONCLUÍDO
Entregue com arquitetura de dados por **grau de necessidade** (além do resumo básico do PO). Briefing de contexto
em **[`PEDIDO-5-ANALISE-DESPESAS.md`](PEDIDO-5-ANALISE-DESPESAS.md)**.

Rota **`GET /api/v1/analytics/expenses/summary?userId=&month=MM-YYYY`** (sem `month` → mês atual). Só **APROVADAS**:
depósitos = receita; saque/transferência/compra = despesa. Payload: **visão geral** (receitas/despesas/saldo livre),
**raio-X** por nível 1 com % da receita, **onde cortar hoje** (top 5 subcategorias discricionárias — nível 1
ESTILO_DE_VIDA ou DIVIDAS_IMPREVISTOS), **custo fixo** (`isFixed`), total **por dia** e `showBlackHoleWarning`
(gasto em "OUTROS" > 5% das despesas). Criação ganhou `categoryLevel1`/`categoryLevel2`/`isFixed`/`isRecurring`
(opcionais; nível 1 derivado da categoria quando ausente) e a **regra de fricção do "OUTROS"** (descrição ≥ 5,
validada no domínio → 400). Agregação **em Java no fuso `America/Sao_Paulo`**; migration **V3** adiciona as colunas.

### (B) Pedido 7 — Relatório de transações em CSV (MS2) — DEPOIS de (A)
**Critério de aceite:** rota que devolve CSV para download das transações do usuário num período; colunas mínimas
`data, tipo, categoria, descrição, valor_brl, status, motivo` (pode somar valor convertido); **abre no Excel pt-BR
sem quebrar acento** e com separador reconhecível.
**Design/atenção:**
- `GET /api/v1/transactions/report?userId=&from=&to=` com `produces="text/csv"` e header `Content-Disposition: attachment; filename=...`.
- **Encoding:** UTF-8 **com BOM** + separador `;` (Excel pt-BR). Escapar `;`/aspas/quebra de linha nas descrições. Testar o arquivo abrindo no Excel (critério explícito do PO).
- Reusar `ListTransactionsUseCase`/`TransactionRepositoryPort.search` (sem paginação / página grande) para o período.
- "Só do usuário autenticado": enquanto **não há auth**, usar `userId` como parâmetro; quando Pedido 6 entrar, trocar por usuário do contexto de segurança.
- **Testes:** conteúdo do CSV (colunas, acentos, separador), filtro por usuário/período, header de download.

### (C) Pedido 6 — Segurança básica (auth) — POR ÚLTIMO
- Versão obrigatória: **Basic Auth** (usuário+senha) validando contra o MS1 ou base simples; rotas de transação/saldo/análise/relatório exigem autenticação; cadastro e docs ficam abertos; um usuário não acessa transações de outro; senha nunca em resposta/log.
- **"Segurança básica sem auth" (o que dá pra adiantar agora, se quiser):** sem autenticação não há "usuário do contexto"; mas é possível preparar o terreno — ex.: centralizar a extração do `userId` (hoje via `@RequestParam`) num único ponto para depois trocar pela identidade autenticada; garantir que nenhuma resposta/log exponha segredo; documentar no Swagger quais rotas ficarão protegidas. A trava real de ownership (um usuário não ver transações de outro) depende de `Principal`, então fica para quando o auth entrar.
- **Atenção:** Spring Security no classpath **protege tudo por padrão** — ao adicionar, configurar o `SecurityFilterChain` para liberar `/swagger-ui/**`, `/v3/api-docs/**` e o cadastro, senão a demo quebra.
