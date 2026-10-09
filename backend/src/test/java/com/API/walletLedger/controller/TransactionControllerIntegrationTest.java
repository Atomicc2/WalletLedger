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
 * Testes de Integração ponta a ponta da camada web (Bloco 4).
 *
 * Por que "IntegrationTest" e não "IT"?
 * → O Surefire (plugin do `mvn test`) só executa classes *Test/*Tests por padrão;
 *   o sufixo "IT" pertence ao Failsafe (`mvn verify`), que não está configurado
 *   neste projeto. Com "IT", o teste nunca rodaria.
 *
 * @SpringBootTest        → sobe o contexto completo (security, controllers, services, JPA).
 * @AutoConfigureMockMvc  → disponibiliza o MockMvc, que faz requisições HTTP simuladas
 *                          dentro do processo, sem abrir porta de rede.
 * @Transactional         → cada teste roda numa transação com rollback no final,
 *                          então o Postgres real não acumula dados de teste.
 *
 * Observação: o @Transactional vale porque o MockMvc executa na mesma thread do teste,
 * então o controller/service "enxergam" os dados criados no @BeforeEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerIntegrationTest {

    private static final String EMAIL = "integracao@wallet.test";
    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        // Fluxo real de cadastro: cria usuário + conta padrão e devolve o id da conta
        UserResponse user = userService.register(
            new UserRegistrationRequest("Usuário de Integração", EMAIL, PASSWORD)
        );
        accountId = user.defaultAccountId();
    }

    // ---------- Helpers ----------

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private String depositBody(UUID targetAccountId) throws Exception {
        return toJson(Map.of(
            "idempotencyKey", UUID.randomUUID().toString(),
            "targetAccountId", targetAccountId.toString(),
            "amount", new BigDecimal("100.00"),
            "description", "Depósito de teste de integração"
        ));
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("deposit: deve retornar 401 quando não há token")
    void deposit_deveRetornar401_quandoSemToken() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(depositBody(accountId)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deposit: deve retornar 201 e duas partidas contábeis quando o token é válido")
    void deposit_deveRetornar201_quandoTokenValido() throws Exception {
        String token = loginAndGetToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(depositBody(accountId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            // Dupla entrada: uma perna DEBIT (SYSTEM) e uma CREDIT (usuário)
            .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    @DisplayName("deposit-async: deve retornar 202 com status PENDING e SEM partidas contábeis")
    void depositAsync_deveRetornar202_comStatusPending_semPartidas() throws Exception {
        String token = loginAndGetToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/transactions/deposit-async")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(depositBody(accountId)))
            .andExpect(status().isAccepted()) // 202: "recebi, ainda não terminei"
            .andExpect(jsonPath("$.status").value("PENDING"))
            // Sem partidas: o saldo do usuário ainda não mudou
            .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    @DisplayName("deposit-async: deve retornar 401 quando não há token")
    void depositAsync_deveRetornar401_quandoSemToken() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(depositBody(accountId)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login: deve retornar 200 com token Bearer quando as credenciais são válidas")
    void login_deveRetornar200EToken_quandoCredenciaisValidas() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("login: deve retornar 401 quando a senha está errada")
    void login_deveRetornar401_quandoSenhaErrada() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", EMAIL, "password", "senha-errada"))))
            .andExpect(status().isUnauthorized());
    }
}
