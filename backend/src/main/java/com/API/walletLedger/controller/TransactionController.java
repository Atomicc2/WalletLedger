package com.API.walletLedger.controller;

import com.API.walletLedger.config.OwnershipGuard;
import com.API.walletLedger.dto.DepositRequest;
import com.API.walletLedger.dto.ReverseRequest;
import com.API.walletLedger.dto.TransactionResponse;
import com.API.walletLedger.dto.TransferRequest;
import com.API.walletLedger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerService ledgerService;
    private final OwnershipGuard ownershipGuard;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        // Revisão de segurança: só creditar na CONTA PRÓPRIA (IDOR bloqueado → 403)
        ownershipGuard.assertAccountOwner(request.targetAccountId());

        TransactionResponse response = ledgerService.deposit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Depósito assíncrono (Fase 3.1): 202 Accepted.
     * Significa "recebi, mas ainda não terminei" — a transação nasce PENDING e
     * só ganha partidas contábeis quando for liquidada (webhook ou fila).
     */
    @PostMapping("/deposit-async")
    public ResponseEntity<TransactionResponse> depositAsync(@Valid @RequestBody DepositRequest request) {
        // Revisão de segurança: destino precisa ser do próprio usuário
        ownershipGuard.assertAccountOwner(request.targetAccountId());

        TransactionResponse response = ledgerService.depositAsync(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        // Revisão de segurança (crítico): a ORIGEM precisa ser conta própria.
        // O DESTINO pode ser de qualquer um — transferir para outros é a feature.
        ownershipGuard.assertAccountOwner(request.sourceAccountId());

        TransactionResponse response = ledgerService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reverse")
    public ResponseEntity<TransactionResponse> reverse(@Valid @RequestBody ReverseRequest request) {
        // Revisão de segurança: só quem PARTICIPA da transação pode estorná-la
        // (dono do depósito ou alguma das contas da transferência)
        ownershipGuard.assertTransactionParticipant(request.transactionId());

        TransactionResponse response = ledgerService.reverse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
