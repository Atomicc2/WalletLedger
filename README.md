# 💰 WalletLedger

> Carteira digital moderna com ledger de partidas dobradas (dupla entrada contábil), controle de concorrência e idempotência.

---

## 📌 Sobre o Projeto

O **WalletLedger** é um sistema financeiro focado em integridade contábil e alta confiabilidade. Diferente de sistemas ingênuos que apenas somam ou subtraem saldos em uma coluna, o WalletLedger implementa um **ledger imutável de dupla entrada (double-entry bookkeeping)**:
- Todo movimento financeiro possui uma **origem** (débito) e um **destino** (crédito).
- A soma de débitos e créditos de qualquer transação é rigorosamente **ZERO**.
- O saldo de uma conta é calculado com base no histórico contábil de lançamentos.

---

## 🚀 Tecnologias

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.4.0
- **Segurança:** Spring Security (BCrypt / JWT)
- **Persistência:** Spring Data JPA / Hibernate
- **Banco de Dados:** PostgreSQL 16
- **Versionamento de Banco:** Flyway
- **Produtividade:** Lombok & Jakarta Validation
- **Containerização:** Docker & Docker Compose
- **Frontend (Planejado):** Angular

---

## 🏛️ Estrutura Atual do Banco de Dados

- **`users`**: Cadastro de usuários com senhas criptografadas via BCrypt.
- **`accounts`**: Carteiras dos usuários e contas especiais de liquidação do sistema.
- **`transactions`**: Registro do evento de transferência/pagamento com chave única de idempotência.
- **`ledger_entries`**: Entradas contábeis imutáveis (`DEBIT` / `CREDIT`) ligadas a cada transação.

---

## 🛠️ Como Rodar o Projeto Localmente

### 1. Pré-requisitos
- [Docker](https://www.docker.com/) e Docker Compose instalados
- [JDK 21](https://adoptium.net/) instalado
- [Maven](https://maven.apache.org/) (ou use o `./mvnw` embutido)

### 2. Subir o Banco de Dados (PostgreSQL)
Na raiz do projeto, inicie o container do banco:
```bash
docker compose up -d postgres
```

### 3. Iniciar o Backend
Navegue até a pasta `backend` e execute:
```bash
./mvnw spring-boot:run
```
A API estará acessível em `http://localhost:8080`.

---

## 🗺️ Roadmap de Desenvolvimento

- [x] Configuração inicial do projeto (Spring Boot 3 + Java 21)
- [x] Migrations com Flyway (`users`, `accounts`, `transactions`, `ledger_entries`, `system_account`)
- [x] Mapeamento das entidades JPA e Enums
- [x] Spring Data Repositories com consultas agregadas de saldo (JPQL)
- [x] Serviço de cadastro de usuário com abertura automática de carteira (`UserService`)
- [ ] Endpoint de cadastro (`UserController`)
- [ ] Autenticação e autorização via JWT (Spring Security)
- [ ] Mecanismo de criação de transações com chave de idempotência
- [ ] Controle de concorrência pessimista/otimista no banco
- [ ] Processamento assíncrono via webhook simulado
- [ ] Frontend em Angular com dashboard e extrato
