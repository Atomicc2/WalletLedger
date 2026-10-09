package com.API.walletLedger.repository;

import com.API.walletLedger.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de Slice JPA do LedgerEntryRepository (@DataJpaTest + H2).
 *
 * O que é um "slice"?
 * → Sobe apenas a camada de persistência: DataSource, Hibernate,
 *   repositórios e transações. NÃO sobe web, security nem controllers.
 *
 * Por que desabilitar o Flyway e sobrescrever ddl-auto/dialect?
 * → As migrações V1–V3 são SQL específico de PostgreSQL e quebrariam no H2.
 * → O application.properties principal força `ddl-auto=none` e
 *   `PostgreSQLDialect`, que valeriam também aqui — sem schema gerado e com
 *   SQL gerado errado para o H2. No slice, o schema é criado pelo Hibernate
 *   (ddl-auto=create-drop) a partir das entidades, portável e descartável.
 *
 * Por que validar saldo com isEqualByComparingTo?
 * → O SUM() do banco pode retornar escalas diferentes (750 vs 750.00);
 *   isEqualByComparingTo compara o valor numérico ignorando a escala.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class LedgerEntryRepositoryTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // ---------- Helpers de Seed ----------

    private User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userRepository.save(user);
    }

    private Account newAccount(User user) {
        Account account = new Account();
        account.setUser(user);
        account.setAccountType(AccountType.USER);
        return accountRepository.save(account);
    }

    private Transaction newTransaction(BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(UUID.randomUUID().toString());
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    private void newEntry(Transaction transaction, Account account, EntryType type, String amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setAccount(account);
        entry.setEntryType(type);
        entry.setAmount(new BigDecimal(amount));
        ledgerEntryRepository.save(entry);
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("getBalanceByAccountId: deve retornar saldo zero quando conta não tem entradas")
    void getBalanceByAccountId_deveRetornarSaldoZero_quandoSemEntradas() {
        // ARRANGE — conta criada sem nenhum lançamento contábil
        User user = newUser("sem-entradas@test.com");
        Account account = newAccount(user);

        // ACT
        BigDecimal balance = ledgerEntryRepository.getBalanceByAccountId(account.getId());

        // ASSERT
        assertThat(balance).isNotNull();
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getBalanceByAccountId: deve calcular saldo líquido corretamente com débitos e créditos")
    void getBalanceByAccountId_deveCalcularSaldoCorreto_comDebitosECreditos() {
        // ARRANGE
        User user = newUser("saldo@test.com");
        Account accountA = newAccount(user);   // conta sob teste
        Account accountB = newAccount(user);   // conta de controle (não deve influenciar A)

        Transaction tx1 = newTransaction(new BigDecimal("1000.00"));
        Transaction tx2 = newTransaction(new BigDecimal("250.00"));
        Transaction tx3 = newTransaction(new BigDecimal("500.00"));

        // Conta A: crédito 1000, débito 250 → saldo esperado 750
        newEntry(tx1, accountA, EntryType.CREDIT, "1000.00");
        newEntry(tx2, accountA, EntryType.DEBIT, "250.00");
        // Conta B: crédito 500 → não pode vazar para o saldo de A
        newEntry(tx3, accountB, EntryType.CREDIT, "500.00");

        // ACT
        BigDecimal balanceA = ledgerEntryRepository.getBalanceByAccountId(accountA.getId());
        BigDecimal balanceB = ledgerEntryRepository.getBalanceByAccountId(accountB.getId());

        // ASSERT
        assertThat(balanceA).isEqualByComparingTo("750.00");
        assertThat(balanceB).isEqualByComparingTo("500.00");
    }
}
