package com.API.walletLedger.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Instant createdAt,
        UUID defaultAccountId
) {}