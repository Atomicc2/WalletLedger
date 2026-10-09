package com.API.walletLedger.controller;

import com.API.walletLedger.dto.TransactionResponse;
import com.API.walletLedger.dto.WebhookPaymentRequest;
import com.API.walletLedger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Webhook do "provedor de pagamento" (Fase 3.1 — B2a).
 *
 * Simula a notificação EXTERNA: em vez de ficarmos perguntando "foi aprovado?",
 * o provedor nos AVISA disparando um POST aqui (inversão do fluxo HTTP normal).
 *
 * Segurança: rota pública (o provedor não tem nosso JWT), protegida pelo header
 * X-Webhook-Secret comparado em tempo CONSTANTE — comparação com equals() comum
 * permitiria um timing attack descobrir o segredo caractere a caractere.
 * Em produção: assinatura HMAC-SHA256 do corpo + chave em secrets manager.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final LedgerService ledgerService;

    @Value("${wallet.webhook.secret}")
    private String webhookSecret;

    /**
     * Recebe a decisão sobre uma transação PENDING e a liquida.
     *
     * 200 OK (não 201): a transação já existe — aqui só processamos a decisão.
     * Retentativas do provedor também recebem 200: o método settle() é idempotente
     * (só age em PENDING), então reenviar não duplica partidas nem dinheiro.
     */
    @PostMapping("/payment")
    public ResponseEntity<TransactionResponse> paymentSettled(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String receivedSecret,
            @Valid @RequestBody WebhookPaymentRequest request) {

        if (receivedSecret == null || !MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                receivedSecret.getBytes(StandardCharsets.UTF_8))) {
            // Cai no GlobalExceptionHandler → 401 com ProblemDetail
            throw new BadCredentialsException("Assinatura do webhook inválida.");
        }

        TransactionResponse response = ledgerService.settle(
            request.transactionId(),
            "APPROVED".equals(request.event())
        );

        return ResponseEntity.ok(response);
    }
}