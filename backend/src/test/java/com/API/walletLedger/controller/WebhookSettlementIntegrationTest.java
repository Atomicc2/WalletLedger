package com.API.walletLedger.controller;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do webhook (Fase 3.1 — B2a).
 *
 * Fluxo ponta a ponta: depósito assíncrono (202/PENDING, saldo 0) → notificação
 * do "provedor" via /api/webhooks/payment → liquidação (COMPLETED, saldo muda).
 * Cobre também: secret errado/ausente (401), rejeição (FAILED) e retentativa
 * idempotente do provedor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebhookSettlementIntegrationTest {

    private static final String EMAIL = "webhook@wallet.test";
    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionRepository transactionRepository;

    // Mesmo segredo configurado no application.properties
    @Value("${wallet.webhook.secret}")
    private String webhookSecret;

    private UUID accountId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        UserResponse user = userService.register(
            new UserRegistrationRequest("Usuário Webhook", EMAIL, PASSWORD)
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

    private ResultActions enviaWebhook(String secret, String transactionId, String event) throws Exception {
        return mockMvc.perform(post("/api/webhooks/payment")
            .header("X-Webhook-Secret", secret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(Map.of("transactionId", transactionId, "event", event))));
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
    @DisplayName("webhook: deve retornar 401 quando o secret está errado (transação fica PENDING)")
    void webhook_deveRetornar401_quandoSecretInvalido() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("50.00"));

        enviaWebhook("segredo-errado", txId, "APPROVED")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Não autorizado"));

        // A transação NÃO foi tocada
        assertThat(transactionRepository.findById(UUID.fromString(txId)).orElseThrow().getStatus())
            .isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("webhook: deve retornar 401 quando o header X-Webhook-Secret está ausente")
    void webhook_deveRetornar401_quandoHeaderAusente() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("30.00"));

        mockMvc.perform(post("/api/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("transactionId", txId, "event", "APPROVED"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("webhook: aprovado — liquida com partidas e só ENTÃO o saldo muda")
    void webhook_deveLiquidarComPartidasESaldo_quandoAprovado() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("250.00"));

        // PENDING: intenção de depósito, saldo ainda ZERO
        assertThat(saldo()).isEqualByComparingTo("0");

        enviaWebhook(webhookSecret, txId, "APPROVED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.entries.length()").value(2));

        // COMPLETED: partidas criadas, saldo reflete a liquidação
        assertThat(saldo()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("webhook: retentativa idempotente — reenviar a mesma decisão não duplica partidas")
    void webhook_deveSerIdempotente_naRetentativa() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("120.00"));

        enviaWebhook(webhookSecret, txId, "APPROVED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // O provedor reenviou porque... porque sim (redes falham). Segunda entrega:
        enviaWebhook(webhookSecret, txId, "APPROVED")
            .andExpect(status().isOk()) // 200 de novo → provedor para de reenviar
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.entries.length()").value(2)); // 2, e NÃO 4

        assertThat(saldo()).isEqualByComparingTo("120.00"); // e não 240
    }

    @Test
    @DisplayName("webhook: rejeitado — transação vira FAILED e o saldo continua zero")
    void webhook_deveMarcarFAILED_quandoRejeitado() throws Exception {
        String txId = criaDepositoPendente(new BigDecimal("90.00"));

        enviaWebhook(webhookSecret, txId, "REJECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.entries.length()").value(0));

        assertThat(saldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("webhook: deve retornar 400 quando a transação não existe")
    void webhook_deveRetornar400_quandoTransacaoNaoEncontrada() throws Exception {
        enviaWebhook(webhookSecret, UUID.randomUUID().toString(), "APPROVED")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }
}