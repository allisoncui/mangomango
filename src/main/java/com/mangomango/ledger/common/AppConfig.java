package com.mangomango.ledger.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

/** Shared infrastructure beans. */
@Configuration
@EnableAsync
public class AppConfig {

  private static final int WEBHOOK_TIMEOUT_MS = 3000;

  /** Clock used for all time-dependent logic, injectable for tests. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /** HTTP client for webhook delivery, using the app's JSON settings and short timeouts. */
  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(WEBHOOK_TIMEOUT_MS);
    factory.setReadTimeout(WEBHOOK_TIMEOUT_MS);
    // Buffering sends a Content-Length header instead of chunked encoding, which some simple
    // webhook receivers reject.
    return builder
        .requestFactory(new BufferingClientHttpRequestFactory(factory))
        .build();
  }
}
