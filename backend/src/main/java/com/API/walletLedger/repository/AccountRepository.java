package com.API.walletLedger.repository;

import com.API.walletLedger.domain.Account;
import com.API.walletLedger.domain.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserId(UUID userId);

    Optional<Account> findByAccountType(AccountType accountType);
}
