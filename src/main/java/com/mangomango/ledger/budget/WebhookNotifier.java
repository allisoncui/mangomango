// Generated using Claude Code.

package com.mangomango.ledger.budget;

/** Delivers budget alerts to client callback URLs. */
public interface WebhookNotifier {

  /** Sends one alert; implementations must not throw on delivery failure. */
  void send(BudgetAlertEvent alert);
}
