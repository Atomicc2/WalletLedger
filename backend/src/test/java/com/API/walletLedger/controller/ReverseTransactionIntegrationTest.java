package com.API.walletLedger.controller;

import com.API.walletLedger.domain.Transaction;
import com.API.walletLedger.domain.TransactionStatus;
import com.API.walletLedger.dto.UserRegistrationRequest;
import com.API.walletLedger.dto.UserResponse;
import com.API.walletLedger.repository.TransactionRepository;
import com.API.walletLedger.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do estorno de transações (Fase 3.2).
 *
 * Fluxo real com token JWT e persistência no PostgreSQL (rollback por teste).
 * Valida: estorno feliz com partidas invertidas, estorno duplo (409),
 * saldo insuficiente (400) e proteção JWT (401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReverseTransactionIntegrationTest {

    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionRepository transactionRepository;

    private String tokenA;
    private String tokenB;
    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() throws Exception {
        UUID salt = UUID.randomUUID();
        tokenA = registraELoga("Usuário A", "usuario-a-" + salt + "@wallet.test");
        tokenB = registraELoga("Usuário B", "usuario-b-" + salt + "@wallet.test");
    }

    private String registraELoga(String nome, String email) throws Exception {
        UserResponse user = userService.register(new UserRegistrationRequest(nome, email, PASSWORD));
        UUID accountId = user.defaultAccountId();
        if (accountA == null) accountA = accountId; else accountB = accountId;

        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        return login.path("accessToken").asText();
    }

    private JsonNode deposita(String token, UUID accountId, BigDecimal valor, String chave) throws Exception {
        String response = mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", chave,
                    "targetAccountId", accountId.toString(),
                    "amount", valor))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode transfere(String token, UUID origem, UUID destino, BigDecimal valor, String chave) throws Exception {
        String response = mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", chave,
                    "sourceAccountId", origem.toString(),
                    "targetAccountId", destino.toString(),
                    "amount", valor))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("reverse: estorno feliz — devolve o valor com partidas invertidas e marca a original como REVERSED")
    void reverse_deveRetornar201_comPartidasInvertidas_eOriginalReversed() throws Exception {
        // A recebe 200 e transfere 100 para B → saldos: A=100, B=100
        deposita(tokenA, accountA, new BigDecimal("200.00"), UUID.randomUUID().toString());
        JsonNode transferencia = transfere(tokenA, accountA, accountB, new BigDecimal("100.00"), UUID.randomUUID().toString());
        String transferId = transferencia.path("id").asText();

        // Estorno da transferência: B devolve 100 para A
        String response = mockMvc.perform(post("/api/transactions/reverse")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", transferId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andReturn().getResponse().getContentAsString();

        JsonNode estorno = objectMapper.readTree(response);

        // A transação de estorno referencia a original (auditoria)
        Transaction original = transactionRepository.findById(UUID.fromString(transferId)).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(original.getReversalOf()).isNull();

        UUID reversalId = UUID.fromString(estorno.path("id").asText());
        Transaction reversalTx = transactionRepository.findById(reversalId).orElseThrow();
        assertThat(reversalTx.getReversalOf()).isNotNull();
        assertThat(reversalTx.getReversalOf().getId()).isEqualTo(UUID.fromString(transferId));
        assertThat(reversalTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Partidas invertidas: CREDIT em A (devolvido) e DEBIT em B (quem devolveu)
        assertThat(estorno.path("entries")).hasSize(2);
        JsonNode entradas = estorno.path("entries");
        boolean temCreditA = false;
        boolean temDebitB = false;
        for (JsonNode entrada : entradas) {
            if (entrada.path("entryType").asText().equals("CREDIT")
                && entrada.path("accountId").asText().equals(accountA.toString())) {
                temCreditA = true;
            }
            if (entrada.path("entryType").asText().equals("DEBIT")
                && entrada.path("accountId").asText().equals(accountB.toString())) {
                temDebitB = true;
            }
        }
        assertThat(temCreditA).isTrue();
        assertThat(temDebitB).isTrue();
    }

    @Test
    @DisplayName("reverse: deve retornar 409 quando a transação já foi estornada")
    void reverse_deveRetornar409_quandoTransacaoJaEstornada() throws Exception {
        deposita(tokenA, accountA, new BigDecimal("100.00"), UUID.randomUUID().toString());
        String transferId = transfere(tokenA, accountA, accountB, new BigDecimal("50.00"), UUID.randomUUID().toString())
            .path("id").asText();

        // Primeiro estorno: ok
        mockMvc.perform(post("/api/transactions/reverse")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", transferId))))
            .andExpect(status().isCreated());

        // Segundo estorno (chave diferente): a original já está REVERSED → 409
        mockMvc.perform(post("/api/transactions/reverse")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", transferId))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflito de estado"))
            .andExpect(jsonPath("$.detail").value("Apenas transações COMPLETED podem ser estornadas."));
    }

    @Test
    @DisplayName("reverse: deve retornar 400 quando o saldo é insuficiente para devolver")
    void reverse_deveRetornar400_quandoSaldoInsuficiente() throws Exception {
        // A recebe 100 e transfere tudo para B → A fica com 0
        JsonNode deposito = deposita(tokenA, accountA, new BigDecimal("100.00"), UUID.randomUUID().toString());
        transfere(tokenA, accountA, accountB, new BigDecimal("100.00"), UUID.randomUUID().toString());

        // Estornar o DEPÓSITO exige que A devolva 100, mas A está zerado → 400
        mockMvc.perform(post("/api/transactions/reverse")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", deposito.path("id").asText()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Requisição inválida"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Saldo insuficiente para estorno")));
    }

    @Test
    @DisplayName("reverse: deve retornar 401 quando não há token")
    void reverse_deveRetornar401_quandoSemToken() throws Exception {
        deposita(tokenA, accountA, new BigDecimal("100.00"), UUID.randomUUID().toString());
        JsonNode deposito = deposita(tokenA, accountA, new BigDecimal("50.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transactions/reverse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", deposito.path("id").asText()))))
            .andExpect(status().isUnauthorized());
    }
}