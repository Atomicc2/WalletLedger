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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de autorização "dono-de-conta" nas LEITURAS (Revisão de Segurança — S1).
 *
 * Cenário IDOR: um usuário autenticado tentando ler saldo/extrato de OUTRA conta.
 * Sem o OwnershipGuard isso era possível (apenas 401 sem token era bloqueado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountOwnershipIntegrationTest {

    private static final String PASSWORD = "senha123";
    private static final UUID SYSTEM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private String tokenA;
    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() throws Exception {
        UUID salt = UUID.randomUUID();
        UserResponse userA = userService.register(
            new UserRegistrationRequest("Usuário A", "dono-a-" + salt + "@wallet.test", PASSWORD)
        );
        UserResponse userB = userService.register(
            new UserRegistrationRequest("Usuário B", "dono-b-" + salt + "@wallet.test", PASSWORD)
        );
        accountA = userA.defaultAccountId();
        accountB = userB.defaultAccountId();

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "dono-a-" + salt + "@wallet.test", "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        tokenA = objectMapper.readTree(response).path("accessToken").asText();
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("balance: deve retornar 200 quando a conta é do próprio usuário")
    void balance_deveRetornar200_quandoContaEPropria() throws Exception {
        mockMvc.perform(get("/api/accounts/" + accountA + "/balance")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountA.toString()));
    }

    @Test
    @DisplayName("balance: deve retornar 403 quando a conta pertence a outro usuário (IDOR bloqueado)")
    void balance_deveRetornar403_quandoContaEdeOutroUsuario() throws Exception {
        mockMvc.perform(get("/api/accounts/" + accountB + "/balance")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("balance: deve retornar 403 para a conta SYSTEM (não tem dono)")
    void balance_deveRetornar403_quandoContaEDoSistema() throws Exception {
        mockMvc.perform(get("/api/accounts/" + SYSTEM_ACCOUNT_ID + "/balance")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("statement: deve retornar 403 quando o extrato é de outro usuário (vazamento bloqueado)")
    void statement_deveRetornar403_quandoContaEdeOutroUsuario() throws Exception {
        mockMvc.perform(get("/api/accounts/" + accountB + "/statement")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    @DisplayName("balance: deve retornar 400 quando a conta não existe (mantém o mapeamento atual)")
    void balance_deveRetornar400_quandoContaNaoExiste() throws Exception {
        mockMvc.perform(get("/api/accounts/" + UUID.randomUUID() + "/balance")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Conta não encontrada")));
    }

    @Test
    @DisplayName("statement: deve retornar 401 quando não há token")
    void statement_deveRetornar401_quandoSemToken() throws Exception {
        mockMvc.perform(get("/api/accounts/" + accountA + "/statement"))
            .andExpect(status().isUnauthorized());
    }
}