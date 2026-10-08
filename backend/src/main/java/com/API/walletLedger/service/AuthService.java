package com.API.walletLedger.service;

import com.API.walletLedger.domain.User;
import com.API.walletLedger.dto.LoginRequest;
import com.API.walletLedger.dto.TokenResponse;
import com.API.walletLedger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public TokenResponse login(LoginRequest request) {
        // 1. Tenta autenticar credenciais (email e senha) via Spring Security
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // 2. Busca o usuário completo no banco de dados
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + request.email()));

        // 3. Emite o token JWT assinado
        String token = jwtService.generateToken(user);

        return new TokenResponse(token, jwtService.getExpirationInSeconds());
    }
}
