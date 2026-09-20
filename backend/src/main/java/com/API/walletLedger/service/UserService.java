package com.API.walletLedger.service;

import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.AccountStatus;
import com.API.walletLedger.domain.AccountType;
import com.API.walletLedger.domain.User;
import com.API.walletLedger.dto.UserRegistrationRequest;
import com.API.walletLedger.dto.UserResponse;
import com.API.walletLedger.repository.AccountRepository;
import com.API.walletLedger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email já cadastrado");
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(encodedPassword);
        User savedUser = userRepository.save(user);

        Account account = new Account();
        account.setUser(savedUser);
        account.setAccountType(AccountType.USER);
        account.setCurrency("BRL");
        account.setStatus(AccountStatus.ACTIVE);
        Account savedAccount = accountRepository.save(account);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt(),
                savedAccount.getId()
        );
    }
}