package com.API.walletLedger.controller;

import com.API.walletLedger.dto.UserRegistrationRequest;
import com.API.walletLedger.dto.UserResponse;
import com.API.walletLedger.service.UserService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de autorização "dono-de-conta" nas ESCRITAS (Revisão de Segurança — S2).
 *
 * O cenário mais grave da revisão: transferência com sourceAccountId de terceiro
 * (roubo de fundos via IDOR). Sem a guarda, todos os testes 403 abaixo falhavam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionOwnershipIntegrationTest {

    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private String tokenA;
    private String tokenB;
    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() throws Exception {
        UUID salt = UUID.randomUUID();
        String emailA = "escrita-a-" + salt + "@wallet.test";
        String emailB = "escrita-b-" + salt + "@wallet.test";

        UserResponse userA = userService.register(new UserRegistrationRequest("Usuário A", emailA, PASSWORD));
        UserResponse userB = userService.register(new UserRegistrationRequest("Usuário B", emailB, PASSWORD));
        accountA = userA.defaultAccountId();
        accountB = userB.defaultAccountId();

        tokenA = login(emailA);
        tokenB = login(emailB);
    }

    // ---------- Helpers ----------

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private void depositaProprio(String token, BigDecimal valor) throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountA.toString(),
                    "amount", valor))))
            .andExpect(status().isCreated());
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("transfer: 403 quando a CONTA DE ORIGEM é de outro usuário (IDOR crítico bloqueado)")
    void transfer_deveRetornar403_quandoOrigemEdeOutroUsuario() throws Exception {
        // O ataque original: A tenta drenar a conta de B usando o próprio token
        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "sourceAccountId", accountB.toString(),   // ← conta da vítima
                    "targetAccountId", accountA.toString(),   // ← carteira do atacante
                    "amount", new BigDecimal("1000.00")))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("deposit: 403 quando o destino é conta de outro usuário")
    void deposit_deveRetornar403_quandoDestinoEdeOutroUsuario() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountB.toString(),   // crédito forjado em conta alheia
                    "amount", new BigDecimal("50.00")))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("deposit-async: 403 quando o destino é conta de outro usuário")
    void depositAsync_deveRetornar403_quandoDestinoEdeOutroUsuario() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit-async")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountB.toString(),
                    "amount", new BigDecimal("50.00")))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("reverse: 403 quando a transação não envolve o usuário")
    void reverse_deveRetornar403_quandoTransacaoNaoEnvolveUsuario() throws Exception {
        // A faz um depósito próprio e B tenta estorná-lo
        String depositJson = mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountA.toString(),
                    "amount", new BigDecimal("80.00")))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String depositId = objectMapper.readTree(depositJson).path("id").asText();

        mockMvc.perform(post("/api/transactions/reverse")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "transactionId", depositId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("transfer: 201 quando a origem é própria (regressão — destino de terceiro é permitido)")
    void transfer_deveRetornar201_quandoOrigemEPropria() throws Exception {
        depositaProprio(tokenA, new BigDecimal("300.00"));

        // Enviar para OUTRA conta continua sendo a feature — só a origem precisa ser sua
        mockMvc.perform(post("/api/transactions/transfer")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "sourceAccountId", accountA.toString(),
                    "targetAccountId", accountB.toString(),
                    "amount", new BigDecimal("100.00")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}