// Generated using Claude Code.

package com.mangomango.ledger.metrics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated usage for a time range.
 *
 * @param start range start (inclusive)
 * @param end range end (exclusive)
 * @param granularity bucket size of {@code timeSeries}
 * @param groupBy grouping dimension of {@code groups}, or null
 * @param totals statistics across every matching call
 * @param costBreakdown cost per provider and model, most expensive first
 * @param timeSeries one point per bucket in the range, including empty buckets
 * @param groups statistics per group when {@code groupBy} is set, otherwise empty
 */
public record UsageSummary(
    Instant start,
    Instant end,
    Granularity granularity,
    GroupBy groupBy,
    UsageStats totals,
    List<ModelCost> costBreakdown,
    List<TimePoint> timeSeries,
    List<Group> groups) {

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
      String provider, String modelId, long requestCount, long totalTokens, BigDecimal totalCost) {}

  /**
   * One time-series bucket.
   *
   * @param bucketStart bucket start
   * @param requestCount number of calls
   * @param totalTokens input plus output tokens
   * @param totalCost dollar cost
   */
  public record TimePoint(
      Instant bucketStart, long requestCount, long totalTokens, BigDecimal totalCost) {}

  /**
   * Statistics for one group.
   *
   * @param key group key, e.g. a provider name or project tag
   * @param stats statistics for calls in the group
   */
  public record Group(String key, UsageStats stats) {}
}
