# Handoff — Pedido 5: Análise de despesas (MS2 `transaction-service`)

Briefing **autossuficiente** para implementar a análise de despesas numa sessão dedicada. Leia inteiro antes de
codar. O objetivo é executar sem erro, reaproveitando o que já existe e sem violar a arquitetura.

---

## 0. Contexto mínimo (para quem chega sem contexto)

- Monorepo `cadeopila`, 2 microsserviços Spring Boot. **MS2 = `transaction-service`** (Java 25, Spring Boot 4.1.2,
  PostgreSQL 17, porta 8082). Pedidos 1/2/3 já entregues e verdes (POST/GET/saldo/câmbio + edição/soft-delete).
- **Arquitetura hexagonal ESTRITA** — há um ArchUnit (`src/test/java/.../architecture/HexagonalArchitectureTest.java`)
  que **quebra o build** se violar:
  - `core/` → só JDK. Sem Spring, JPA, HTTP, Jackson, Swagger. Entidades, VOs, ports (`ports/in`, `ports/out`), exceções.
  - `application/` → casos de uso. Só pode depender de `core` + `org.springframework.stereotype` e `org.springframework.transaction`.
  - `adapters/` → framework: `in/web` (controllers, DTOs, mappers, handler), `out/persistence` (JPA), `out/account`, `out/exchange`, `config`.
- **Fuso:** `app.time-zone = America/Sao_Paulo`. Beans prontos em `adapters/config/AppConfig`: `Clock` (UTC) e
  `ZoneId` (`appZoneId`). **Nunca** usar `Instant.now()`/`LocalDate.now()` direto — injetar `Clock` + `ZoneId`.
- **Build/test:** `./mvnw -B -ntp -pl transaction-service -am verify` **tem que ficar VERDE** (hoje 137 testes;
  cobertura instrução 98,3% / classes 100%). Testes usam H2 (modo PostgreSQL); Postgres real só no Compose.
- **Git:** a IA **não commita** — entrega os comandos prontos para o usuário rodar.
- **README:** instruções de "como rodar" ficam no **README da raiz**; o README do serviço tem a tabela de endpoints
  (acrescentar a nova rota lá).

---

## 1. Critério de aceite do PO (Pedido 5)

- **Só saídas APROVADAS contam como despesa:** `WITHDRAWAL`, `TRANSFER`, `PURCHASE`. **Depósitos e rejeitadas NÃO entram.**
  (Transações excluídas/soft-deleted também não.)
- **Resumo por categoria** num período: total de cada categoria **+ total geral**.
- **Total por dia** e **total por mês** no período.
- **Período padrão, quando não informado: o mês atual.**
- Checks de aceite:
  1. 5 compras em 3 categorias → resumo mostra **3 linhas** e o total **bate** com a soma.
  2. Rejeitadas e depósitos **não aparecem**.
  3. Sem informar período → **mês atual**.

---

## 2. Decisão de design central — AGREGAR EM JAVA, não no banco

**Não faça `GROUP BY` de data (dia/mês) no SQL.** O repositório devolve as **linhas cruas de despesa** do período
(categoria, valor, `occurredAt`); o **caso de uso agrupa em Java** (por categoria, por dia, por mês) usando o `ZoneId`.

Motivo: truncar `TIMESTAMPTZ` por dia/mês **no fuso certo** é frágil e não-portável entre H2 (testes) e Postgres
(produção); o volume é pequeno (demo); e a lógica de fuso fica **testável** na camada application. Agrupar em UTC
daria dia/mês errados (ex.: transação às 23:00 UTC cai no dia seguinte em `America/Sao_Paulo`).

---

## 3. Peças EXISTENTES para reusar (caminhos e assinaturas reais)

- `core/domain/TransactionType` → enum `DEPOSIT, WITHDRAWAL, TRANSFER, PURCHASE`; método `isOutflow()` (true p/ tudo menos DEPOSIT).
- `core/domain/TransactionCategory` → enum `FOOD, TRANSPORT, HOUSING, HEALTH, EDUCATION, LEISURE, SHOPPING, BILLS, SALARY, OTHER`.
- `core/domain/TransactionStatus` → `APPROVED, REJECTED`.
- `core/exception/InvalidTransactionFilterException` → já mapeada para **400** no `GlobalExceptionHandler` (use p/ período inválido).
- `GlobalExceptionHandler` já trata `MissingServletRequestParameterException` (param faltando → 400) e
  `MethodArgumentTypeMismatchException` (ex.: `userId`/data mal formatada → 400). **Não precisa criar handler novo.**
- `adapters/out/persistence/TransactionPersistenceAdapter` → já injeta `ZoneId`; tem a constante
  `OUTFLOW_TYPES` (lista dos tipos de saída) e o padrão de fronteira de dia:
  `date.atStartOfDay(zoneId).toInstant()` e `date.plusDays(1).atStartOfDay(zoneId).toInstant()` (meia-aberta, datas inclusivas).
- `adapters/out/persistence/TransactionJpaRepository` → já tem `@Query` JPQL com `status`/`types IN :types`
  (métodos `sumSignedApproved`, `sumOutflows`) — **copie o estilo**.
- Padrões de caso de uso: `@Service`, `@Transactional(readOnly = true)`, injeção por construtor, `Clock` + `ZoneId`.
  Veja `application/usecase/GetAccountBalanceUseCaseImpl` (quase idêntico ao que você vai fazer).
- Padrões de web: `TransactionController`/`AccountController` (`@Tag`, `@Operation`, `@ApiResponses`, DTO `@Schema`,
  mapper `@Component`), `adapters/in/web/dto/*`, `adapters/in/web/mapper/*`.
- Padrões de teste: use case com Mockito (`CreateTransactionUseCaseImplTest`), repositório com `@DataJpaTest`
  + `@Import` + `TestConfig` que provê o bean `ZoneId` (`TransactionPersistenceAdapterTest`), controller com
  `@WebMvcTest` + `@MockitoBean` + `@Import(mapper)`, integração `@SpringBootTest` + MockMvc.

---

## 4. Implementação passo a passo

### 4.1 `core` — VO de linha crua
`core/domain/ExpenseRow.java`:
```java
public record ExpenseRow(TransactionCategory category, BigDecimal amount, Instant occurredAt) {}
```

### 4.2 `core` — porta de saída (nova consulta)
Em `core/ports/out/TransactionRepositoryPort` adicione:
```java
/** Saídas APROVADAS (não excluídas) do usuário com occurredAt em [início, fim). Nunca retorna null. */
List<ExpenseRow> findApprovedExpenses(UUID userId, Instant startInclusive, Instant endExclusive);
```

### 4.3 `core` — porta de entrada (caso de uso)
`core/ports/in/GetExpenseAnalysisUseCase.java`:
```java
public interface GetExpenseAnalysisUseCase {
    Analysis analyze(UUID userId, LocalDate from, LocalDate to); // from/to podem ser null → mês atual

    record Analysis(LocalDate from, LocalDate to,
                    List<CategoryTotal> byCategory, BigDecimal total,
                    List<DailyTotal> byDay, List<MonthlyTotal> byMonth) {}
    record CategoryTotal(TransactionCategory category, BigDecimal total) {}
    record DailyTotal(LocalDate date, BigDecimal total) {}
    record MonthlyTotal(YearMonth month, BigDecimal total) {}
}
```

### 4.4 `application` — caso de uso (toda a lógica fica aqui)
`application/usecase/GetExpenseAnalysisUseCaseImpl.java` (`@Service`, `@Transactional(readOnly = true)`, injeta
`TransactionRepositoryPort`, `Clock`, `ZoneId`):
```java
public Analysis analyze(UUID userId, LocalDate from, LocalDate to) {
    LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
    LocalDate effFrom = from != null ? from : today.withDayOfMonth(1);
    LocalDate effTo   = to   != null ? to   : today.withDayOfMonth(today.lengthOfMonth());
    if (effFrom.isAfter(effTo))
        throw new InvalidTransactionFilterException("from não pode ser depois de to");

    Instant start = effFrom.atStartOfDay(zoneId).toInstant();
    Instant end   = effTo.plusDays(1).atStartOfDay(zoneId).toInstant(); // meia-aberta, datas inclusivas

    List<ExpenseRow> rows = repository.findApprovedExpenses(userId, start, end);

    // por categoria (ordem determinística: maior total primeiro, depois nome)
    Map<TransactionCategory, BigDecimal> cat = new EnumMap<>(TransactionCategory.class);
    // por dia / por mês no FUSO (TreeMap = ordem cronológica)
    Map<LocalDate, BigDecimal> day = new TreeMap<>();
    Map<YearMonth, BigDecimal> month = new TreeMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (ExpenseRow r : rows) {
        cat.merge(r.category(), r.amount(), BigDecimal::add);
        ZonedDateTime z = r.occurredAt().atZone(zoneId);
        day.merge(z.toLocalDate(), r.amount(), BigDecimal::add);
        month.merge(YearMonth.from(z), r.amount(), BigDecimal::add);
        total = total.add(r.amount());
    }
    // montar as listas (CategoryTotal ordenado por total desc, depois category.name())
    // ...
    return new Analysis(effFrom, effTo, byCategory, total, byDay, byMonth);
}
```
> `total` tem que **bater** com a soma de `byCategory` (mesmas linhas). Em período vazio: listas vazias e `total = 0` (padronize `0.00`).

### 4.5 `adapters/out/persistence` — query + mapeamento
Em `TransactionJpaRepository` (copie o estilo de `sumOutflows`):
```java
@Query("""
        SELECT t FROM TransactionJpaEntity t
        WHERE t.userId = :userId AND t.status = :status AND t.type IN :types
          AND t.deletedAt IS NULL AND t.occurredAt >= :start AND t.occurredAt < :end
        """)
List<TransactionJpaEntity> findApprovedOutflows(@Param("userId") UUID userId,
        @Param("status") TransactionStatus status, @Param("types") Collection<TransactionType> types,
        @Param("start") Instant start, @Param("end") Instant end);
```
Em `TransactionPersistenceAdapter.findApprovedExpenses(...)`: chame o método acima com
`TransactionStatus.APPROVED` e `OUTFLOW_TYPES`, e mapeie cada entidade para
`new ExpenseRow(e.getCategory(), e.getAmount(), e.getOccurredAt())`.

### 4.6 `adapters/in/web` — rota + DTOs + mapper + Swagger
- **Rota:** adicionar em `TransactionController` → `@GetMapping("/analysis")` (caminho literal; o Spring prefere
  `/analysis` ao `/{id}`, então não conflita — **mas escreva um teste provando isso**). Assinatura:
  ```java
  @GetMapping("/analysis")
  public ResponseEntity<ExpenseAnalysisResponse> analysis(
      @RequestParam UUID userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { ... }
  ```
  (Se preferir, um `ExpenseController` dedicado em `/api/v1/transactions/analysis` também serve — evita qualquer dúvida de rota.)
- **DTOs** (`adapters/in/web/dto`, com `@Schema`): `ExpenseAnalysisResponse(from, to, byCategory, total, byDay, byMonth)`,
  `CategoryTotalResponse(category, total)`, `DailyTotalResponse(date, total)`, `MonthlyTotalResponse(String month, total)`.
  **`month` como `String` (`"2026-11"` = `YearMonth.toString()`)** — evita ambiguidade de serialização do `YearMonth`.
- **Mapper:** método em `TransactionWebMapper` (ou `AnalysisWebMapper`) convertendo `Analysis` → `ExpenseAnalysisResponse`.
- **Swagger:** `@Operation` + `@ApiResponses` (200; 400 período/param inválido).

### 4.7 Validação / erros (reusar o que já existe)
- `userId` ausente → 400 (handler `MissingServletRequestParameterException` já existe).
- `from`/`to` mal formatadas → 400 (handler `MethodArgumentTypeMismatchException` já existe).
- `from > to` → lance `InvalidTransactionFilterException` → 400 (handler já existe).

---

## 5. Armadilhas de correção (CHECKLIST — não erre nestes)

1. **Só APPROVED + só saída** (`isOutflow`), **não excluídas**. Depósito e REJECTED fora. (Garanta na query E teste.)
2. **Agrupar dia/mês no fuso** `America/Sao_Paulo` (`occurredAt.atZone(zoneId)`), **nunca em UTC**.
3. **Período:** datas **inclusivas**; instante **meia-aberto** `[start, end)` com `end = to.plusDays(1).atStartOfDay(zone)`.
4. **Default = mês atual** quando `from`/`to` nulos (primeiro ao último dia do mês, no fuso). Decisão: se só um vier,
   o outro completa com o limite do mês atual.
5. **`total` reconcilia** com a soma de `byCategory` (mesmas linhas somadas).
6. **BigDecimal**: somar com `add`; manter escala 2; vazio → `0.00`.
7. **Ordenação determinística** (para teste estável): `byDay`/`byMonth` cronológico (TreeMap); `byCategory` por total desc e depois nome.
8. **ArchUnit**: caso de uso só usa `core` + `java.*` + `org.springframework.stereotype/.transaction`. `YearMonth`/`EnumMap`/`TreeMap` são JDK — ok.
9. **Rota `/analysis` x `/{id}`**: confirme por teste que `GET /api/v1/transactions/analysis` não cai no handler de id.

---

## 6. Testes obrigatórios (espelhe os padrões existentes)

- **`GetExpenseAnalysisUseCaseImplTest`** (Mockito, `Clock.fixed`):
  - 5 despesas em 3 categorias → `byCategory` com 3 linhas, `total` bate (check 1 do PO).
  - `byDay`/`byMonth` corretos no fuso (inclua uma linha às 23:00 UTC caindo no dia seguinte local).
  - sem período → usa mês atual (verifique os `Instant` passados ao repositório com clock fixo) (check 3).
  - período vazio → listas vazias e `total = 0.00`.
- **`TransactionPersistenceAdapterTest`** (`@DataJpaTest`, +casos): `findApprovedExpenses` traz só saídas APPROVED,
  não excluídas, do usuário, no período; exclui depósito/rejeitada/excluída/outro usuário (check 2).
- **Controller** (`@WebMvcTest`): 200 com corpo; `userId` ausente → 400; `from>to` → 400; rota `/analysis` resolve certo.
- **Integração** (`@SpringBootTest` + MockMvc, H2): cenário do PO ponta-a-ponta (5 compras/3 categorias) + depósito/rejeitada ignorados.

---

## 7. Convenções e Definition of Done

- `./mvnw -B -ntp -pl transaction-service -am verify` **VERDE**; cobertura mantida alta (domínio/aplicação ~100%).
- ArchUnit verde (fronteiras intactas).
- Swagger: a rota aparece e executa; DTOs com `@Schema`.
- **README do serviço** (`transaction-service/README.md`): acrescentar a linha da rota na tabela **## Endpoints**
  (ex.: `GET /api/v1/transactions/analysis` — análise de despesas por categoria/dia/mês). **Não** mexer no "como rodar".
- **Git (você NÃO commita):** entregue o comando pronto, mensagem no padrão do projeto, ex.:
  `feat(transaction-service): DBTB-XX analise de despesas por categoria/dia/mes`.
- Atualizar o handoff/roadmap (`docs/DEMO-E-ROADMAP.md`, seção A) marcando como concluído.

---

## 8. Jira (antes de commitar)

- Projeto **DBTB** (`tbeweber-nttdata-desafio.atlassian.net`, cloudId `e3e2b0f7-6727-499c-9230-f69d68463f7e`), via MCP Atlassian.
- **Pedido 5 ainda NÃO tem história no board** (os cards de `transaction-service` hoje são só Pedidos 1/2/3 —
  DBTB-8/9/10/57/58). Crie/confirme o **épico "Análise de despesas"** e uma **História** com o critério de aceite
  desta seção, e mova para "Fazendo" ao começar. Fluxo de status: A fazer (11) → Fazendo (21) → Feito (31).
