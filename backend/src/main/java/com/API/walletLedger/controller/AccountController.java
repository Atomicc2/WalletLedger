package com.API.walletLedger.controller;

import com.API.walletLedger.config.OwnershipGuard;
import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.LedgerEntry;
import com.API.walletLedger.dto.BalanceResponse;
import com.API.walletLedger.dto.LedgerEntryResponse;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import com.API.walletLedger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final OwnershipGuard ownershipGuard;
    private final UserRepository userRepository;

    /** Endpoint "me": saldo da conta do usuário autenticado (não expõe UUID). */
    @GetMapping("/me/balance")
    public ResponseEntity<BalanceResponse> getMyBalance() {
        UUID accountId = currentUserAccountId();
        return getBalance(accountId);
    }

    /** Endpoint "me": extrato da conta do usuário autenticado. */
    @GetMapping("/me/statement")
    public ResponseEntity<List<LedgerEntryResponse>> getMyStatement() {
        UUID accountId = currentUserAccountId();
        return getStatement(accountId);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        // Revisão de segurança: só o dono lê o próprio saldo (senão → 403)
        ownershipGuard.assertAccountOwner(accountId);

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada: " + accountId));

        BigDecimal balance = ledgerEntryRepository.getBalanceByAccountId(accountId);
        return ResponseEntity.ok(new BalanceResponse(account.getId(), balance, account.getCurrency()));
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<List<LedgerEntryResponse>> getStatement(@PathVariable UUID accountId) {
        // Revisão de segurança: o extrato financeiro de terceiros não é público (senão → 403)
        ownershipGuard.assertAccountOwner(accountId);

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

    /** Resolve o ID da conta do usuário autenticado no SecurityContext. */
    private UUID currentUserAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails principal)) {
            throw new org.springframework.security.access.AccessDeniedException("Usuário não autenticado");
        }
        String email = principal.getUsername();
        return userRepository.findByEmail(email)
            .map(u -> accountRepository.findByUserId(u.getId()).get(0).getId())
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada para o usuário: " + email));
    }
}
