package com.API.walletLedger.worker;

import com.API.walletLedger.domain.Transaction;
import com.API.walletLedger.domain.TransactionStatus;
import com.API.walletLedger.repository.TransactionRepository;
import com.API.walletLedger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PendingSettlementWorker (Fase 3.1 — B2b).
 *
 * Sem Spring e sem banco: os repositórios são mocks — testamos apenas o laço
 * de consumo da fila (buscar → liquidar → não morrer com erro).
 */
@ExtendWith(MockitoExtension.class)
class PendingSettlementWorkerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private PendingSettlementWorker worker;

    @BeforeEach
    void setUp() {
        // @Value não injeta fora do contexto Spring — mesmo dilema do JwtServiceTest:
        // injetamos o campo manualmente via ReflectionTestUtils (senão fica 0L e
        // o cutoff viraria "agora", filtrando tudo).
        ReflectionTestUtils.setField(worker, "settleAfterSeconds", 30L);
    }

    private Transaction pendingTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setStatus(TransactionStatus.PENDING);
        return transaction;
    }

    @Test
    @DisplayName("settlePending: deve liquidar todas as transações pendentes da fila")
    void settlePending_deveLiquidarTodasAsTransacoesDaFila() {
        // ARRANGE — a "fila" devolveu duas transações antigas
        Transaction primeira = pendingTransaction();
        Transaction segunda = pendingTransaction();
        when(transactionRepository.findPendingOlderThan(any(Instant.class)))
            .thenReturn(List.of(primeira, segunda));

        // ACT
        worker.settlePendingTransactions();

        // ASSERT — ambas passaram pelo settle() compartilhado (aprovadas)
        verify(ledgerService).settle(primeira.getId(), true);
        verify(ledgerService).settle(segunda.getId(), true);
        verify(ledgerService, times(2)).settle(any(), anyBoolean());
    }

    @Test
    @DisplayName("settlePending: falha em uma transação não impede as demais (fila continua)")
    void settlePending_deveContinuarQuandoUmaTransacaoFalha() {
        // ARRANGE — a primeira explode, a segunda não
        Transaction problematica = pendingTransaction();
        Transaction saudavel = pendingTransaction();
        when(transactionRepository.findPendingOlderThan(any(Instant.class)))
            .thenReturn(List.of(problematica, saudavel));
        when(ledgerService.settle(problematica.getId(), true))
            .thenThrow(new IllegalStateException("boom"));

        // ACT — não deve lançar exceção para fora
        worker.settlePendingTransactions();

        // ASSERT — o loop continuou e processou a segunda
        verify(ledgerService).settle(saudavel.getId(), true);
    }

    @Test
    @DisplayName("settlePending: deve consultar a fila com cutoff = agora menos a carência (30s)")
    void settlePending_deveUsarCutoffDeCarencia() {
        // ARRANGE
        when(transactionRepository.findPendingOlderThan(any(Instant.class))).thenReturn(List.of());

        // ACT
        worker.settlePendingTransactions();

        // ASSERT — captura o Instant passado para a query
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(transactionRepository).findPendingOlderThan(cutoffCaptor.capture());

        Instant esperado = Instant.now().minusSeconds(30);
        long desvioSegundos = Math.abs(Duration.between(cutoffCaptor.getValue(), esperado).toSeconds());
        assertThat(desvioSegundos).isLessThanOrEqualTo(2); // tolerância de 2s pelo tempo de execução
        verifyNoInteractions(ledgerService); // fila vazia → nada a liquidar
    }
}