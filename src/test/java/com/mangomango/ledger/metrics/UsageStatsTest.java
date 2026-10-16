package com.mangomango.ledger.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangomango.ledger.usage.RequestStatus;
import com.mangomango.ledger.usage.UsageEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageStatsTest {

  private static UsageEvent event(long latency, RequestStatus status) {
    return new UsageEvent("c", "k" + latency, "p", "m", 100, 50, 0, new BigDecimal("0.01"), 1,
        Instant.EPOCH, latency, status, null, Instant.EPOCH);
  }

  @Test
  void computesTotalsErrorRateAndLatency() {
    List<UsageEvent> events = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      events.add(event(i * 10L, i <= 2 ? RequestStatus.ERROR : RequestStatus.SUCCESS));
    }

    UsageStats stats = UsageStats.of(events);

    assertThat(stats.requestCount()).isEqualTo(20);
    assertThat(stats.totalTokens()).isEqualTo(3000);
    assertThat(stats.totalCost()).isEqualByComparingTo("0.20");
    assertThat(stats.errorRate()).isEqualTo(0.1);
    assertThat(stats.averageLatencyMs()).isEqualTo(105.0);
    assertThat(stats.p95LatencyMs()).isEqualTo(190L);
  }

  @Test
  void emptyInputHasNoLatency() {
    UsageStats stats = UsageStats.of(List.of());

    assertThat(stats.requestCount()).isZero();
    assertThat(stats.averageLatencyMs()).isNull();
    assertThat(stats.p95LatencyMs()).isNull();
    assertThat(stats.errorRate()).isZero();
  }

  @Test
  void percentileOfSingleValueIsThatValue() {
    assertThat(UsageStats.percentile(new long[] {42}, 95)).isEqualTo(42L);
  }
}
