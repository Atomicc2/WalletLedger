package com.API.walletLedger.worker;

import com.API.walletLedger.domain.TransactionStatus;
import com.API.walletLedger.dto.UserRegistrationRequest;
import com.API.walletLedger.dto.UserResponse;
import com.API.walletLedger.repository.TransactionRepository;
import com.API.walletLedger.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do worker de liquidação (Fase 3.1 — B2b).
 *
 * Fluxo: depósito assíncrono (PENDING, saldo 0) → envelhece a transação além da
 * carência → dispara o worker manualmente → COMPLETED com saldo creditado.
 * E o caso inverso: PENDING recente é IGNORADO pelo worker (é papel do webhook
 * liquidar rápido; o worker é o fallback para o que ficou para trás).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PendingSettlementWorkerIntegrationTest {

    private static final String EMAIL = "worker@wallet.test";
    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PendingSettlementWorker worker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID accountId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        UserResponse user = userService.register(
            new UserRegistrationRequest("Usuário Worker", EMAIL, PASSWORD)
        );
        accountId = user.defaultAccountId();

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(response).path("accessToken").asText();
    }

    // ---------- Helpers ----------

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Cria um depósito assíncrono e devolve o ID da transação PENDING. */
    private String criaDepositoPendente(BigDecimal valor) throws Exception {
        String response = mockMvc.perform(post("/api/transactions/deposit-async")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountId.toString(),
                    "amount", valor))))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asText();
    }

    private TransactionStatus statusDa(String txId) {
        return transactionRepository.findById(UUID.fromString(txId)).orElseThrow().getStatus();
    }

    private BigDecimal saldo() throws Exception {
        String response = mockMvc.perform(get("/api/accounts/" + accountId + "/balance")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("balance").decimalValue();
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("worker: deve liquidar PENDING que passou da carência (saldo creditado)")
    void worker_deveLiquidarPENDING_antiga() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("75.00"));

        // PENDING recente e sem saldo
        assertThat(statusDa(txId)).isEqualTo(TransactionStatus.PENDING);
        assertThat(saldo()).isEqualByComparingTo("0");

        // Envelhece a transação para 1h atrás (passa da carência de 30s).
        // created_at tem updatable=false na entidade, então a JPA não altera esse
        // campo por dirty checking — usamos SQL direto. Antes, damos flush para
        // garantir que o INSERT do depósito já foi ao banco (senão o UPDATE não acha a linha).
        transactionRepository.flush();
        jdbcTemplate.update(
            "UPDATE transactions SET created_at = now() - interval '1 hour' WHERE id = ?",
            UUID.fromString(txId)
        );

        // ACT — dispara o worker manualmente (mesmo método que o @Scheduled roda)
        worker.settlePendingTransactions();

        // ASSERT — liquidou com as partidas e creditou o saldo
        assertThat(statusDa(txId)).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saldo()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("worker: deve ignorar PENDING recente (ainda dentro da carência)")
    void worker_deveIgnorarPENDING_recente() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("40.00"));

        // ACT
        worker.settlePendingTransactions();

        // ASSERT — muito novo para o worker: segue PENDING, saldo intacto
        assertThat(statusDa(txId)).isEqualTo(TransactionStatus.PENDING);
        assertThat(saldo()).isEqualByComparingTo("0");
    }
}