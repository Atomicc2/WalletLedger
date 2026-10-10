package com.API.walletLedger.repository;

import com.API.walletLedger.domain.EntryType;
import com.API.walletLedger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountId(UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /** Paginação simples para extrato. */
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);

    /** Filtro por tipo (DEBIT/CREDIT) + paginação. */
    Page<LedgerEntry> findByAccountIdAndEntryType(UUID accountId, EntryType entryType, Pageable pageable);

    /** Filtro por período + paginação. */
    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.account.id = :accountId
          AND e.createdAt >= :start
          AND e.createdAt <= :end
        ORDER BY e.createdAt DESC
    """)
    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(
        @Param("accountId") UUID accountId,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    /** Filtro por tipo + período + paginação. */
    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.account.id = :accountId
          AND e.entryType = :type
          AND e.createdAt >= :start
          AND e.createdAt <= :end
        ORDER BY e.createdAt DESC
    """)
    Page<LedgerEntry> findByAccountIdAndEntryTypeAndCreatedAtBetween(
        @Param("accountId") UUID accountId,
        @Param("type") EntryType type,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN e.entryType = com.API.walletLedger.domain.EntryType.CREDIT THEN e.amount ELSE -e.amount END),
            0
        )
        FROM LedgerEntry e
        WHERE e.account.id = :accountId
    """)
    BigDecimal getBalanceByAccountId(@Param("accountId") UUID accountId);
}