package com.API.walletLedger.service;

import com.API.walletLedger.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes Unitários do JwtService.
 *
 * Por que ReflectionTestUtils em vez de @SpringBootTest?
 * → Inicializa o JwtService diretamente (new JwtService()).
 * → Usa ReflectionTestUtils.setField() para injetar manualmente os valores
 *   que o @Value do Spring injetaria (secretKey e jwtExpirationMs),
 *   sem subir o contexto Spring completo (sem banco, sem rede, sem servlet).
 * → Roda em milissegundos.
 *
 * Por que não usamos @Value nos campos desta classe de teste?
 * → Campos @Value só são injetados pelo container Spring; sem um contexto
 *   ativo (ex.: @SpringBootTest), eles permanecem null/0 e o teste quebra
 *   com "Decode argument cannot be null". Por isso os valores são fixos
 *   em constantes locais.
 *
 * Convenção do arquivo: as constantes TEST_SECRET/TEST_EXPIRATION_MS são
 * usadas exclusivamente aqui — a chave real de produção fica apenas em
 * application.properties.
 */
class JwtServiceTest {

    /** Chave Base64 de 32 bytes (256 bits) — exigida pelo HMAC-SHA256 do JJWT. */
    private static final String TEST_SECRET = "qx4PzqIUIfuf1DZ0mW0hF4jc8CcWYtAmMnJHRgGPoE4=";
    /** Expiração de 1 hora em milissegundos. */
    private static final long TEST_EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Injeta os valores que o @Value do Spring injetaria em produção.
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    /** Monta um User completo — generateToken exige id não nulo (user.getId().toString()). */
    private User newUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail(email);
        return user;
    }

    @Test
    @DisplayName("gerarToken: deve retornar token não nulo e bem-formado com 3 partes")
    void gerarToken_deveRetornarTokenValido() {
        // ARRANGE
        User user = newUser("teste@exemplo.com");

        // ACT
        String token = jwtService.generateToken(user);

        // ASSERT
        assertThat(token).isNotNull();
        // Token JWT é composto por 3 partes separadas por '.': header.payload.assinatura
        assertThat(token.split("\\.")).hasSize(3);
        // O subject (e-mail) precisa ser recuperável a partir do token.
        // Não validamos o payload em texto puro porque ele é codificado em base64url.
        assertThat(jwtService.extractEmail(token)).isEqualTo("teste@exemplo.com");
    }

    @Test
    @DisplayName("extrairEmail: deve retornar o e-mail correto do token")
    void extrairEmail_deveRetornarEmailCorreto() {
        // ARRANGE
        String token = jwtService.generateToken(newUser("alice@exemplo.com"));

        // ACT
        String email = jwtService.extractEmail(token);

        // ASSERT
        assertThat(email).isEqualTo("alice@exemplo.com");
    }

    @Test
    @DisplayName("validarToken: deve retornar true quando token é válido e e-mail bate")
    void validarToken_deveRetornarTrue_quandoTokenValido() {
        // ARRANGE
        String token = jwtService.generateToken(newUser("valid@test.com"));

        // ACT
        boolean valid = jwtService.isTokenValid(token, "valid@test.com");

        // ASSERT
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("validarToken: deve retornar false quando o e-mail não bate")
    void validarToken_deveRetornarFalse_quandoEmailNaoBate() {
        // ARRANGE
        String token = jwtService.generateToken(newUser("valid@test.com"));

        // ACT
        boolean valid = jwtService.isTokenValid(token, "outro@test.com");

        // ASSERT
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("validarToken: deve retornar false quando token expirado")
    void validarToken_deveRetornarFalse_quandoTokenExpirado() {
        // ARRANGE — expiração negativa faz o token nascer já vencido
        // (expiration = now - 1000ms), sem precisar de Thread.sleep.
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1_000L);
        String token = jwtService.generateToken(newUser("expired@test.com"));

        // ACT
        boolean valid = jwtService.isTokenValid(token, "expired@test.com");

        // ASSERT
        assertThat(valid).isFalse();
        // Comportamento real do JJWT: parsear claims de token vencido lança
        // ExpiredJwtException. O isTokenValid funciona porque internamente
        // captura JwtException e devolve false — é o caminho seguro.
        assertThatThrownBy(() -> jwtService.isTokenExpired(token))
            .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("isTokenExpired: deve retornar false quando token está dentro da validade")
    void isTokenExpired_deveRetornarFalse_quandoTokenValido() {
        // ARRANGE
        String token = jwtService.generateToken(newUser("notexpired@test.com"));

        // ACT
        boolean expired = jwtService.isTokenExpired(token);

        // ASSERT
        assertThat(expired).isFalse();
    }
}
