package com.API.walletLedger.service;

import com.API.walletLedger.domain.*;
import com.API.walletLedger.dto.*;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import com.API.walletLedger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Operação de Depósito Externo:
     * - Débito na conta SYSTEM (origem dos recursos externos)
     * - Crédito na conta do Usuário (destino dos recursos)
     */
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        // 1. Idempotência: se já existe transação com esta chave, retorna a existente
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            return toResponse(existingTx.get());
        }

        // 2. Busca conta de destino do usuário
        Account targetAccount = accountRepository.findById(request.targetAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada: " + request.targetAccountId()));

        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Conta de destino não está ativa.");
        }

        // 3. Busca conta mestre do sistema
        Account systemAccount = accountRepository.findByAccountType(AccountType.SYSTEM)
            .orElseThrow(() -> new IllegalStateException("Conta mestre do sistema não encontrada."));

        // 4. Cria e persiste o evento de negócio (Transaction)
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setAmount(request.amount());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(request.description() != null ? request.description() : "Depósito de fundos");
        transaction.setTargetAccount(targetAccount);
        Transaction savedTx = transactionRepository.save(transaction);

        // 5. Partidas Dobradas: Débito no Sistema e Crédito no Usuário
        LedgerEntry debitSystem = new LedgerEntry();
        debitSystem.setTransaction(savedTx);
        debitSystem.setAccount(systemAccount);
        debitSystem.setEntryType(EntryType.DEBIT);
        debitSystem.setAmount(request.amount());

        LedgerEntry creditUser = new LedgerEntry();
        creditUser.setTransaction(savedTx);
        creditUser.setAccount(targetAccount);
        creditUser.setEntryType(EntryType.CREDIT);
        creditUser.setAmount(request.amount());

        ledgerEntryRepository.save(debitSystem);
        ledgerEntryRepository.save(creditUser);

        return toResponse(savedTx);
    }

    /**
     * Operação de Depósito Assíncrono (Fase 3.1):
     * - Cria a transação como PENDING e devolve 202 imediatamente (o cliente não espera)
     * - NÃO cria lançamentos contábeis: o dinheiro ainda NÃO moveu.
     *   As partidas só são criadas quando a transação for LIQUIDADA (COMPLETED),
     *   pelo webhook (B2a) ou pelo worker da fila (B2b).
     * - Enquanto PENDING, o saldo do usuário não muda — é apenas uma intenção.
     */
    @Transactional
    public TransactionResponse depositAsync(DepositRequest request) {
        // 1. Idempotência: retentativas do cliente não duplicam a intenção de depósito
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            return toResponse(existingTx.get());
        }

        // 2. Valida a conta de destino (existe e está ativa)
        Account targetAccount = accountRepository.findById(request.targetAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada: " + request.targetAccountId()));

        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Conta de destino não está ativa.");
        }

        // 3. Cria a transação PENDING — SEM partidas contábeis (dinheiro não moveu ainda).
        //    O destino é registrado na própria transação (target_account_id), pois
        //    sem partidas não há outro vínculo com a conta até a liquidação.
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setAmount(request.amount());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTargetAccount(targetAccount);
        transaction.setDescription(request.description() != null
            ? request.description()
            : "Depósito externo aguardando liquidação");
        Transaction savedTx = transactionRepository.save(transaction);

        return toResponse(savedTx);
    }

    /**
     * Operação de Transferência entre Usuários:
     * - Validação de mesma conta
     * - Lock pessimista na conta de origem para evitar concorrência/gasto duplo
     * - Validação do saldo contábil atualizado
     * - Débito na conta de origem
     * - Crédito na conta de destino
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        // 1. Idempotência
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            return toResponse(existingTx.get());
        }

        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("A conta de origem e destino não podem ser as mesmas.");
        }

        // 2. Busca com Lock Pessimista na conta de origem (SELECT ... FOR UPDATE)
        Account sourceAccount = accountRepository.findByIdForUpdate(request.sourceAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Conta de origem não encontrada: " + request.sourceAccountId()));

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Conta de origem não está ativa.");
        }

        // 3. Busca conta de destino
        Account targetAccount = accountRepository.findById(request.targetAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada: " + request.targetAccountId()));

        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Conta de destino não está ativa.");
        }

        // 4. Validação de Saldo Contábil no banco de dados
        BigDecimal currentBalance = ledgerEntryRepository.getBalanceByAccountId(sourceAccount.getId());
        if (currentBalance.compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente. Saldo disponível: R$ " + currentBalance);
        }

        // 5. Criação da Transação
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setAmount(request.amount());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(request.description() != null ? request.description() : "Transferência entre contas");
        Transaction savedTx = transactionRepository.save(transaction);

        // 6. Partidas Dobradas: Débito na Origem e Crédito no Destino
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransaction(savedTx);
        debitEntry.setAccount(sourceAccount);
        debitEntry.setEntryType(EntryType.DEBIT);
        debitEntry.setAmount(request.amount());

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransaction(savedTx);
        creditEntry.setAccount(targetAccount);
        creditEntry.setEntryType(EntryType.CREDIT);
        creditEntry.setAmount(request.amount());

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return toResponse(savedTx);
    }

    /**
     * Operação de Estorno (Fase 3.2):
     * - Só estorna transação COMPLETED (não estorna PENDING/FAILED nem já estornada)
     * - Cria uma NOVA transação de estorno (COMPLETED) com partidas compensatórias INVERTIDAS
     * - Marca a original como REVERSED e vincula-a ao estorno via reversal_of (auditoria)
     * - Lock pessimista na transação original E nas contas a debitar, impedindo
     *   estorno duplo e gasto duplo concorrente
     */
    @Transactional
    public TransactionResponse reverse(ReverseRequest request) {
        // 1. Idempotência do estorno: mesma chave → retorna o estorno já criado
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            return toResponse(existingTx.get());
        }

        // 2. Lock pessimista na transação original (SELECT ... FOR UPDATE):
        //    dois estornos concorrentes na MESMA transação ficam serializados aqui
        Transaction original = transactionRepository.findByIdForUpdate(request.transactionId())
            .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada: " + request.transactionId()));

        // 3. Regra de estado: somente COMPLETED pode ser estornada
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Apenas transações COMPLETED podem ser estornadas.");
        }

        // 4. Carrega as partidas originais para poder invertê-las
        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(original.getId());
        if (originalEntries.isEmpty()) {
            throw new IllegalStateException("Transação original não possui lançamentos contábeis.");
        }

        // 5. Validação de saldo nas contas que serão DEBITADAS no estorno:
        //    quem recebeu o CREDITO na original devolve o valor.
        //    Lock pessimista na conta evita que um débito concorrente zere o saldo
        //    entre a verificação e a gravação dos lançamentos.
        for (LedgerEntry entry : originalEntries) {
            if (entry.getEntryType() == EntryType.CREDIT) {
                Account accountToDebit = accountRepository.findByIdForUpdate(entry.getAccount().getId())
                    .orElseThrow(() -> new IllegalStateException("Conta a debitar não encontrada: " + entry.getAccount().getId()));

                if (accountToDebit.getStatus() != AccountStatus.ACTIVE) {
                    throw new IllegalStateException("Conta a debitar não está ativa.");
                }

                BigDecimal balance = ledgerEntryRepository.getBalanceByAccountId(accountToDebit.getId());
                if (balance.compareTo(entry.getAmount()) < 0) {
                    throw new IllegalArgumentException("Saldo insuficiente para estorno. Saldo disponível: R$ " + balance);
                }
            }
        }

        // 6. Marca a original como REVERSED (auditoria)
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        // 7. Cria a transação de estorno (COMPLETED) com vínculo de auditoria
        Transaction reversal = new Transaction();
        reversal.setIdempotencyKey(request.idempotencyKey());
        reversal.setAmount(original.getAmount());
        reversal.setStatus(TransactionStatus.COMPLETED);
        reversal.setDescription(request.description() != null
            ? request.description()
            : "Estorno da transação " + original.getId());
        reversal.setReversalOf(original);
        Transaction savedReversal = transactionRepository.save(reversal);

        // 8. Partidas compensatórias INVERTIDAS: o DEBIT original vira CREDIT e vice-versa.
        //    Isso preserva o invariante contábil (soma algébrica = zero) POR transação.
        for (LedgerEntry entry : originalEntries) {
            LedgerEntry reversed = new LedgerEntry();
            reversed.setTransaction(savedReversal);
            reversed.setAccount(entry.getAccount());
            reversed.setEntryType(entry.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT);
            reversed.setAmount(entry.getAmount());
            ledgerEntryRepository.save(reversed);
        }

        return toResponse(savedReversal);
    }

    /**
     * Liquidação de uma transação PENDING (Fase 3.1) — chamada pelo webhook do
     * provedor (B2a) e pelo worker da fila (B2b). É o momento em que o dinheiro
     * "passa a valer de verdade".
     *
     * Fluxo:
     * - Lock pessimista na transação: notificações CONCORRENTES ficam serializadas;
     * - Idempotência natural: só liquida quem ainda está PENDING (retentativas do
     *   provedor caem aqui e recebem 200 sem duplicar partidas);
     * - REJECTED/conta bloqueada → FAILED (sem partidas: nada se moveu);
     * - APPROVED → cria as partidas do depósito (DEBIT SYSTEM, CREDIT destino)
     *   e marca COMPLETED — só AGORA o saldo muda.
     */
    @Transactional
    public TransactionResponse settle(UUID transactionId, boolean approved) {
        // 1. Lock pessimista (SELECT ... FOR UPDATE): a segunda notificação da MESMA
        //    transação espera aqui e enxerga o status já atualizado
        Transaction transaction = transactionRepository.findByIdForUpdate(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada: " + transactionId));

        // 2. Idempotência: provedores de webhook reenviam até receber 2xx.
        //    Transação que já saiu de PENDING → devolve o estado atual e NÃO refaz nada
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return toResponse(transaction);
        }

        // 3. Reprovação do provedor → FAILED sem lançamentos contábeis
        if (!approved) {
            transaction.setStatus(TransactionStatus.FAILED);
            return toResponse(transactionRepository.save(transaction));
        }

        // 4. O destino foi registrado na transação na criação (coluna target_account_id)
        Account registeredTarget = transaction.getTargetAccount();
        if (registeredTarget == null) {
            throw new IllegalStateException("Transação pendente sem conta de destino registrada.");
        }

        // 5. Lock na conta de destino: a conta pode ter sido bloqueada ENQUANTO a
        //    transação ficava PENDING — o lock garante checar e creditar atomicamente
        Account targetAccount = accountRepository.findByIdForUpdate(registeredTarget.getId())
            .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada: " + registeredTarget.getId()));

        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            transaction.setStatus(TransactionStatus.FAILED);
            return toResponse(transactionRepository.save(transaction));
        }

        // 6. Conta mestre do sistema (perna de débito do depósito, como no síncrono)
        Account systemAccount = accountRepository.findByAccountType(AccountType.SYSTEM)
            .orElseThrow(() -> new IllegalStateException("Conta mestre do sistema não encontrada."));

        // 7. LIQUIDAÇÃO: cria as partidas dobradas e finaliza
        LedgerEntry debitSystem = new LedgerEntry();
        debitSystem.setTransaction(transaction);
        debitSystem.setAccount(systemAccount);
        debitSystem.setEntryType(EntryType.DEBIT);
        debitSystem.setAmount(transaction.getAmount());

        LedgerEntry creditTarget = new LedgerEntry();
        creditTarget.setTransaction(transaction);
        creditTarget.setAccount(targetAccount);
        creditTarget.setEntryType(EntryType.CREDIT);
        creditTarget.setAmount(transaction.getAmount());

        ledgerEntryRepository.save(debitSystem);
        ledgerEntryRepository.save(creditTarget);

        transaction.setStatus(TransactionStatus.COMPLETED);
        return toResponse(transactionRepository.save(transaction));
    }

    /**
     * Monta o TransactionResponse contendo todas as linhas contábeis associadas
     */
    private TransactionResponse toResponse(Transaction tx) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        List<LedgerEntryResponse> entryResponses = entries.stream()
            .map(e -> new LedgerEntryResponse(
                e.getId(),
                e.getAccount().getId(),
                e.getEntryType(),
                e.getAmount(),
                e.getCreatedAt()
            ))
            .toList();

        return new TransactionResponse(
            tx.getId(),
            tx.getIdempotencyKey(),
            tx.getAmount(),
            tx.getStatus(),
            tx.getDescription(),
            tx.getCreatedAt(),
            tx.getUpdatedAt(),
            entryResponses
        );
    }
}
