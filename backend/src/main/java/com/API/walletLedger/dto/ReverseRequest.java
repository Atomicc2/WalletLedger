package com.API.walletLedger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Requisição de estorno (Fase 3.2).
 *
 * O valor NÃO vem no request: ele é copiado da transação original, garantindo
 * que não exista divergência entre o que foi lançado e o que será estornado.
 */
public record ReverseRequest(
    @NotBlank(message = "A chave de idempotência é obrigatória")
    String idempotencyKey,

    @NotNull(message = "O ID da transação original é obrigatório")
    UUID transactionId,

    String description
) {}