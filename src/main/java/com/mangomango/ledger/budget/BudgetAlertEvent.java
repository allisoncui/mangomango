// Generated using Claude Code.

package com.mangomango.ledger.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload sent to a rule's callback URL when a threshold is first crossed in a period.
 *
 * @param clientId owning client
 * @param ruleId the rule whose threshold was crossed
 * @param thresholdPercent the threshold crossed
 * @param percentUsed current spending as a percentage of the limit
 * @param spent current spending in the period
 * @param limit spending limit for the period
 * @param periodStart start of the period
 * @param callbackUrl where the payload is delivered
 */
public record BudgetAlertEvent(
    String clientId,
    UUID ruleId,
    int thresholdPercent,
    BigDecimal percentUsed,
    BigDecimal spent,
    BigDecimal limit,
    Instant periodStart,
    String callbackUrl) {}
