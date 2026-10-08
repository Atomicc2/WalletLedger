package com.API.walletLedger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
    UUID accountId,
    BigDecimal balance,
    String currency
) {}
