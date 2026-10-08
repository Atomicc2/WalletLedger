package com.API.walletLedger.service;

import com.API.walletLedger.domain.*;
import com.API.walletLedger.dto.DepositRequest;
import com.API.walletLedger.dto.TransactionResponse;
import com.API.walletLedger.dto.TransferRequest;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import com.API.walletLedger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes Unitários do LedgerService.
 *
 * Por que @ExtendWith(MockitoExtension.class)?
 * → Inicializa os @Mock e @InjectMocks automaticamente SEM subir o Spring.
 * → Sem banco, sem contexto HTTP: apenas a lógica de negócio isolada.
 * → Roda em milissegundos.
 *
 * Por que Mockito?
 * → Substituímos os repositórios por "dublês" (mocks) que retornam
 *   valores controlados por nós. Testamos o LedgerService sem efeitos
 *   colaterais externos (banco, rede, etc.).
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    // @InjectMocks cria uma instância REAL do LedgerService e injeta os mocks acima
    @InjectMocks
    private LedgerService ledgerService;

    // --- Dados reutilizáveis nos testes (criados antes de cada @Test) ---
    private Account systemAccount;
    private Account userAccount;
    private Account anotherAccount;

    @BeforeEach
    void setUp() {
        systemAccount = new Account();
        systemAccount.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        systemAccount.setAccountType(AccountType.SYSTEM);
        systemAccount.setStatus(AccountStatus.ACTIVE);

        userAccount = new Account();
        userAccount.setId(UUID.randomUUID());
        userAccount.setAccountType(AccountType.USER);
        userAccount.setStatus(AccountStatus.ACTIVE);

        anotherAccount = new Account();
        anotherAccount.setId(UUID.randomUUID());
        anotherAccount.setAccountType(AccountType.USER);
        anotherAccount.setStatus(AccountStatus.ACTIVE);
    }

    // =========================================================================
    // TESTES DE DEPÓSITO
    // =========================================================================

    @Test
    @DisplayName("deposit: cenário feliz — deve criar Transaction e duas LedgerEntries (DEBIT + CREDIT)")
    void deposit_deveCriarTransacaoEDuasEntradasContabeis() {
        // ARRANGE — DepositRequest(idempotencyKey, targetAccountId, amount, description)
        String idempotencyKey = UUID.randomUUID().toString();
        DepositRequest request = new DepositRequest(
            idempotencyKey,
            userAccount.getId(),
            new BigDecimal("200.00"),
            "Depósito inicial"
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findById(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findByAccountType(AccountType.SYSTEM)).thenReturn(Optional.of(systemAccount));

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        savedTx.setIdempotencyKey(idempotencyKey);
        savedTx.setAmount(request.amount());
        savedTx.setStatus(TransactionStatus.COMPLETED);
        savedTx.setDescription(request.description());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);
        when(ledgerEntryRepository.findByTransactionId(savedTx.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.deposit(request);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("200.00");

        // Verifica que save() foi chamado 1x para Transaction e 2x para LedgerEntry
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("deposit: idempotência — deve retornar transação existente sem criar nova")
    void deposit_deveRetornarTransacaoExistente_quandoIdempotencyKeyDuplicada() {
        // ARRANGE
        String idempotencyKey = "chave-ja-usada-123";
        DepositRequest request = new DepositRequest(
            idempotencyKey,
            userAccount.getId(),
            new BigDecimal("100.00"),
            "Segundo disparo"
        );

        Transaction existingTx = new Transaction();
        existingTx.setId(UUID.randomUUID());
        existingTx.setIdempotencyKey(idempotencyKey);
        existingTx.setAmount(new BigDecimal("100.00"));
        existingTx.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTx));
        when(ledgerEntryRepository.findByTransactionId(existingTx.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.deposit(request);

        // ASSERT
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        // NUNCA deve chamar save() — retornou a existente sem criar nada novo
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    // =========================================================================
    // TESTES DE TRANSFERÊNCIA
    // =========================================================================

    @Test
    @DisplayName("transfer: deve lançar IllegalArgumentException quando saldo é insuficiente")
    void transfer_deveLancarExcecao_quandoSaldoInsuficiente() {
        // ARRANGE — TransferRequest(idempotencyKey, sourceAccountId, targetAccountId, amount, description)
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(
            idempotencyKey,
            userAccount.getId(),
            anotherAccount.getId(),
            new BigDecimal("500.00"), // Valor maior que o saldo
            "Transferência com saldo insuficiente"
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findById(anotherAccount.getId())).thenReturn(Optional.of(anotherAccount));
        // Saldo disponível é apenas R$ 50,00
        when(ledgerEntryRepository.getBalanceByAccountId(userAccount.getId()))
            .thenReturn(new BigDecimal("50.00"));

        // ACT & ASSERT
        assertThatThrownBy(() -> ledgerService.transfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Saldo insuficiente");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfer: deve lançar IllegalArgumentException quando conta origem == destino")
    void transfer_deveLancarExcecao_quandoContaOrigemIgualDestino() {
        // ARRANGE — mesma UUID para origem e destino
        UUID mesmaId = userAccount.getId();
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(
            idempotencyKey,
            mesmaId,
            mesmaId,
            new BigDecimal("100.00"),
            "Auto-transferência proibida"
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThatThrownBy(() -> ledgerService.transfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("origem e destino não podem ser as mesmas");

        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("transfer: invariante contábil — DEBIT na origem e CREDIT no destino com mesmo valor")
    void transfer_deveCriarDuasEntradasComMesmoValor() {
        // ARRANGE
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal valorTransferencia = new BigDecimal("75.00");
        TransferRequest request = new TransferRequest(
            idempotencyKey,
            userAccount.getId(),
            anotherAccount.getId(),
            valorTransferencia,
            "Transferência normal"
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findById(anotherAccount.getId())).thenReturn(Optional.of(anotherAccount));
        when(ledgerEntryRepository.getBalanceByAccountId(userAccount.getId()))
            .thenReturn(new BigDecimal("200.00")); // Saldo suficiente

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        savedTx.setIdempotencyKey(idempotencyKey);
        savedTx.setAmount(valorTransferencia);
        savedTx.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(ledgerEntryRepository.findByTransactionId(savedTx.getId())).thenReturn(List.of());

        // ACT
        ledgerService.transfer(request);

        // ASSERT — ArgumentCaptor "grava" os argumentos passados para save()
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        List<LedgerEntry> entriesSalvas = entryCaptor.getAllValues();
        assertThat(entriesSalvas).hasSize(2);

        // Invariante: DEBIT na conta de origem com o valor correto
        LedgerEntry debit = entriesSalvas.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT)
            .findFirst().orElseThrow();
        assertThat(debit.getAmount()).isEqualByComparingTo(valorTransferencia);
        assertThat(debit.getAccount()).isEqualTo(userAccount);

        // Invariante: CREDIT na conta de destino com o mesmo valor
        LedgerEntry credit = entriesSalvas.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .findFirst().orElseThrow();
        assertThat(credit.getAmount()).isEqualByComparingTo(valorTransferencia);
        assertThat(credit.getAccount()).isEqualTo(anotherAccount);
    }
}
