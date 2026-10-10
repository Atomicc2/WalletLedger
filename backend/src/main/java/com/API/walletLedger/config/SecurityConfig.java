package com.API.walletLedger.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // CORS deve vir ANTES do csrf/authorizeHttpRequests para que preflight (OPTIONS) passe
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 1. Desabilita proteção CSRF (Cross-Site Request Forgery)
            .csrf(csrf -> csrf.disable())

            // 2. Define a política de sessão como STATELESS (sem cookies de sessão em memória)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 3. Regras de autorização por URL
            .authorizeHttpRequests(authorize -> authorize
                // Permite cadastro de novos usuários sem autenticação prévia
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                // Futura rota de login pública
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // Webhook do provedor de pagamento (Fase 3.1): o provedor NÃO tem
                // nosso JWT, então a rota é pública e se protege pelo header
                // X-Webhook-Secret (validado no WebhookController).
                .requestMatchers("/api/webhooks/**").permitAll()
                // Qualquer outro endpoint exige autenticação
                .anyRequest().authenticated()
            )

            // 4. Ponto de entrada de autenticação:
            //    por padrão o Spring Security usa Http403ForbiddenEntryPoint, que devolve
            //    403 (Forbidden) para requisições sem autenticação. Em uma API stateless o
            //    correto é 401 (Unauthorized) — "você não se autenticou" (o 403 fica para
            //    "autenticou, mas não tem permissão"). O BadCredentialsException do login
            //    também cai aqui, então credenciais erradas passam a responder 401.
            .exceptionHandling(handling -> handling.authenticationEntryPoint(
                (request, response, authException) ->
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            ))

            // 5. Registra o filtro JWT antes do filtro padrão de autenticação por usuário/senha
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /**
     * Configuração CORS para permitir o frontend (Vite dev server: localhost:5173)
     * acessar a API.
     *
     * Por que precisa? Navegador bloqueia requests cross-origin (porta diferente)
     * a menos que o servidor responda headers Access-Control-Allow-Origin, etc.
     *
     * Em produção: troque "http://localhost:5173" pela URL real do frontend (ex.: Vercel).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true); // necessário para cookies; com Bearer token é opcional mas inofensivo
        config.setMaxAge(3600L); // cache do preflight (OPTIONS) por 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
