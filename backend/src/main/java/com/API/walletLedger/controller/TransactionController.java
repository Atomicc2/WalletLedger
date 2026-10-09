package com.API.walletLedger.controller;

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

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
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
        TransactionResponse response = ledgerService.depositAsync(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransactionResponse response = ledgerService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reverse")
    public ResponseEntity<TransactionResponse> reverse(@Valid @RequestBody ReverseRequest request) {
        TransactionResponse response = ledgerService.reverse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
