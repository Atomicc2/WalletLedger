package com.API.walletLedger.exception;

import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.AccountStatus;
import com.API.walletLedger.dto.UserRegistrationRequest;
import com.API.walletLedger.dto.UserResponse;
import com.API.walletLedger.repository.AccountRepository;
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
 * Testes de integração do tratamento global de exceções (Fase 3.3).
 *
 * Verifica que as exceções de negócio são traduzidas para o status HTTP correto
 * e para o corpo padrão RFC 7807 (ProblemDetail: title, status, detail).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerIntegrationTest {

    private static final String EMAIL = "handlers@wallet.test";
    private static final String PASSWORD = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountRepository accountRepository;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        UserResponse user = userService.register(
            new UserRegistrationRequest("Usuário Handler", EMAIL, PASSWORD)
        );
        accountId = user.defaultAccountId();
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String loginToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    // ---------- Cenários ----------

    @Test
    @DisplayName("registro: deve retornar 400 com os erros de validação por campo")
    void registro_deveRetornar400_comErrosDeValidacao() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("name", "", "email", "nao-e-email", "password", "123"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Erro de validação"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("registro: deve retornar 400 quando o e-mail já está cadastrado")
    void registro_deveRetornar400_quandoEmailDuplicado() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("name", "Duplicado", "email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Requisição inválida"))
            .andExpect(jsonPath("$.detail").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("depósito: deve retornar 409 quando a conta de destino está bloqueada")
    void deposito_deveRetornar409_quandoContaBloqueada() throws Exception {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);

        String token = loginToken();

        mockMvc.perform(post("/api/transactions/deposit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                    "idempotencyKey", UUID.randomUUID().toString(),
                    "targetAccountId", accountId.toString(),
                    "amount", new BigDecimal("50.00")))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflito de estado"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("login: deve retornar 401 com ProblemDetail quando a senha está errada")
    void login_deveRetornar401_comProblemDetail_quandoSenhaErrada() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", EMAIL, "password", "senha-errada"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Não autorizado"))
            .andExpect(jsonPath("$.detail").value("Credenciais inválidas."));
    }
}
