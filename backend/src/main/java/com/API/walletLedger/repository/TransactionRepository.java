package com.API.walletLedger.repository;

import com.API.walletLedger.domain.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Busca com Lock Pessimista (SELECT ... FOR UPDATE).
     * Usado no estorno para serializar acessos concorrentes à MESMA transação
     * e impedir que dois estornos sejam criados simultaneamente (race condition).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * A "fila" do worker (Fase 3.1 — B2b): as transações PENDING que já passaram
     * da carência (cutoff). A própria tabela transactions faz o papel da fila —
     * o produtor é o depositAsync e o consumidor é o PendingSettlementWorker.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = com.API.walletLedger.domain.TransactionStatus.PENDING
          AND t.createdAt <= :cutoff
    """)
    List<Transaction> findPendingOlderThan(@Param("cutoff") Instant cutoff);
}