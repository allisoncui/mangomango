package com.mangomango.ledger.budget;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends alerts only after the usage record that triggered them has been committed. */
@Component
public class BudgetAlertDispatcher {

  private final WebhookNotifier notifier;

  /** Creates the dispatcher. */
  public BudgetAlertDispatcher(WebhookNotifier notifier) {
    this.notifier = notifier;
  }

  /** Delivers an alert asynchronously once the transaction commits. */
  @Async
  @TransactionalEventListener
  public void onAlert(BudgetAlertEvent alert) {
    notifier.send(alert);
  }
}
