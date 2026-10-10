package com.API.walletLedger.dto;

import java.util.List;

/** Resposta paginada genérica (compatível com Spring Data Page). */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}