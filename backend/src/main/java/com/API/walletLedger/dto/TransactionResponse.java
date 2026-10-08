package com.API.walletLedger.dto;

import com.API.walletLedger.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String idempotencyKey,
    BigDecimal amount,
    TransactionStatus status,
    String description,
    Instant createdAt,
    Instant updatedAt,
    List<LedgerEntryResponse> entries
) {}
