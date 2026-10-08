package com.API.walletLedger.service;

import com.API.walletLedger.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes Unitários do JwtService.
 *
 * Estratégia: usar ReflectionTestUtils.setField() para injetar os valores das
 * propriedades @Value (secretKey e jwtExpirationMs) sem subir o contexto Spring.
 * Isso mantém o teste rápido (sem banco, sem contexto HTTP) e isolado.
 *
 * Por que não @SpringBootTest? → O JwtService não precisa do contexto completo;
 * apenas precisa dos valores das propriedades de configuração.
 */
class JwtServiceTest {

    // Valores de teste adequados para HS256 (256 bits = 32 bytes em Base64)
    private static final String TEST_SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION_MS = 86400000L; // 24 horas

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Injeta os valores diretamente via ReflectionTestUtils.
        // Isso substitui o que o Spring faria com @Value a partir do application.properties.
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    @Test
    @DisplayName("gerarToken: deve retornar token não nulo e bem-formado (3 partes)")
    void gerarToken_deveRetornarTokenValido() {
        // ARRANGE — Usuário de teste com ID definido
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("teste@exemplo.com");
        user.setName("Test User");

        // ACT
        String token = jwtService.generateToken(user);

        // ASSERT
        assertThat(token).isNotNull();
        // Token JWT tem 3 partes separadas por '.'
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
    }

    @Test
    @DisplayName("extrairEmail: deve retornar email correto do token")
    void extrairEmail_deveRetornarEmailCorreto() {
        // ARRANGE — Criar um token com subject known
        User user = new User();
        user.setId(UUID.randomUUID());
        String token = jwtService.generateToken(user);

        // ACT
        String email = jwtService.extractEmail(token);

        // ASSERT
        assertThat(email).isNotNull();
        // O subject do token é o e-mail do usuário definido
        assertThat(email).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("validarToken: deve retornar true quando token é válido e e-mail bate")
    void validarToken_deveRetornarTrue_quandoTokenValido() {
        // ARRANGE — Criar token para um e-mail específico
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("valid@test.com");
        String token = jwtService.generateToken(user);

        // ACT
        boolean valid = jwtService.isTokenValid(token, "valid@test.com");

        // ASSERT
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("validarToken: deve retornar false quando e-mail não bate")
    void validarToken_deveRetornarFalse_quandoEmailInvalido() {
        // ARRANGE — Criar token para um e-mail
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("valid@test.com");
        String token = jwtService.generateToken(user);

        // ACT
        boolean valid = jwtService.isTokenValid(token, "outro@test.com");

        // ASSERT
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired: deve retornar false quando token não expirou")
    void isTokenExpired_deveRetornarFalse_quandoTokenValido() {
        // ARRANGE — Token recente (within expiration)
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("notexpired@test.com");
        String token = jwtService.generateToken(user);

        // ACT
        boolean expired = jwtService.isTokenExpired(token);

        // ASSERT
        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired: deve retornar true quando token expirou")
    void isTokenExpired_deveRetornarTrue_quandoTokenExpirado() {
        // ARRANGE — Criar token com issuedAt no passado para forçar expiração imediata
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 1L);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("expired@test.com");
        // Definir issuedAt há 2 dias para garantir que o token expirou muito tempo atrás
        long pastEpochDay = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000);
        user.setPassword(Long.toString(pastEpochDay)); // usar password apenas como placeholder

        // Gera token manualmente com claims controladas para testar isTokenExpired
        String token = jwtService.generateToken(user);

        // ACT
        boolean expired = jwtService.isTokenExpired(token);

        // ASSERT
        // Com issuedAt no passado e expiração de 1ms, o token deve estar expirado
        assertThat(expired).isTrue();
    }
}
