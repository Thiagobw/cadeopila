# transaction-service (MS2)

Serviço de transações financeiras (depósito, retirada, transferência, compra) em **arquitetura hexagonal**
(Java 25 + Spring Boot 4.1.2 + PostgreSQL 17). Cada transação termina `APPROVED` ou `REJECTED` — a rejeitada
também é gravada, com o motivo.

## Saldo e limites: a Mock é somente leitura

A cada transação, o MS2 consulta a **API de contas (MockAPI)** para obter o **saldo inicial** e os **limites**
(`dailyLimit`, `transactionLimit`). **O saldo não é gravado de volta na Mock nem persistido no banco do MS2.**

Motivo: a Mock não se atualiza sozinha quando aprovamos uma transação, e manter uma cópia do saldo no nosso banco
criaria **duas fontes do mesmo dado**, uma delas fadada a ficar desatualizada. Então:

- **Saldo atual** = saldo inicial da Mock **+** histórico de transações **aprovadas** do MS2
  (depósitos somam, saídas — saque/transferência/compra — subtraem), ignorando as excluídas.
- **Limite diário** = soma das **saídas aprovadas do dia** (dia no fuso `app.time-zone`, padrão `America/Sao_Paulo`).
- A política de aprovação (`TransactionPolicy`) usa esse saldo atual derivado, nunca o saldo bruto da Mock.
- `account.balanceAfter` na resposta do POST é **derivado** (saldo atual ± valor); não é persistido.

## Resiliência da integração

- O cliente HTTP da Mock tem **timeout de conexão (2s) e de leitura (3s)** — a requisição nunca fica travada.
- **Mock fora do ar** (timeout, erro de rede, 5xx) → a transação é gravada como **`REJECTED`** com motivo
  `ACCOUNT_SERVICE_UNAVAILABLE` e a API responde **201** (dois estados apenas e registro auditável).
- **Conta inexistente** → `REJECTED` com `ACCOUNT_NOT_FOUND`.
- O câmbio (BrasilAPI) é **tolerante a falha**: se indisponível, a transação é criada mesmo assim e o bloco
  `exchange` sinaliza `available: false`.

## Limitações conhecidas (sem correção nesta entrega)

- **Sem transação distribuída** entre a Mock e o banco. Como a Mock é somente leitura, não há escrita externa a
  compensar; uma falha de persistência apenas resulta em 500, sem efeito colateral na Mock.
- **Leitura concorrente do saldo**: duas transações simultâneas do mesmo usuário podem derivar o **mesmo saldo**
  (antes de qualquer uma persistir) e ambas serem aprovadas, ultrapassando o saldo/limite. Não há trava nesta entrega.

## Build e testes

```bash
./mvnw -B -ntp -pl transaction-service -am verify
```

Testes unitários (domínio/casos de uso), de fatia (`@WebMvcTest`, `@DataJpaTest` com H2 em modo PostgreSQL),
de integração (`@SpringBootTest` com as portas externas mockadas) e de arquitetura (ArchUnit). As migrations Flyway
rodam apenas contra o PostgreSQL real (via Docker Compose); os testes usam H2 com `ddl-auto=create-drop`.
Relatório de cobertura em `target/site/jacoco/index.html`.
