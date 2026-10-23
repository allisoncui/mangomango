// Generated using Claude Code.

package com.mangomango.ledger.recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Result of a routing recommendation.
 *
 * @param recommended the highest-scoring model, or null when no model qualifies
 * @param fallback the highest-scoring model from a different provider, or null
 * @param budgetPressure true when the client is past a budget alert threshold, which shifts
 *     weight toward cost
 * @param weights the weights applied to each score component
 * @param rankedOptions every qualifying model, best first
 * @param excluded models that did not qualify, with reasons
 */
public record RecommendationResponse(
    Option recommended,
    Option fallback,
    boolean budgetPressure,
    Map<String, Double> weights,
    List<Option> rankedOptions,
    List<Exclusion> excluded) {

  /**
   * One scored model.
   *
   * @param provider provider name
   * @param modelId model identifier
   * @param capabilityTier catalog capability tier
   * @param contextWindow catalog context window
   * @param estimatedCost dollar cost of the described call at current prices
   * @param expectedLatencyMs recorded average latency, or null without history
   * @param errorRate recorded error rate, or null without history
   * @param sampleSize number of recorded calls behind the latency and error figures
   * @param score weighted total, 0 to 1
   * @param scoreBreakdown each component score, 0 to 1
   */
  public record Option(
      String provider,
      String modelId,
      int capabilityTier,
      int contextWindow,
      BigDecimal estimatedCost,
      Double expectedLatencyMs,
      Double errorRate,
      long sampleSize,
      double score,
      Map<String, Double> scoreBreakdown) {}

  /**
   * A model left out of the ranking.
   *
   * @param provider provider name
   * @param modelId model identifier
   * @param reason why it was excluded
   */
  public record Exclusion(String provider, String modelId, String reason) {}
}
