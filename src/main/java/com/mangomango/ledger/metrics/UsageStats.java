package com.mangomango.ledger.metrics;

import com.mangomango.ledger.common.Money;
import com.mangomango.ledger.usage.UsageEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;

/**
 * Aggregate statistics over a set of usage events.
 *
 * @param requestCount number of calls
 * @param inputTokens total input tokens
 * @param outputTokens total output tokens
 * @param cachedInputTokens total cached input tokens
 * @param totalTokens input plus output tokens
 * @param totalCost total dollar cost
 * @param averageCostPerRequest mean cost per call
 * @param averageTokensPerRequest mean input plus output tokens per call
 * @param errorRate fraction of calls with ERROR status, 0 to 1
 * @param averageLatencyMs mean latency, or null when there are no calls
 * @param p95LatencyMs 95th-percentile latency (nearest rank), or null when there are no calls
 */
public record UsageStats(
    long requestCount,
    long inputTokens,
    long outputTokens,
    long cachedInputTokens,
    long totalTokens,
    BigDecimal totalCost,
    BigDecimal averageCostPerRequest,
    double averageTokensPerRequest,
    double errorRate,
    Double averageLatencyMs,
    Long p95LatencyMs) {

  /** Computes statistics over {@code events}. */
  public static UsageStats of(Collection<UsageEvent> events) {
    long count = events.size();
    long input = 0;
    long output = 0;
    long cached = 0;
    long errors = 0;
    BigDecimal cost = BigDecimal.ZERO;
    for (UsageEvent e : events) {
      input += e.getInputTokens();
      output += e.getOutputTokens();
      cached += e.getCachedInputTokens();
      cost = cost.add(e.getCost());
      if (e.isError()) {
        errors++;
      }
    }
    long[] latencies = events.stream().mapToLong(UsageEvent::getLatencyMs).sorted().toArray();
    return new UsageStats(
        count,
        input,
        output,
        cached,
        input + output,
        Money.round(cost),
        count == 0
            ? Money.round(BigDecimal.ZERO)
            : cost.divide(BigDecimal.valueOf(count), Money.SCALE, RoundingMode.HALF_UP),
        count == 0 ? 0 : (double) (input + output) / count,
        count == 0 ? 0 : (double) errors / count,
        count == 0 ? null : Arrays.stream(latencies).average().orElse(0),
        percentile(latencies, 95));
  }

  /** Nearest-rank percentile of an ascending array; null when empty. */
  static Long percentile(long[] sortedAscending, int percentile) {
    if (sortedAscending.length == 0) {
      return null;
    }
    int rank = (int) Math.ceil(percentile / 100.0 * sortedAscending.length);
    return sortedAscending[Math.max(rank, 1) - 1];
  }
}
