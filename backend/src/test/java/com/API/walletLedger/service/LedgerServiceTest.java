package com.API.walletLedger.service;

import com.API.walletLedger.domain.*;
import com.API.walletLedger.dto.DepositRequest;
import com.API.walletLedger.dto.ReverseRequest;
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
    // TESTES DE DEPÓSITO ASSÍNCRONO (Fase 3.1)
    // =========================================================================

    @Test
    @DisplayName("depositAsync: deve criar transação PENDING sem partidas contábeis (dinheiro não moveu)")
    void depositAsync_deveCriarTransacaoPENDING_semPartidasContabeis() {
        // ARRANGE
        String idempotencyKey = UUID.randomUUID().toString();
        DepositRequest request = new DepositRequest(
            idempotencyKey,
            userAccount.getId(),
            new BigDecimal("300.00"),
            "Depósito externo"
        );

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findById(userAccount.getId())).thenReturn(Optional.of(userAccount));

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        savedTx.setIdempotencyKey(idempotencyKey);
        savedTx.setAmount(request.amount());
        savedTx.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        // ACT
        TransactionResponse response = ledgerService.depositAsync(request);

        // ASSERT
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);

        // Captura a transação salva para inspecionar o estado persistido
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        // Sem partidas, a transação é quem REGISTRA o destino (coluna target_account_id)
        assertThat(saved.getTargetAccount()).isEqualTo(userAccount);

        // NENHUMA partidas contábeis: o saldo não muda enquanto PENDING
        verify(ledgerEntryRepository, never()).save(any());
        // A conta SYSTEM só é necessária na LIQUIDAÇÃO, não na criação da intenção
        verify(accountRepository, never()).findByAccountType(AccountType.SYSTEM);
    }

    @Test
    @DisplayName("depositAsync: idempotência — deve retornar transação existente sem duplicar")
    void depositAsync_deveRetornarTransacaoExistente_quandoIdempotencyKeyDuplicada() {
        // ARRANGE
        String idempotencyKey = "chave-assincrona-ja-usada";
        Transaction existing = new Transaction();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(idempotencyKey);
        existing.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionId(existing.getId())).thenReturn(List.of());

        DepositRequest request = new DepositRequest(
            idempotencyKey, userAccount.getId(), new BigDecimal("50.00"), null
        );

        // ACT
        TransactionResponse response = ledgerService.depositAsync(request);

        // ASSERT
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
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

    // =========================================================================
    // TESTES DE ESTORNO
    // =========================================================================

    @Test
    @DisplayName("reverse: deve criar estorno COMPLETED com partidas compensatórias invertidas")
    void reverse_deveCriarEstornoComPartidasInvertidas() {
        // ARRANGE — Original COMPLETED: DEBIT SYSTEM + CREDIT Usuário
        String idempotencyKey = UUID.randomUUID().toString();
        UUID originalTxId = UUID.randomUUID();
        BigDecimal valor = new BigDecimal("100.00");

        Transaction original = new Transaction();
        original.setId(originalTxId);
        original.setIdempotencyKey("chave-original-1");
        original.setAmount(valor);
        original.setStatus(TransactionStatus.COMPLETED);

        LedgerEntry debitSystem = new LedgerEntry();
        debitSystem.setAccount(systemAccount);
        debitSystem.setEntryType(EntryType.DEBIT);
        debitSystem.setAmount(valor);

        LedgerEntry creditUser = new LedgerEntry();
        creditUser.setAccount(userAccount);
        creditUser.setEntryType(EntryType.CREDIT);
        creditUser.setAmount(valor);

        // Quem recebeu o CREDITO (o usuário) devolve o valor → saldo suficiente
        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdForUpdate(originalTxId)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByTransactionId(originalTxId)).thenReturn(List.of(debitSystem, creditUser));
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(ledgerEntryRepository.getBalanceByAccountId(userAccount.getId())).thenReturn(new BigDecimal("100.00"));

        Transaction savedReversal = new Transaction();
        savedReversal.setId(UUID.randomUUID());
        savedReversal.setIdempotencyKey(idempotencyKey);
        savedReversal.setAmount(valor);
        savedReversal.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedReversal);
        when(ledgerEntryRepository.findByTransactionId(savedReversal.getId())).thenReturn(List.of());

        ReverseRequest request = new ReverseRequest(idempotencyKey, originalTxId, null);

        // ACT
        TransactionResponse response = ledgerService.reverse(request);

        // ASSERT
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo(valor);

        // Original foi marcada como REVERSED e salva
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        verify(transactionRepository).save(original);

        // Estorno criou 2 partidas COMPENSATÓRIAS com os tipos INVERTIDOS
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());
        List<LedgerEntry> entradasInvertidas = entryCaptor.getAllValues();
        assertThat(entradasInvertidas).hasSize(2);

        LedgerEntry invertidaSystem = entradasInvertidas.stream()
            .filter(e -> e.getAccount().equals(systemAccount)).findFirst().orElseThrow();
        assertThat(invertidaSystem.getEntryType()).isEqualTo(EntryType.CREDIT); // era DEBIT
        assertThat(invertidaSystem.getAmount()).isEqualByComparingTo(valor);

        LedgerEntry invertidaUser = entradasInvertidas.stream()
            .filter(e -> e.getAccount().equals(userAccount)).findFirst().orElseThrow();
        assertThat(invertidaUser.getEntryType()).isEqualTo(EntryType.DEBIT); // era CREDIT
        assertThat(invertidaUser.getAmount()).isEqualByComparingTo(valor);
    }

    @Test
    @DisplayName("reverse: idempotência — deve retornar estorno existente sem duplicar")
    void reverse_deveRetornarEstornoExistente_quandoIdempotencyKeyDuplicada() {
        // ARRANGE
        String idempotencyKey = "chave-estorno-ja-usada";
        Transaction existing = new Transaction();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(idempotencyKey);
        existing.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionId(existing.getId())).thenReturn(List.of());

        ReverseRequest request = new ReverseRequest(idempotencyKey, UUID.randomUUID(), null);

        // ACT
        TransactionResponse response = ledgerService.reverse(request);

        // ASSERT
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        verify(transactionRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("reverse: deve lançar IllegalArgumentException quando a transação não existe")
    void reverse_deveLancarExcecao_quandoTransacaoNaoEncontrada() {
        // ARRANGE
        String idempotencyKey = UUID.randomUUID().toString();
        UUID txInexistente = UUID.randomUUID();
        ReverseRequest request = new ReverseRequest(idempotencyKey, txInexistente, null);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdForUpdate(txInexistente)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThatThrownBy(() -> ledgerService.reverse(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transação não encontrada");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reverse: deve lançar IllegalStateException quando a transação não está COMPLETED")
    void reverse_deveLancarExcecao_quandoStatusNaoEhCompleted() {
        // ARRANGE — transação já estornada (REVERSED)
        String idempotencyKey = UUID.randomUUID().toString();
        UUID originalTxId = UUID.randomUUID();

        Transaction original = new Transaction();
        original.setId(originalTxId);
        original.setStatus(TransactionStatus.REVERSED);

        ReverseRequest request = new ReverseRequest(idempotencyKey, originalTxId, null);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdForUpdate(originalTxId)).thenReturn(Optional.of(original));

        // ACT & ASSERT
        assertThatThrownBy(() -> ledgerService.reverse(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Apenas transações COMPLETED");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reverse: deve lançar IllegalArgumentException quando o saldo é insuficiente para devolver")
    void reverse_deveLancarExcecao_quandoSaldoInsuficiente() {
        // ARRANGE — usuário recebeu CREDIT de 100 na original, mas só tem 30
        String idempotencyKey = UUID.randomUUID().toString();
        UUID originalTxId = UUID.randomUUID();
        BigDecimal valor = new BigDecimal("100.00");

        Transaction original = new Transaction();
        original.setId(originalTxId);
        original.setStatus(TransactionStatus.COMPLETED);

        LedgerEntry creditUser = new LedgerEntry();
        creditUser.setAccount(userAccount);
        creditUser.setEntryType(EntryType.CREDIT);
        creditUser.setAmount(valor);

        ReverseRequest request = new ReverseRequest(idempotencyKey, originalTxId, null);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdForUpdate(originalTxId)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByTransactionId(originalTxId)).thenReturn(List.of(creditUser));
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(ledgerEntryRepository.getBalanceByAccountId(userAccount.getId())).thenReturn(new BigDecimal("30.00"));

        // ACT & ASSERT
        assertThatThrownBy(() -> ledgerService.reverse(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Saldo insuficiente para estorno");

        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    // =========================================================================
    // TESTES DE LIQUIDAÇÃO — WEBHOOK / FILA (Fase 3.1)
    // =========================================================================

    @Test
    @DisplayName("settle: aprovado — deve criar partidas do depósito e marcar COMPLETED")
    void settle_deveCriarPartidasECOMPLETED_quandoAprovado() {
        // ARRANGE — transação PENDING criada pelo depositAsync (sem partidas)
        Transaction pending = new Transaction();
        pending.setId(UUID.randomUUID());
        pending.setAmount(new BigDecimal("250.00"));
        pending.setStatus(TransactionStatus.PENDING);
        pending.setTargetAccount(userAccount);

        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findByAccountType(AccountType.SYSTEM)).thenReturn(Optional.of(systemAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pending);
        when(ledgerEntryRepository.findByTransactionId(pending.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.settle(pending.getId(), true);

        // ASSERT
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Agora sim: as duas partidas do depósito foram criadas
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());
        List<LedgerEntry> entradas = entryCaptor.getAllValues();

        LedgerEntry debit = entradas.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT).findFirst().orElseThrow();
        assertThat(debit.getAccount()).isEqualTo(systemAccount);
        assertThat(debit.getAmount()).isEqualByComparingTo("250.00");

        LedgerEntry credit = entradas.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT).findFirst().orElseThrow();
        assertThat(credit.getAccount()).isEqualTo(userAccount);
        assertThat(credit.getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("settle: rejeitado — deve marcar FAILED sem nenhuma partidas contábil")
    void settle_deveMarcarFAILED_quandoRejeitado() {
        // ARRANGE
        Transaction pending = new Transaction();
        pending.setId(UUID.randomUUID());
        pending.setAmount(new BigDecimal("100.00"));
        pending.setStatus(TransactionStatus.PENDING);
        pending.setTargetAccount(userAccount);

        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pending);
        when(ledgerEntryRepository.findByTransactionId(pending.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.settle(pending.getId(), false);

        // ASSERT — FAILED e o saldo nunca muda (sem partidas, sem conta SYSTEM)
        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        verify(ledgerEntryRepository, never()).save(any());
        verify(accountRepository, never()).findByAccountType(any());
    }

    @Test
    @DisplayName("settle: idempotente — retentativa do webhook em transação já liquidada não refaz nada")
    void settle_deveSerIdempotente_quandoTransacaoJaNaoEstaPending() {
        // ARRANGE — segunda entrega do provedor encontra a transação já COMPLETED
        Transaction alreadyCompleted = new Transaction();
        alreadyCompleted.setId(UUID.randomUUID());
        alreadyCompleted.setAmount(new BigDecimal("100.00"));
        alreadyCompleted.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findByIdForUpdate(alreadyCompleted.getId()))
            .thenReturn(Optional.of(alreadyCompleted));
        when(ledgerEntryRepository.findByTransactionId(alreadyCompleted.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.settle(alreadyCompleted.getId(), true);

        // ASSERT — devolve o estado atual sem criar nada novo
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("settle: conta bloqueada enquanto a transação esperava — deve marcar FAILED")
    void settle_deveMarcarFAILED_quandoContaDestinoBloqueada() {
        // ARRANGE — conta estava ativa ao criar o PENDING, foi bloqueada DEPOIS
        userAccount.setStatus(AccountStatus.BLOCKED);
        Transaction pending = new Transaction();
        pending.setId(UUID.randomUUID());
        pending.setAmount(new BigDecimal("80.00"));
        pending.setStatus(TransactionStatus.PENDING);
        pending.setTargetAccount(userAccount);

        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(accountRepository.findByIdForUpdate(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pending);
        when(ledgerEntryRepository.findByTransactionId(pending.getId())).thenReturn(List.of());

        // ACT
        TransactionResponse response = ledgerService.settle(pending.getId(), true);

        // ASSERT — não creditou em conta bloqueada
        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        verify(ledgerEntryRepository, never()).save(any());
        verify(accountRepository, never()).findByAccountType(any());
    }
}
