package com.mangomango.ledger.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating or replacing a budget rule.
 *
 * @param scope what spending the rule counts
 * @param scopeValue project tag or provider name; must be absent for client-wide rules
 * @param period reset period
 * @param limitAmount spending limit in dollars per period
 * @param thresholds alert thresholds as percentages of the limit, e.g. 50, 80, 100
 * @param callbackUrl optional http(s) URL notified when a threshold is first crossed
 */
public record BudgetRuleRequest(
    @NotNull BudgetScope scope,
    String scopeValue,
    @NotNull BudgetPeriod period,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal limitAmount,
    @NotEmpty List<@NotNull @Min(1) @Max(1000) Integer> thresholds,
    String callbackUrl) {}
