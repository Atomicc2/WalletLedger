# 💰 WalletLedger

> Carteira digital moderna com **ledger de partidas dobradas (dupla entrada contábil)**, concorrência pessimista, idempotência e liquidação assíncrona (webhook + fila).

![Java](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-5%20migrations-CA4721?logo=flyway&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT%20(JJWT)-000000?logo=jsonwebtokens&logoColor=white)
![Tests](https://img.shields.io/badge/testes-61%20passando-brightgreen)
![License](https://img.shields.io/badge/licença-MIT-blue)

---

## 📌 Sobre o Projeto

O **WalletLedger** é um sistema financeiro focado em **integridade contábil e alta confiabilidade**. Diferente de sistemas ingênuos que apenas somam ou subtraem saldos em uma coluna (`UPDATE accounts SET balance = balance + X`), o WalletLedger implementa um **ledger imutável de dupla entrada (double-entry bookkeeping)**:

- Todo movimento financeiro possui uma **origem** (débito) e um **destino** (crédito).
- A soma algébrica de débitos e créditos de qualquer transação é rigorosamente **ZERO**.
- O saldo de uma conta é **calculado** a partir do histórico de lançamentos (`SUM`), nunca "mutado".

É um projeto de estudo com filosofia **IA-First**: o agente de IA implementa, o desenvolvedor atua como tech lead revisando cada decisão arquitetural. A fonte única da verdade (estado, roadmap e padrões) está no **[`AGENTS.md`](./AGENTS.md)**.

---

## ✅ Funcionalidades

- [x] **Cadastro de usuário** com hash **BCrypt** e abertura automática de carteira
- [x] **Login via JWT** (HS256, expiração de 24h) — API stateless
- [x] **Depósito síncrono** (`201`) — partida dupla: `DEBIT SYSTEM` + `CREDIT usuário`
- [x] **Depósito assíncrono** (`202`) — transação nasce `PENDING` sem partidas (saldo só muda na liquidação)
- [x] **Transferência** entre usuários com **lock pessimista** (`SELECT ... FOR UPDATE`) e validação de saldo
- [x] **Estorno (reverse)** com lançamentos **compensatórios invertidos** — a transação original vira `REVERSED` e é preservada para auditoria
- [x] **Saldo e extrato** em tempo real (queries agregadas JPQL)
- [x] **Webhook de liquidação** autenticado por header `X-Webhook-Secret` (comparação em tempo constante)
- [x] **Worker/fila** que liquida transações `PENDING` antigas (`@Scheduled` + carência configurável)
- [x] **Idempotência ponta a ponta** via `idempotency_key` (sem gasto duplo por retry/clique duplo)
- [x] **Tratamento global de erros** no padrão **RFC 7807** (`application/problem+json`)
- [x] **Autorização de dono-de-conta** (`OwnershipGuard`) — bloqueio de IDOR em todas as rotas
- [x] **Testes automatizados** (unitários, slice JPA e integração) — **61 testes**
- [ ] **Frontend React** (Fase 4 — em andamento)

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologias |
|---|---|
| **Linguagem** | Java 21 (LTS) |
| **Backend** | Spring Boot 3.4.0 · Spring Security · Spring Data JPA / Hibernate 6.6+ |
| **Banco de dados** | PostgreSQL 16 (Docker Compose) |
| **Migrações** | Flyway (5 migrações versionadas) |
| **Autenticação** | JWT (JJWT 0.12.6) · BCrypt |
| **Qualidade** | Lombok · Jakarta Bean Validation · JUnit 5 · Mockito · H2 (testes) |
| **Build** | Maven (usar `mvn` do sistema — o wrapper `./mvnw` está incompleto) |
| **Frontend** | React (Vite + TypeScript + React Router + Tailwind) — *Fase 4* |
| **Deploy planejado** | Backend: Render/Railway · Banco: Neon/Supabase · Frontend: Vercel |

---

## 🏛️ Arquitetura & Decisões de Domínio

1. **Dupla entrada (double-entry):** cada transação gera no mínimo duas `LedgerEntry` — uma `DEBIT` e uma `CRÉDITO` — com **invariante** `SUM(créditos) − SUM(débitos) == 0`.
2. **Idempotência:** toda criação de transação carrega um `idempotency_key` único; retries com a mesma chave retornam a transação original.
3. **Concorrência:** bloqueio **pessimista** (`@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`) nas contas envolvidas, impedindo *race conditions* e gasto duplo.
4. **Ciclo de vida da transação:** máquina de estados `PENDING → COMPLETED / FAILED / REVERSED`.
5. **Liquidação assíncrona:** um **único** método `LedgerService.settle(transactionId, approved)` é compartilhado entre o **webhook** do provedor e o **worker** da fila — a lógica de liquidação existe uma vez só, e é idempotente por estado (`PENDING`).
6. **Conta mestre `SYSTEM`:** conta com ID fixo (`...0001`) que "paga" os depósitos externos, fechando a partida dupla.
7. **Segurança na borda:** a autorização de dono-de-conta vive no **controller** (não no `LedgerService`), porque o `settle()` também é chamado por webhook/worker, que não têm usuário logado.

---

## 📂 Estrutura do Projeto

```
WalletLedger/
├── AGENTS.md                  # Fonte única da verdade (estado, roadmap, padrões)
├── LICENSE.md                 # Licença MIT
├── docker-compose.yml         # PostgreSQL 16 + pgAdmin
└── backend/
    ├── pom.xml
    └── src/
        ├── main/java/com/API/walletLedger/
        │   ├── config/        # SecurityConfig, JwtAuthenticationFilter, OwnershipGuard
        │   ├── controller/    # Users, Auth, Transactions, Accounts, Webhooks
        │   ├── domain/        # User, Account, Transaction, LedgerEntry + enums
        │   ├── dto/           # Requests/Responses (records)
        │   ├── exception/     # GlobalExceptionHandler (RFC 7807)
        │   ├── repository/    # Spring Data + queries JPQL agregadas
        │   ├── service/       # LedgerService, JwtService, AuthService, UserService
        │   └── worker/        # PendingSettlementWorker (@Scheduled)
        ├── main/resources/
        │   ├── application.properties
        │   └── db/migration/  # V1..V5 (Flyway)
        └── test/java/         # 61 testes (unit, slice JPA, integração)
```

---

## 🚀 Como Rodar o Projeto Localmente

### 1. Pré-requisitos

- [Docker](https://www.docker.com/) + Docker Compose
- [JDK 21](https://adoptium.net/)
- [Maven](https://maven.apache.org/) 3.9+ (`mvn` do sistema)
- [Node.js](https://nodejs.org/) 20+ e npm *(para o frontend — Fase 4)*

### 2. Subir o banco de dados

```bash
docker compose up -d
```

Cria o **PostgreSQL 16** na porta `5432` (banco `walletledger`, usuário `postgres`/`1234`). As migrações do Flyway rodam **automaticamente** na primeira subida do backend.

> 🖥️ Opcional: **pgAdmin** sobe junto em `http://localhost:5050` (`admin@example.com` / `adminpass`).

### 3. Iniciar o backend

```bash
cd backend
mvn spring-boot:run
```

A API fica em `http://localhost:8080`.

### 4. Rodar os testes

```bash
cd backend
mvn test          # 61 testes (unit + slice JPA + integração)
```

### 5. Frontend (Fase 4 — em construção)

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173
```

### ⚙️ Configurações principais (`backend/src/main/resources/application.properties`)

| Propriedade | Valor (dev) | Descrição |
|---|---|---|
| `spring.datasource.*` | `localhost:5432/walletledger` | Conexão com o PostgreSQL do compose |
| `jwt.secret` | chave Base64 (256 bits) | Assinatura HMAC-SHA256 do token |
| `jwt.expiration-ms` | `86400000` | Expiração do token (24h) |
| `wallet.webhook.secret` | `dev-webhook-secret-local` | Esperado no header `X-Webhook-Secret` |
| `wallet.worker.poll-interval-ms` | `10000` | Intervalo do worker (espera 10s após cada execução) |
| `wallet.worker.settle-after-seconds` | `30` | Carência mínima do `PENDING` antes de liquidar |

> ⚠️ Segredos de desenvolvimento estão versionados por praticidade. **Em produção** eles vêm de variáveis de ambiente/secrets manager, e o webhook idealmente usa assinatura HMAC-SHA256 do corpo.

---

## 🔌 API Reference

Base URL: `http://localhost:8080`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/api/users` | Público | Cadastro (cria usuário + carteira padrão) |
| `POST` | `/api/auth/login` | Público | Login → `accessToken` (Bearer) |
| `POST` | `/api/transactions/deposit` | JWT + dono | Depósito síncrono → `201` |
| `POST` | `/api/transactions/deposit-async` | JWT + dono | Depósito assíncrono → `202` (`PENDING`) |
| `POST` | `/api/transactions/transfer` | JWT + origem própria | Transferência entre contas → `201` |
| `POST` | `/api/transactions/reverse` | JWT + participante | Estorno da transação → `201` |
| `GET` | `/api/accounts/{id}/balance` | JWT + dono | Saldo atual da conta |
| `GET` | `/api/accounts/{id}/statement` | JWT + dono | Extrato de lançamentos |
| `POST` | `/api/webhooks/payment` | `X-Webhook-Secret` | Liquidação (`APPROVED`/`REJECTED`) |

### Exemplos

**Login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@wallet.test","password":"senha123"}'
# → { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 86400 }
```

**Depósito com idempotência:**
```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"11111111-2222-3333-4444-555555555555",
       "targetAccountId":"<uuid-da-conta>","amount":100.00}'
```

### Formato de erro (RFC 7807)

Todas as falhas respondem `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Saldo insuficiente para a transferência.",
  "errors": { "campo": "mensagem" }
}
```

**Semântica de status:** `401` = não se identificou (sem token) · `403` = identificou, mas **não pode** (não é dono) · `409` = conflito de estado (ex.: estornar `PENDING`).

---

## 🧪 Testes

```bash
cd backend && mvn test
```

**Estratégia — pirâmide de testes:**

| Camada | Ferramenta | Exemplos |
|---|---|---|
| Unitários | JUnit 5 + Mockito | `LedgerServiceTest` (16) · `JwtServiceTest` (6) · `PendingSettlementWorkerTest` (3) |
| Slice JPA | `@DataJpaTest` + H2 | `LedgerEntryRepositoryTest` (2) — saldo agregado |
| Integração | `@SpringBootTest` + MockMvc | transações, estorno, webhook, worker, ownership e exceções |

**Suíte atual: `Tests run: 61, Failures: 0, Errors: 0` ✅**

---

## 🔐 Segurança

**Implementado:**
- Senhas com **BCrypt** (nunca em texto puro) e API **stateless** (CSRF desabilitado por ser API com Bearer).
- **JWT** com assinatura HMAC-SHA256 e validação de expiração.
- **Webhook** com secret em header comparado em **tempo constante** (`MessageDigest.isEqual` — proteção contra *timing attack*).
- **Autorização de dono-de-conta** (`OwnershipGuard`): corrigido o **IDOR** em todas as rotas — saldo/extrato só do dono, transferência exige origem própria, estorno exige participação na transação.

**Dívidas conhecidas (futuro):** rate limiting no login, papéis/RBAC, secrets via variáveis de ambiente + assinatura HMAC do webhook, CORS (entra na Fase 4).

---

## 🗺️ Roadmap

- [x] **Fase 1** — Usuários, segurança e JWT
- [x] **Fase 2** — Motor contábil (dupla entrada, idempotência, lock pessimista)
- [x] **Fase 2.5** — Testes automatizados (pirâmide: unit → slice → integração)
- [x] **Fase 3** — Assíncrono e resiliência (depósito async, webhook, fila/worker, estorno, RFC 7807)
- [x] **Passo 3.4** — Revisão de segurança e autorização de dono-de-conta
- [ ] **Fase 4** — Frontend React (Vite + TypeScript): setup, login/cadastro, dashboard, extrato

---

## 🤝 Contribuição

Os commits seguem o padrão **[Conventional Commits](https://www.conventionalcommits.org/)** em português:

```
<tipo>(<escopo>): <título curto no imperativo>

- <detalhe 1>
- <detalhe 2>
```

**Tipos:** `feat` · `fix` · `docs` · `refactor` · `test` · `chore`
**Escopos comuns:** `auth` · `user` · `account` · `ledger` · `db` · `security`

---

## 📄 Licença

Este projeto está sob a licença **MIT** — veja [`LICENSE.md`](./LICENSE.md).
