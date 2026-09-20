package com.API.walletLedger.repository;

import com.API.walletLedger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountId(UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

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