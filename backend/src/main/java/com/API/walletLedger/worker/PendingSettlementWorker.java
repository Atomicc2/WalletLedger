package com.API.walletLedger.worker;

import com.API.walletLedger.domain.Transaction;
import com.API.walletLedger.repository.TransactionRepository;
import com.API.walletLedger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Worker de liquidação (Fase 3.1 — B2b): o "consumidor de fila".
 *
 * Simula uma fila assíncrona sem infra nova: a própria tabela transactions é a
 * fila. O produtor (depositAsync) joga transações PENDING; este worker as consome
 * — varre as que passaram da carência e as liquida via MESMO settle() do webhook.
 *
 * Por que o mesmo settle()?
 * → A lógica de liquidação (lock, idempotência, partidas) existe UMA vez só.
 *   Webhook e worker são dois mensageiros diferentes entregando a mesma notícia:
 *   se um já liquidou, o outro cai na guarda "só liquida PENDING" e não duplica.
 *
 * @Scheduled(fixedDelayString = ...): repete o método numa thread de fundo,
 * esperando o intervalo APÓS TERMINAR a execução anterior (nunca sobrepõe).
 * Se a execução lançar exceção, o Spring registra e reagenda — o worker sobrevive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSettlementWorker {

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    /** Carência em segundos: PENDING mais novo que isso ainda não é da fila. */
    @Value("${wallet.worker.settle-after-seconds}")
    private long settleAfterSeconds;

    @Scheduled(fixedDelayString = "${wallet.worker.poll-interval-ms}")
    public void settlePendingTransactions() {
        Instant cutoff = Instant.now().minusSeconds(settleAfterSeconds);
        List<Transaction> pendingTransactions = transactionRepository.findPendingOlderThan(cutoff);

        if (pendingTransactions.isEmpty()) {
            return;
        }

        for (Transaction transaction : pendingTransactions) {
            try {
                // Simula a confirmação do provedor chegando pela fila interna.
                // setor true = aprovado; setor false (rejeição) também seria um
                // evento possível na fila real — o settle() já suporta os dois.
                ledgerService.settle(transaction.getId(), true);
            } catch (Exception e) {
                // try/catch POR TRANSAÇÃO: uma transação com problema não pode
                // travar o resto da fila (um item ruim pararia o worker inteiro)
                log.error("Falha ao liquidar a transação {}", transaction.getId(), e);
            }
        }

        log.info("Worker de liquidação processou {} transação(ões) pendente(s)", pendingTransactions.size());
    }
}