package com.mangomango.ledger.savings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Result of re-pricing recorded usage under alternative models.
 *
 * @param start range start
 * @param end range end
 * @param projectTag project filter, or null
 * @param requestCount number of recorded calls analyzed
 * @param actualCost what the recorded calls cost
 * @param largestRequestTokens largest input plus output tokens of any one call
 * @param highestCapabilityTierUsed highest capability tier among models actually used
 * @param alternatives qualifying alternatives, cheapest first
 * @param excluded alternatives that did not qualify, with reasons
 */
public record SavingsResponse(
    Instant start,
    Instant end,
    String projectTag,
    long requestCount,
    BigDecimal actualCost,
    long largestRequestTokens,
    int highestCapabilityTierUsed,
    List<Alternative> alternatives,
    List<Excluded> excluded) {

  /**
   * One alternative model's projected cost.
   *
   * @param provider provider name
   * @param modelId model identifier
   * @param projectedCost cost of the same token volumes under this model
   * @param savings actual minus projected cost; negative means more expensive
   * @param savingsPercent savings as a percentage of actual cost
   * @param averageLatencyMs client's recorded average latency for this model in the range, or
   *     null
   * @param currentlyUsed true if the client used this model in the range
   * @param requestsPricedAtCurrentRate calls that predate this model's first price and were
   *     priced at its current price instead
   */
  public record Alternative(
      String provider,
      String modelId,
      BigDecimal projectedCost,
      BigDecimal savings,
      BigDecimal savingsPercent,
      Double averageLatencyMs,
      boolean currentlyUsed,
      long requestsPricedAtCurrentRate) {}

  /**
   * An alternative left out of the comparison.
   *
   * @param provider provider name
   * @param modelId model identifier
   * @param reason why it was excluded
   */
  public record Excluded(String provider, String modelId, String reason) {}
}
