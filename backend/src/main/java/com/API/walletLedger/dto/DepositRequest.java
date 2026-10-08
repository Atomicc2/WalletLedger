package com.API.walletLedger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
    @NotBlank(message = "A chave de idempotência é obrigatória")
    String idempotencyKey,

    @NotNull(message = "O ID da conta de destino é obrigatório")
    UUID targetAccountId,

    @NotNull(message = "O valor é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor mínimo para depósito é R$ 0,01")
    BigDecimal amount,

    String description
) {}
