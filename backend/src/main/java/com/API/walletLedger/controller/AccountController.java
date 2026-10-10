package com.API.walletLedger.controller;

import com.API.walletLedger.config.OwnershipGuard;
import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.EntryType;
import com.API.walletLedger.domain.LedgerEntry;
import com.API.walletLedger.dto.AccountResponse;
import com.API.walletLedger.dto.BalanceResponse;
import com.API.walletLedger.dto.LedgerEntryResponse;
import com.API.walletLedger.dto.PageResponse;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import com.API.walletLedger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /** Endpoint "me": dados da conta do usuário autenticado (inclui ID para usar em depósito/transferência). */
    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyAccount() {
        UUID accountId = currentUserAccountId();
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada: " + accountId));
        return ResponseEntity.ok(new AccountResponse(account.getId(), account.getCurrency()));
    }

    /** Endpoint "me": saldo da conta do usuário autenticado (não expõe UUID). */
    @GetMapping("/me/balance")
    public ResponseEntity<BalanceResponse> getMyBalance() {
        UUID accountId = currentUserAccountId();
        return getBalance(accountId);
    }

    /** Endpoint "me": extrato paginado com filtros (tipo, data inicial/final). */
    @GetMapping("/me/statement")
    public ResponseEntity<PageResponse<LedgerEntryResponse>> getMyStatement(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String[] sort,
        @RequestParam(required = false) EntryType type,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        UUID accountId = currentUserAccountId();
        return getStatementPaged(accountId, page, size, sort, type, startDate, endDate);
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

    /** Extrato legado (sem paginação) — mantido para compatibilidade. */
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

    /** Extrato paginado com filtros (para uso interno pelo endpoint /me/statement). */
    private ResponseEntity<PageResponse<LedgerEntryResponse>> getStatementPaged(
        UUID accountId,
        int page,
        int size,
        String[] sort,
        EntryType type,
        LocalDate startDate,
        LocalDate endDate
    ) {
        ownershipGuard.assertAccountOwner(accountId);

        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Conta não encontrada: " + accountId);
        }

        // Parse do sort (ex.: "createdAt,desc" -> Sort.by(Direction.DESC, "createdAt"))
        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc")
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = sort[0];
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));

        Instant start = (startDate != null) ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant end = (endDate != null) ? endDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant() : null;

        Page<LedgerEntry> entryPage;
        if (type != null && start != null && end != null) {
            entryPage = ledgerEntryRepository.findByAccountIdAndEntryTypeAndCreatedAtBetween(accountId, type, start, end, pageable);
        } else if (type != null) {
            entryPage = ledgerEntryRepository.findByAccountIdAndEntryType(accountId, type, pageable);
        } else if (start != null && end != null) {
            entryPage = ledgerEntryRepository.findByAccountIdAndCreatedAtBetween(accountId, start, end, pageable);
        } else {
            entryPage = ledgerEntryRepository.findByAccountId(accountId, pageable);
        }

        List<LedgerEntryResponse> content = entryPage.getContent().stream()
            .map(e -> new LedgerEntryResponse(
                e.getId(),
                e.getAccount().getId(),
                e.getEntryType(),
                e.getAmount(),
                e.getCreatedAt()
            ))
            .toList();

        PageResponse<LedgerEntryResponse> response = new PageResponse<>(
            content,
            entryPage.getNumber(),
            entryPage.getSize(),
            entryPage.getTotalElements(),
            entryPage.getTotalPages(),
            entryPage.isFirst(),
            entryPage.isLast()
        );

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