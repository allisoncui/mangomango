package com.mangomango.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the provider-neutral AI usage ledger service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LedgerApplication {

  /** Starts the service. */
  public static void main(String[] args) {
    SpringApplication.run(LedgerApplication.class, args);
  }
}
