package com.API.walletLedger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Payload que o "provedor de pagamento" envia no webhook (Fase 3.1).
 *
 * Simula a notificação externa: "a transação X foi aprovada/reprovada".
 * O valor NÃO vem aqui — ele já está na transação PENDING no nosso banco.
 */
public record WebhookPaymentRequest(
    @NotNull(message = "O ID da transação é obrigatório")
    UUID transactionId,

    @NotBlank(message = "O evento é obrigatório")
    @Pattern(regexp = "APPROVED|REJECTED", message = "O evento deve ser APPROVED ou REJECTED")
    String event
) {}