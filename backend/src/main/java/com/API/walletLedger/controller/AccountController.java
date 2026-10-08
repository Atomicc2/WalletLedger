package com.API.walletLedger.controller;

import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.LedgerEntry;
import com.API.walletLedger.dto.BalanceResponse;
import com.API.walletLedger.dto.LedgerEntryResponse;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada: " + accountId));

        BigDecimal balance = ledgerEntryRepository.getBalanceByAccountId(accountId);
        return ResponseEntity.ok(new BalanceResponse(account.getId(), balance, account.getCurrency()));
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<List<LedgerEntryResponse>> getStatement(@PathVariable UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Conta não encontrada: " + accountId);
        }

        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(accountId);
        List<LedgerEntryResponse> response = entries.stream()
            .map(e -> new LedgerEntryResponse(
                e.getId(),
                e.getAccount().getId(),
                e.getEntryType(),
                e.getAmount(),
                e.getCreatedAt()
            ))
            .toList();

        return ResponseEntity.ok(response);
    }
}
