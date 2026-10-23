// Generated using Claude Code.

package com.mangomango.ledger.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spending against one budget rule in its current period.
 *
 * @param ruleId the rule
 * @param scope what the rule counts
 * @param scopeValue project tag or provider, null for client-wide rules
 * @param periodStart start of the current period (inclusive)
 * @param periodEnd end of the current period (exclusive)
 * @param limit spending limit for the period
 * @param spent spending so far in the period
 * @param remaining limit minus spent, never below zero
 * @param percentUsed spent as a percentage of limit
 * @param crossedThresholds every configured threshold at or below percentUsed
 * @param newlyCrossedThresholds thresholds crossed for the first time by this request
 */
public record BudgetStatus(
    UUID ruleId,
    BudgetScope scope,
    String scopeValue,
    Instant periodStart,
    Instant periodEnd,
    BigDecimal limit,
    BigDecimal spent,
    BigDecimal remaining,
    BigDecimal percentUsed,
    List<Integer> crossedThresholds,
    List<Integer> newlyCrossedThresholds) {

  /** Returns true when at least one alert threshold is crossed. */
  public boolean anyThresholdCrossed() {
    return !crossedThresholds.isEmpty();
  }
}
