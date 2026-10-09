package com.API.walletLedger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Ativa o @Scheduled (worker B2b) — sem esta anotação ele é IGNORADO em silêncio
public class WalletLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletLedgerApplication.class, args);
	}

}
