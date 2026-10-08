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
