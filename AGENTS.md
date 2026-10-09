# WalletLedger — Guia de Instruções para Agentes de IA

Este documento serve como a **fonte única da verdade (Single Source of Truth)** para qualquer modelo ou agente de IA que atuar neste repositório. Ele define o fluxo de trabalho, a filosofia de engenharia, a stack técnica, o estado atual do desenvolvimento, o padrão de commits e o roadmap futuro.

---

## 🧭 Filosofia de Trabalho: IA-First com Foco em Aprendizado

O objetivo deste projeto é duplo: **desenvolver uma carteira digital com padrões de produção de mercado** e **proporcionar o aprendizado aprofundado do desenvolvedor**. O fluxo deve simular o ambiente corporativo moderno orientado a IA (AI-First): o agente implementa o código, enquanto o desenvolvedor atua como o engenheiro sênior/tech lead que revisa, compreende e valida cada decisão arquitetural.

### Regras Mandatórias para o Agente:

1. **O Agente Implementa o Código:** O agente pode e deve criar/editar arquivos e implementar as soluções diretamente.
2. **Implementação Incremental em Pequenos Blocos:** **NUNCA** implemente múltiplos módulos, serviços complexos ou refatorações gigantescas em uma única resposta. Faça uma etapa por vez (ex.: primeiro o Controller, depois a Segurança, depois o Teste).
3. **Explicação Clara e Concisa do "Porquê":** A cada alteração ou novo arquivo gerado, o agente deve fornecer uma explicação didática dos conceitos-chave aplicados (ex.: por que desabilitar CSRF em APIs stateless, por que usar `SecurityFilterChain`, por que locking pessimista em transferências).
4. **Revisão e Aprovação Prévia:** O agente deve apresentar o plano e as alterações de forma compreensível para que o desenvolvedor aprove antes de avançar para a próxima etapa.
5. **Atualização Contínua deste Documento:** Conforme novos módulos, tabelas ou fluxos forem concluídos, o agente deve manter as seções de *Estado Atual* e *Roadmap* deste arquivo sempre atualizadas.
6. **Padrão Rigoroso de Commits:** Sempre que uma etapa for concluída e testada, os commits devem seguir rigorosamente o padrão **Conventional Commits** com escopo e descrição detalhada no corpo (veja seção abaixo).

### Protocolo de Didática (obrigatório neste projeto)

Como o objetivo é o aprendizado do desenvolvedor, o agente deve seguir este ciclo em **toda** etapa:

1. **Antes de codar:** explicar em poucas frases o conceito/tecnologia envolvida e **o porquê** da abordagem escolhida (e as alternativas descartadas).
2. **Durante:** implementar **apenas um bloco pequeno** por vez — nunca vários módulos, refatorações amplas ou "de uma vez".
3. **Depois:** explicar o que mudou, por que funciona e quais armadilhas existem, em linguagem didática (sem jargão não explicado).
4. **Pausar:** só avançar para o próximo bloco com a aprovação explícita do desenvolvedor. Encerrar a resposta com um resumo curto do estado atual e uma pergunta objetiva do tipo *"posso seguir para X?"*.
5. **Nunca** commitar ou alterar arquivos principais sem antes apresentar o que será feito e obter o "ok" (ver seção de Permissões abaixo).

### 🔐 Permissões e Aprovações

O repositório tem regras de permissão em `.opencode/opencode.jsonc` (OpenCode v2) que **exigem aprovação (`ask`)** antes de:

- qualquer escrita em arquivo (`edit`/`write`/`patch`);
- `git add`, `git commit` e `git push`;
- qualquer outro comando de shell (ex.: `mvn`, `rm`, `mv`).

Leitura e inspeção (`read`, `grep`, `git status/diff/log/show/branch`) são liberadas.
**Importante:** ao aprovar um pedido, escolher **"Allow once"** — o **"Allow always"** grava uma liberação permanente e o comando deixa de pedir aprovação.

---

## 📦 Padrão de Commits (Conventional Commits)

Todos os commits devem seguir o padrão:
```text
<tipo>(<escopo>): <título curto no imperativo e em português>

- <detalhe 1 do que foi implementado e o motivo>
- <detalhe 2>
- <detalhe 3>
```

### Tipos Permitidos:
- **`feat`**: Nova funcionalidade ou recurso de negócio (ex.: novo endpoint, novo service).
- **`fix`**: Correção de bug ou comportamento incorreto.
- **`docs`**: Mudança em documentação (`README.md`, `AGENTS.md`, diagramas).
- **`refactor`**: Refatoração de código que não altera o comportamento externo da aplicação.
- **`test`**: Criação ou ajuste de testes automatizados.
- **`chore`**: Tarefas de manutenção, dependências no `pom.xml`, `.gitignore`, scripts de build.

### Escopos Comuns:
- `auth`: Autenticação, JWT, senhas, filtros de segurança.
- `user`: Domínio, repositórios, serviços e endpoints de usuários.
- `account`: Carteiras, saldos e contas contábeis.
- `ledger`: Motor contábil, transações, débitos/créditos, regras de dupla entrada.
- `db`: Migrações do Flyway, docker-compose, scripts de banco.

---

## 🛠️ Stack Tecnológica

- **Backend:**
  - Java 21 (LTS)
  - Spring Boot 3.4.0
  - Spring Security (Autenticação Stateless via JWT)
  - Spring Data JPA / Hibernate 6.6+
  - PostgreSQL 16 (executando via Docker Compose)
  - Flyway (Migrações versionadas de banco de dados)
  - Lombok & Jakarta Bean Validation
  - Maven
- **Frontend (Futuro):**
  - Angular (TypeScript, RxJS, Tailwind/Material)
- **Infraestrutura e Deploy Planejado:**
  - Docker & Docker Compose (Ambiente local de desenvolvimento)
  - Backend: Render ou Railway
  - Banco: Supabase ou Neon (PostgreSQL gerenciado)
  - Frontend: Vercel

---

## 🏛️ Arquitetura e Decisões de Domínio

### 1. Ledger de Partidas Dobradas (Double-Entry Bookkeeping)
- O sistema **não** altera saldos via mutações simples (`UPDATE accounts SET balance = balance + X`).
- Todo movimento financeiro gera uma `Transaction` e no mínimo duas `LedgerEntry` vinculadas:
  - Uma linha de **`DEBIT`** (origem do recurso).
  - Uma linha de **`CREDIT`** (destino do recurso).
- **Invariante Contábil:** A soma algébrica de débitos e créditos de qualquer transação deve ser estritamente zero (`SUM(credits) - SUM(debits) == 0`).
- O saldo é calculado de forma agregada no banco (`SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)`).

### 2. Idempotência
- Toda requisição de criação de transação deve carregar um header/campo `idempotency_key` (UUID/String única).
- Garante proteção contra cliques duplos, retentativas de rede ou falhas no cliente.

### 3. Concorrência e Integridade
- Uso de bloqueio no banco de dados (pessimistic locking / `SELECT FOR UPDATE`) para evitar condições de corrida (Race Conditions) ao verificar saldos e debitar contas concorrentemente.

### 4. Ciclo de Vida da Transação
- Máquina de estados: `PENDING` → `COMPLETED` / `FAILED` / `REVERSED`.

---

## 📊 Estado Atual do Projeto

### O que já está implementado e funcional:

- [x] **Ambiente & Banco:**
  - `docker-compose.yml` configurado com PostgreSQL 16 na porta `5432`.
  - Migrações Flyway:
    - `V1__create_users_and_accounts_tables.sql` (tabelas `users` e `accounts`).
    - `V2__create_transactions_and_ledger_entries_tables.sql` (tabelas `transactions` e `ledger_entries`).
    - `V3__create_system_account.sql` (conta mestre do sistema com ID fixo `00000000-0000-0000-0000-000000000001` para suportar depósitos externos).
- [x] **Domínio (`domain`):**
  - Entidades JPA: `User`, `Account`, `Transaction`, `LedgerEntry`.
  - Enums: `AccountType` (`USER`, `SYSTEM`), `AccountStatus` (`ACTIVE`, `BLOCKED`), `TransactionStatus` (`PENDING`, `COMPLETED`, `FAILED`, `REVERSED`), `EntryType` (`DEBIT`, `CREDIT`).
- [x] **Repositórios (`repository`):**
  - `UserRepository`, `AccountRepository`, `TransactionRepository`, `LedgerEntryRepository` (com consulta JPQL agregada de saldo `getBalanceByAccountId`).
- [x] **Regra de Cadastro de Usuário:**
  - DTOs: `UserRegistrationRequest` e `UserResponse`.
  - Configuração de Bean: `SecurityBeansConfig` provendo `BCryptPasswordEncoder`.
  - `UserService`: método `@Transactional register(UserRegistrationRequest)` validando e-mail único, aplicando hash BCrypt na senha, salvando usuário e criando automaticamente uma `Account` padrão para ele.
- [x] **Endpoints e Camada Web:**
  - `UserController`: endpoint `POST /api/users` retornando `201 Created` e validando campos com `@Valid`.
  - `SecurityConfig`: liberação do endpoint de registro público e configuração stateless de sessão HTTP.
  - Teste ponta a ponta executado e validado com persistência real no PostgreSQL.
- [x] **Autenticação & Segurança JWT:**
  - Dependências oficiais JJWT 0.12.6 configuradas no `pom.xml`.
  - Propriedades de chave HMAC-SHA256 (256 bits) e tempo de expiração em `application.properties`.
  - `JwtService`: geração de token assinado, extração segura de claims e validação de expiração.
  - `UserDetailsServiceImpl`: integração de usuários com `UserDetailsService` do Spring Security.
  - `JwtAuthenticationFilter`: interceptação de headers `Authorization: Bearer <token>` e injeção de `SecurityContext`.
  - `AuthController` e `AuthService`: endpoint `POST /api/auth/login` retornando `accessToken`, `tokenType: Bearer` e `expiresIn`.
- [x] **Motor Contábil do Ledger (Fase 2):**
  - DTOs do core financeiro: `DepositRequest`, `TransferRequest`, `TransactionResponse`, `LedgerEntryResponse` e `BalanceResponse`.
  - Concorrência pessimista: `AccountRepository.findByIdForUpdate` usando `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`).
  - `LedgerService`:
    - Operação de depósito atômico com débito na conta `SYSTEM` e crédito na conta do usuário.
    - Operação de transferência atômica com validação de saldo no PostgreSQL e bloqueio pessimista de concorrência.
    - Idempotência ponta a ponta via `idempotency_key`.
    - Garantia da invariante de dupla entrada contábil (soma algébrica zero).
  - Camada Web:
    - `TransactionController`: `POST /api/transactions/deposit` e `POST /api/transactions/transfer`.
    - `AccountController`: `GET /api/accounts/{id}/balance` e `GET /api/accounts/{id}/statement`.
  - Testes ponta a ponta validados com emissão de token JWT, depósitos, transferências, proteção contra gasto duplo e idempotência.
- [x] **Governança:**
  - `.gitignore` robusto cobrindo builds, Maven, IDEs e dependências futuras.
  - `README.md` documentado.
  - `AGENTS.md` atualizado com padrões IA-First e Conventional Commits.
  - `.opencode/opencode.jsonc` com regras de permissão (`ask` para escrita/commit/push) — ver seção *Permissões e Aprovações*.
  - ⚠️ **Lições do incidente de 2026-10-08:** nunca rodar duas sessões/agentes no mesmo diretório ao mesmo tempo — elas sobrescrevem arquivos uma da outra (um commit chegou a ser gravado com conteúdo de outra sessão). Para conferir quem está ativo: `ps aux | grep -iE "opencode|antigravity"`. O wrapper Maven `./mvnw` está quebrado (falta `backend/.mvn/wrapper/`); usar `mvn` do sistema.
- [x] **Testes Automatizados (Fase 2.5 — Concluída):**
  - Bloco 1 ✅ — `LedgerServiceTest`: 5 testes unitários com Mockito passando (`Tests run: 5, Failures: 0`).
    - `deposit_deveCriarTransacaoEDuasEntradasContabeis`
    - `deposit_deveRetornarTransacaoExistente_quandoIdempotencyKeyDuplicada`
    - `transfer_deveLancarExcecao_quandoSaldoInsuficiente`
    - `transfer_deveLancarExcecao_quandoContaOrigemIgualDestino`
    - `transfer_deveCriarDuasEntradasComMesmoValor` (verifica invariante contábil via ArgumentCaptor)
  - Bloco 2 ✅ — `JwtServiceTest`: 6 testes unitários passando (`Tests run: 6, Failures: 0`).
    - `gerarToken_deveRetornarTokenValido`
    - `extrairEmail_deveRetornarEmailCorreto`
    - `validarToken_deveRetornarTrue_quandoTokenValido`
    - `validarToken_deveRetornarFalse_quandoEmailNaoBate`
    - `validarToken_deveRetornarFalse_quandoTokenExpirado`
    - `isTokenExpired_deveRetornarFalse_quandoTokenValido`
    - Estratégia: `ReflectionTestUtils.setField()` injeta `secretKey` e `jwtExpirationMs` manualmente — **não** usar `@Value` em classe de teste sem contexto Spring (o campo fica `null` e todos os testes falham com `Decode argument cannot be null`).
    - Comportamento documentado: o JJWT lança `ExpiredJwtException` ao parsear claims de token vencido; `isTokenValid` captura `JwtException` e devolve `false`.
  - Bloco 3 ✅ — `LedgerEntryRepositoryTest`: 2 testes de Slice JPA passando (`Tests run: 2, Failures: 0`).
    - `getBalanceByAccountId_deveRetornarSaldoZero_quandoSemEntradas`
    - `getBalanceByAccountId_deveCalcularSaldoCorreto_comDebitosECreditos` (inclui conta de controle para provar o filtro por conta)
    - Estratégia: `@DataJpaTest` + H2. Desabilitar o Flyway (migrações são SQL de PostgreSQL) e sobrescrever `ddl-auto=create-drop` e `H2Dialect`, pois o `application.properties` principal força `none`/`PostgreSQLDialect`.
  - Bloco 4 ✅ — `TransactionControllerIntegrationTest`: 4 testes de integração passando (`Tests run: 4, Failures: 0`).
    - `deposit_deveRetornar401_quandoSemToken`
    - `deposit_deveRetornar201_quandoTokenValido`
    - `login_deveRetornar200EToken_quandoCredenciaisValidas`
    - `login_deveRetornar401_quandoSenhaErrada`
    - Estratégia: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` (rollback por teste no Postgres real).
- ⚠️ **Suíte completa atual: `Tests run: 18, Failures: 0, Errors: 0` — `BUILD SUCCESS` (5 LedgerService + 6 JwtService + 2 LedgerEntryRepository + 4 integração + 1 contextLoads).**
- ⚠️ **O wrapper Maven está quebrado:** falta `backend/.mvn/wrapper/maven-wrapper.properties`, então `./mvnw` falha. Usar **`mvn` do sistema** (3.9.16) a partir de `backend/`.

---

## 🗺️ Roadmap de Implementação

### Fase 1: Finalização da Camada de Usuários & Segurança (Concluída)
1. **Passo 1.1:** Criar `UserController` para expor `POST /api/users` retornando `201 Created`. *(Concluído)*
2. **Passo 1.2:** Configurar o Spring Security (`SecurityConfig` com `SecurityFilterChain`) liberando rotas públicas de registro e desabilitando CSRF para API stateless. *(Concluído)*
3. **Passo 1.3:** Testar o fluxo de registro e persistência na prática. *(Concluído)*
4. **Passo 1.4:** Implementar autenticação via JWT (`JwtService`, `JwtAuthenticationFilter`, `POST /api/auth/login`). *(Concluído)*

### Fase 2: Motor Contábil do Ledger (Concluída)
1. **Passo 2.1:** DTOs de transação (`DepositRequest`, `TransferRequest`, `TransactionResponse`). *(Concluído)*
2. **Passo 2.2:** `LedgerService` com idempotência e locking pessimista (`@Lock(LockModeType.PESSIMISTIC_WRITE)`). *(Concluído)*
3. **Passo 2.3:** Criação e validação atômica de entradas contábeis (`LedgerEntry`). *(Concluído)*
4. **Passo 2.4:** `TransactionController` e `AccountController` (extrato e saldo em tempo real). *(Concluído)*

### Fase 2.5: Testes Automatizados (Concluída — 2026-10-08)

**Estratégia:** Pirâmide de testes com 3 camadas — Unitários (base), Slice JPA (meio), Integração MockMvc (topo).
**Ferramentas:** JUnit 5 + Mockito (já inclusas no `spring-boot-starter-test`) + H2 em memória para testes JPA.

#### Bloco 1 — Testes Unitários do `LedgerService` (classe: `LedgerServiceTest`)
- `deposit_deveCriarTransacaoEDuasEntradasContabeis` — cenário feliz de depósito.
- `deposit_deveRetornarTransacaoExistente_quandoIdempotencyKeyDuplicada` — idempotência.
- `transfer_deveLancarExcecao_quandoSaldoInsuficiente` — regra de saldo.
- `transfer_deveLancarExcecao_quandoContaOrigemIgualDestino` — auto-transferência proibida.
- `transfer_deveCriarDuasEntradasComSomaAlgebricaZero` — invariante contábil.

#### Bloco 2 — Testes Unitários do `JwtService` (classe: `JwtServiceTest`) — *(Concluído — commit `03fee07`)*
- `gerarToken_deveRetornarTokenValido` — token não nulo, 3 partes e subject recuperável (payload é base64url, não comparar e-mail em texto puro).
- `extrairEmail_deveRetornarEmailCorreto` — parsing de subject.
- `validarToken_deveRetornarTrue_quandoTokenValido` — validação positiva.
- `validarToken_deveRetornarFalse_quandoEmailNaoBate` — e-mail divergente.
- `validarToken_deveRetornarFalse_quandoTokenExpirado` — expiração via `jwtExpirationMs` negativo (token nasce vencido, sem `Thread.sleep`).
- `isTokenExpired_deveRetornarFalse_quandoTokenValido` — token dentro da validade.
- **Ajuste de projeto:** `JwtService.generateToken(User)` exige `user.getId()` e `user.getEmail()` não nulos (`user.getId().toString()` NPEia sem id) — os testes sempre montam User completo.

#### Bloco 3 — Testes de Slice JPA do `LedgerEntryRepository` (classe: `LedgerEntryRepositoryTest`) — *(Concluído — commit `d150c24`)*
- `getBalanceByAccountId_deveRetornarSaldoZero_quandoSemEntradas` — conta nova.
- `getBalanceByAccountId_deveCalcularSaldoCorreto_comDebitosECreditos` — query JPQL agregada.
- **O que é "slice":** sobe só a camada de persistência (DataSource + Hibernate + repositórios), sem web/security.
- **Armadilha resolvida:** o `application.properties` principal vale também nos testes; sem sobrescrever `ddl-auto` e o dialeto, o H2 fica sem schema e recebe SQL de PostgreSQL. O teste anula as duas propriedades e desliga o Flyway.

#### Bloco 4 — Testes de Integração (classe: `TransactionControllerIntegrationTest`) — *(Concluído)*
- `deposit_deveRetornar401_quandoSemToken` — proteção JWT.
- `deposit_deveRetornar201_quandoTokenValido` — fluxo completo autenticado (o controller devolve **201 Created**, não 200) e verifica as 2 partidas contábeis.
- `login_deveRetornar200EToken_quandoCredenciaisValidas` — autenticação.
- `login_deveRetornar401_quandoSenhaErrada` — credencial inválida.
- **Nomenclatura:** a classe **não** se chama `...IT` porque o Surefire (`mvn test`) só executa `*Test`/`*Tests`; sufixo `IT` é do Failsafe (`mvn verify`), que não está configurado no `pom.xml`.
- **Achado do teste (401 vs 403):** sem `AuthenticationEntryPoint`, o Spring Security responde **403** para requisição não autenticada. Corrigido no `SecurityConfig` para responder **401** (o `BadCredentialsException` do login também passa a retornar 401).
- **Estratégia:** `@SpringBootTest` (contexto completo) + `@AutoConfigureMockMvc` (HTTP simulado, sem porta de rede) + `@Transactional` (rollback por teste, sem poluir o Postgres real).

1. **Passo 2.5.1:** Testes unitários do `LedgerService` com Mockito. *(Concluído — commit `e838eda`)*
2. **Passo 2.5.2:** Testes unitários do `JwtService`. *(Concluído — commits `03fee07` / correção `8987721`)*
3. **Passo 2.5.3:** Testes de Slice JPA com `@DataJpaTest` e H2. *(Concluído — commit `d150c24`)*
4. **Passo 2.5.4:** Testes de Integração com `@SpringBootTest` + `MockMvc`. *(Concluído)*

### Fase 3: Processamento Assíncrono e Resiliência (Próxima Etapa)
1. **Passo 3.1:** Simulação de webhook/fila assíncrona para liquidação de transações pendentes.
2. **Passo 3.2:** Tratamento de estornos (`REVERSED`) através de lançamentos contábeis compensatórios.
3. **Passo 3.3:** Tratamento global de exceções (`@RestControllerAdvice` com Problem Details / RFC 7807).

### Fase 4: Frontend (Angular)
1. **Passo 4.1:** Setup do projeto Angular com roteamento e interceptor HTTP para JWT.
2. **Passo 4.2:** Telas de Login e Cadastro.
3. **Passo 4.3:** Dashboard com saldo atualizado e formulário de transferência/depósito.
4. **Passo 4.4:** Extrato de transações e detalhamento de entradas do ledger.
