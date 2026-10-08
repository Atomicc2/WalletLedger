package com.API.walletLedger.dto;

import com.API.walletLedger.domain.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID accountId,
    EntryType entryType,
    BigDecimal amount,
    Instant createdAt
) {}
