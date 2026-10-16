package com.mangomango.ledger.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A stored budget rule with its current spending.
 *
 * @param status {@code created}, {@code updated} or {@code ok}
 * @param ruleId rule ID
 * @param scope what spending the rule counts
 * @param scopeValue project tag or provider, null for client-wide rules
 * @param period reset period
 * @param limitAmount spending limit per period
 * @param thresholds alert thresholds in percent
 * @param callbackUrl webhook URL, or null
 * @param currentSpend spending so far in the current period
 * @param percentConsumed current spending as a percentage of the limit
 * @param crossedThresholds thresholds already crossed in the current period
 */
public record BudgetRuleResponse(
    String status,
    UUID ruleId,
    BudgetScope scope,
    String scopeValue,
    BudgetPeriod period,
    BigDecimal limitAmount,
    List<Integer> thresholds,
    String callbackUrl,
    BigDecimal currentSpend,
    BigDecimal percentConsumed,
    List<Integer> crossedThresholds) {

  /** Combines a rule with its current status. */
  public static BudgetRuleResponse of(String status, BudgetRule rule, BudgetStatus current) {
    return new BudgetRuleResponse(
        status,
        rule.getId(),
        rule.getScope(),
        rule.getScopeValue(),
        rule.getPeriod(),
        rule.getLimitAmount(),
        rule.getThresholds(),
        rule.getCallbackUrl(),
        current.spent(),
        current.percentUsed(),
        current.crossedThresholds());
  }
}
