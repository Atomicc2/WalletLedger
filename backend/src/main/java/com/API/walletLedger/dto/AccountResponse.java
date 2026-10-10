package com.API.walletLedger.dto;

import java.util.UUID;

/** Resposta com dados básicos da conta (usado no endpoint /me). */
public record AccountResponse(UUID accountId, String currency) {
}