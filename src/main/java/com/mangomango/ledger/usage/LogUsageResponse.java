// Generated using Claude Code.

package com.mangomango.ledger.usage;

import com.mangomango.ledger.budget.BudgetStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Result of reporting one call.
 *
 * @param recordId ID of the stored usage record
 * @param replayed true when the idempotency key was already seen and the original is returned
 * @param cost dollar cost computed from the price in effect at the request timestamp
 * @param pricingVersion catalog version used to compute the cost
 * @param budgets current status of every budget rule that applies to this call
 * @param budgetThresholdBreached true when any applicable rule is past an alert threshold
 * @param alertTriggered true when this call crossed a threshold for the first time this period
 */
public record LogUsageResponse(
    UUID recordId,
    boolean replayed,
    BigDecimal cost,
    int pricingVersion,
    List<BudgetStatus> budgets,
    boolean budgetThresholdBreached,
    boolean alertTriggered) {}
