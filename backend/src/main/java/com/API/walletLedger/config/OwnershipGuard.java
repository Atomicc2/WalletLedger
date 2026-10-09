package com.API.walletLedger.config;

import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.Transaction;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.LedgerEntryRepository;
import com.API.walletLedger.repository.TransactionRepository;
import com.API.walletLedger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guarda de autorização "dono-de-conta" (Revisão de Segurança).
 *
 * Por que existe?
 * → O JWTFilter já AUTENTICA (sabe quem é o usuário), mas a autorização —
 *   "você só pode mexer no que é seu" — precisava ser verificada em cada endpoint.
 *   Sem isto, qualquer autenticado passava o ID de conta alheia (IDOR).
 *
 * Por que no CONTROLLER e não no LedgerService?
 * → O settle() é compartilhado com webhook e worker, que NÃO têm usuário logado.
 *   A guarda fica na borda (onde existe requisição HTTP + principal); a lógica
 *   de negócio permanece pura e reutilizável.
 *
 * Como recupera o usuário:
 * → O JwtAuthenticationFilter coloca um UserDetails no SecurityContext com
 *   username = e-mail. Daí consultamos o User no banco para comparar IDs
 *   (o e-mail é a identidade do token; o ID é a identidade do domínio).
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Garante que a conta pertence ao usuário autenticado.
     * Não sendo dono (ou sendo a conta SYSTEM, que não tem dono) → AccessDeniedException → 403.
     */
    public void assertAccountOwner(UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada: " + accountId));

        UUID currentUserId = currentUserId();

        // Conta SYSTEM não tem usuário vinculado → ninguém "é dono" dela
        if (account.getUser() == null || !account.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Esta conta não pertence ao usuário autenticado.");
        }
    }

    /** Identidade (ID) de quem está autenticado no SecurityContext. */
    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails principal)) {
            throw new AccessDeniedException("Usuário autenticado não encontrado.");
        }

        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Usuário não encontrado: " + principal.getUsername()))
            .getId();
    }

    /**
     * Garante que o usuário autenticado PARTICIPA da transação (estorno).
     * Participa se: for dono da conta de destino registrada (depósito, mesmo PENDING),
     * ou tiver alguma conta com lançamento na transação (transferência/depósito liquidado).
     * Um estorno é desfeito CONTRA a transação — só quem está nela pode acioná-lo.
     */
    public void assertTransactionParticipant(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada: " + transactionId));

        UUID currentUserId = currentUserId();

        // Depósitos: o destino registrado na própria transação identifica o dono
        Account targetAccount = transaction.getTargetAccount();
        boolean ownerOfTarget = targetAccount != null
            && targetAccount.getUser() != null
            && targetAccount.getUser().getId().equals(currentUserId);

        // Qualquer conta com lançamento na transação também é "participante"
        boolean participantOfEntries = ledgerEntryRepository.findByTransactionId(transaction.getId()).stream()
            .anyMatch(entry -> entry.getAccount().getUser() != null
                && entry.getAccount().getUser().getId().equals(currentUserId));

        if (!ownerOfTarget && !participantOfEntries) {
            throw new AccessDeniedException("O usuário não participa desta transação.");
        }
    }
}