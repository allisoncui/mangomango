package com.mangomango.ledger.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Posts alerts as JSON to the rule's callback URL; failures are logged, not retried. */
@Component
public class HttpWebhookNotifier implements WebhookNotifier {

  private static final Logger LOG = LoggerFactory.getLogger(HttpWebhookNotifier.class);

  private final RestClient restClient;

  /** Creates the notifier. */
  public HttpWebhookNotifier(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public void send(BudgetAlertEvent alert) {
    try {
      restClient
          .post()
          .uri(alert.callbackUrl())
          .contentType(MediaType.APPLICATION_JSON)
          .body(alert)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException | IllegalArgumentException ex) {
      LOG.warn("Budget webhook to {} failed: {}", alert.callbackUrl(), ex.getMessage());
    }
  }
}
