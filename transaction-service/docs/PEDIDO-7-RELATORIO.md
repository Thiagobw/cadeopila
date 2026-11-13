# Pedido 7 — Relatório de transações (CSV + Excel + PDF)

Decisões de projeto da entrega do relatório exportável do MS2 — **o que foi implementado e por quê**.

## O que a rota faz

`GET /api/v1/transactions/report?userId=&from=&to=&format=` baixa as transações do usuário num período:

- **Período:** datas inclusivas no fuso `America/Sao_Paulo`; sem `from`/`to` usa o **mês atual** (consistente
  com a análise de despesas). `from` depois de `to` → **400**.
- **Escopo:** inclui transações **APPROVED e REJECTED** (o relatório tem colunas de status/motivo) e **exclui**
  as que sofreram _soft delete_. Só do `userId` informado. Ordenado por data de ocorrência (crescente).
- **Colunas:** Data · Tipo · Categoria Nível 1 · Subcategoria · Descrição · Valor (BRL) · Status ·
  Motivo Rejeição · Valor (USD) · Valor (EUR). Os valores convertidos vêm do **snapshot de câmbio** gravado
  na criação da transação (podem estar vazios se não houve cotação no momento).
- **Formato:** _content negotiation_ via parâmetro `?format=csv` (padrão), `?format=xlsx` ou `?format=pdf`.
  Resposta com `Content-Disposition: attachment; filename="relatorio.csv"` (ou `.xlsx`/`.pdf`) para download.

## Decisão central — a FORMATAÇÃO mora no adapter, o domínio não sabe o que é um arquivo

O caso de uso devolve **linhas de domínio tipadas** (`core/domain/ReportRow`: `LocalDate`, `BigDecimal`,
enums) e **nada mais**. Quem transforma essas linhas em bytes de CSV/Excel/PDF são três _writers_ na camada
`adapters/in/web`. Assim:

- `core` e `application` ficam **livres de framework e de formato** — nenhum import de CSV, Apache POI, OpenPDF,
  BOM, `HttpServletResponse` ou `;`. O teste de arquitetura (`architecture/HexagonalArchitectureTest`,
  ArchUnit) continua **verde**.
- "Quais colunas, em que ordem e com quais valores derivados" é **negócio** → fica no caso de uso.
  "Como isso vira texto para o Excel pt-BR / planilha nativa / PDF" é **detalhe de entrega** → fica no adapter.
- Cada peça é testável isoladamente: o caso de uso com Mockito, os _writers_ sem Spring.

```
core/domain/ReportRow                 (JDK puro: LocalDate, BigDecimal, enums)
core/ports/in/GetTransactionReportUseCase
        │  List<ReportRow> generate(userId, from, to)
application/usecase/GetTransactionReportUseCaseImpl
        │  resolve período (mês atual se nulo), valida, mapeia Transaction → ReportRow
        │  (data no fuso; USD/EUR lidos do snapshot de câmbio)
adapters/out/persistence  → findActiveForReport (reusa a Specification de período, sem paginação)
adapters/in/web
        ├─ TransactionReportController  (rota, content negotiation, Content-Disposition)
        ├─ TransactionCsvWriter         (BOM, ';', decimal vírgula, escaping)   ← formato
        ├─ TransactionExcelWriter       (Apache POI, células tipadas)           ← formato
        ├─ TransactionPdfWriter         (OpenPDF, tabela paisagem A4)           ← formato
        ├─ ReportLabels                 (rótulos pt-BR dos enums)
        └─ ReportFormat                 (csv/xlsx/pdf: filename + content type)
```

## CSV: UTF-8 **com BOM**, separador `;` e decimal com vírgula

O critério explícito do PO é _"abrir no Excel pt-BR sem quebrar acento"_. As decisões do `TransactionCsvWriter`:

- **UTF-8 com BOM** (`EF BB BF` no início do arquivo). O BOM é invisível, mas sem ele o Excel pt-BR
  interpreta o arquivo como Latin-1 e os acentos quebram. Com o BOM, o Excel reconhece UTF-8.
- **Separador `;`** (não `,`): o Excel configurado em pt-BR usa `;` como separador de lista.
- **Decimal com vírgula** (`1500,00`), via `Locale pt-BR`, **sem separador de milhar** — o Excel pt-BR lê
  como número de verdade.
- **Fim de linha CRLF** (`\r\n`), compatível com Windows/Excel.
- **Escaping (RFC-4180):** se um campo contém `;`, aspas ou quebra de linha, ele é envolvido em aspas duplas
  e as aspas internas são **dobradas** (`"` → `""`). Descrições "difíceis" não desalinham as colunas.

## Excel (bônus): `.xlsx` nativo com Apache POI e **células tipadas**

O `TransactionExcelWriter` usa **Apache POI** (`poi-ooxml`) para gerar um `.xlsx` real:

- As colunas de **data** e **moeda** são células com **tipo nativo** (data/número) + formato de exibição
  por estilo (`dd/mm/yyyy`, `#,##0.00`), **não texto**. Isso permite que o usuário **some e filtre** os
  valores direto no arquivo final.
- Cabeçalho em **negrito** e **congelado** (`freeze pane`); colunas **auto-dimensionadas** para legibilidade.
- Toda a dependência de `org.apache.poi` fica **confinada a esta classe** — não vaza para core/application
  (nem para o resto do `adapters`). A dependência é declarada no `pom.xml` com a _property_ `poi.version`.

## PDF: documento de apresentação com OpenPDF

O `TransactionPdfWriter` usa **OpenPDF** (fork LGPL/MPL do iText 4 — sem o AGPL do iText 7) para gerar um PDF
em **tabela paisagem A4**:

- Cabeçalho destacado (fundo escuro, texto branco) e **congelado** como linha de cabeçalho repetida; valores
  monetários alinhados à direita. Datas em `dd/MM/yyyy`, moedas em pt-BR com vírgula decimal e enums com rótulo
  pt-BR — tudo como **texto** (PDF é formato de apresentação, não editável/somável como o Excel).
- Fontes base com encoding **Cp1252 (WINANSI)**, que cobre os acentos do português.
- Toda a dependência de `com.lowagie.text` fica **confinada a esta classe** (ArchUnit verde). _Property_
  `openpdf.version` no `pom.xml`.

> **Por que não "PDF via POI"?** O Apache POI gera formatos Office (Excel/Word/PowerPoint) — **não gera PDF**.
> O requisito pedia **POI para o Excel** (atendido); PDF exige um motor de PDF, daí o OpenPDF.

## Reuso e validação (sem reinventar nada)

- A busca reaproveita a `TransactionSpecifications.matching(query, zoneId)` já existente (filtro por
  usuário/período, exclusão de _soft deleted_), agora **sem `Pageable`** (o relatório exporta o período inteiro).
- A validação de período inválido reusa `InvalidTransactionFilterException` → já vira **400** no
  `GlobalExceptionHandler`. `userId` ausente (param obrigatório) e datas malformadas também já caem em **400**.
  **Nenhum handler novo** foi criado.

## Testes

- `TransactionCsvWriterTest` — BOM, cabeçalho pt-BR, separador `;`, CRLF, decimal com vírgula, escaping de
  `;`/aspas/quebra de linha, acentos preservados, motivo traduzido.
- `TransactionExcelWriterTest` — abre o `.xlsx` gerado de volta com POI e prova que data/moeda são **células
  tipadas** (somáveis) e que célula sem cotação fica em branco.
- `TransactionPdfWriterTest` — valida a assinatura `%PDF-` e o marcador `%%EOF` (PDF bem formado), inclusive
  para relatório vazio.
- `GetTransactionReportUseCaseImplTest` — mapeamento `Transaction → ReportRow` (data no fuso, USD/EUR do
  snapshot, motivo só em REJECTED), mês atual por padrão, `from > to` → 400.
- `TransactionPersistenceAdapterTest` — `findActiveForReport` traz APPROVED+REJECTED do usuário no período,
  exclui _soft deleted_/outro usuário/fora do período, em ordem cronológica.
- `TransactionReportControllerTest` (`@WebMvcTest`) — CSV vs XLSX vs PDF (content type, `Content-Disposition`,
  BOM/assinatura `%PDF`), `userId` ausente → 400, `format` inválido → 400.
- `TransactionReportIntegrationTest` (`@SpringBootTest` + MockMvc, H2) — ponta a ponta: cria transações,
  baixa CSV (BOM, cabeçalho, escaping, linhas APROVADA/REJEITADA), XLSX (assinatura `PK`) e PDF (assinatura
  `%PDF`); prova que a rota literal `/report` **não** colide com `GET /{id}`.

## Verificação no app real (Docker)

Validado com o stack via Docker Compose (PostgreSQL 17.11 + MS2): Flyway aplicou V1/V2/V3, e
`GET /api/v1/transactions/report` respondeu **200** em `csv` (BOM + `;` + escaping + acentos), `xlsx`
(células numéricas/data somáveis) e `pdf` (`%PDF-1.5`…`%%EOF`), com os `Content-Disposition` corretos;
`userId` ausente / `format` inválido / período invertido → **400**.
