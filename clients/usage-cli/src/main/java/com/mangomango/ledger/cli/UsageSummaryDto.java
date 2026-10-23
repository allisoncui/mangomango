// Generated using Claude Code.

package com.mangomango.ledger.cli;

import java.math.BigDecimal;
import java.util.List;

/**
 * The part of the service's usage-metrics response this client prints. Everything else in the
 * response is ignored.
 *
 * @param totals statistics across every matching call
 * @param costBreakdown cost per provider and model, most expensive first
 */
public record UsageSummaryDto(Stats totals, List<ModelCost> costBreakdown) {

  /** Replaces an absent breakdown with an empty one so callers never have to null-check. */
  public UsageSummaryDto {
    costBreakdown = costBreakdown == null ? List.of() : costBreakdown;
  }

  /**
   * Aggregate statistics over a set of calls.
   *
   * @param requestCount number of calls
   * @param inputTokens total input tokens
   * @param outputTokens total output tokens
   * @param cachedInputTokens total cached input tokens
   * @param totalTokens input plus output tokens
   * @param totalCost total dollar cost
   * @param errorRate fraction of calls that failed, 0 to 1
   * @param averageLatencyMs mean latency, or null when there are no calls
   * @param p95LatencyMs 95th-percentile latency, or null when there are no calls
   */
  public record Stats(
      long requestCount,
      long inputTokens,
      long outputTokens,
      long cachedInputTokens,
      long totalTokens,
      BigDecimal totalCost,
      double errorRate,
      Double averageLatencyMs,
      Long p95LatencyMs) {}

  /**
   * Cost attributed to one model.
   *
   * @param provider provider name
   * @param modelId model identifier
   * @param requestCount number of calls
   * @param totalTokens input plus output tokens
   * @param totalCost dollar cost
   */
  public record ModelCost(
      String provider,
      String modelId,
      long requestCount,
      long totalTokens,
      BigDecimal totalCost) {}
}
