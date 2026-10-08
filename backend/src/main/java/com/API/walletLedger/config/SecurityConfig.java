package com.API.walletLedger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
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
                // Qualquer outro endpoint exige autenticação
                .anyRequest().authenticated()
            )
            .build();
    }
}
