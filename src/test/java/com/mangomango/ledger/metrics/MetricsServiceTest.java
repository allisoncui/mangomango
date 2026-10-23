// Generated using Claude Code.

package com.mangomango.ledger.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.usage.RequestStatus;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for Operation 2 (Usage Metrics) with the usage repository mocked. */
@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

  private static final Instant DAY_1 = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant DAY_2 = Instant.parse("2026-10-02T00:00:00Z");
  private static final Instant DAY_3 = Instant.parse("2026-10-03T00:00:00Z");
  private static final Instant DAY_4 = Instant.parse("2026-10-04T00:00:00Z");

  @Mock private UsageEventRepository repository;

  private MetricsService service;

  @BeforeEach
  void setUp() {
    service = new MetricsService(repository);
  }

  private static UsageEvent event(String provider, String model, String project, String cost,
      Instant at) {
    return new UsageEvent("c1", "k-" + at + model, provider, model, 1_000, 500, 0,
        new BigDecimal(cost), 1, at, 200, RequestStatus.SUCCESS, project, at);
  }

  private static MetricsQuery query(Instant start, Instant end, Granularity granularity,
      GroupBy groupBy) {
    return new MetricsQuery(start, end, null, null, null, granularity, groupBy);
  }

  private static MetricsQuery filtered(String provider, String model, String project) {
    return new MetricsQuery(DAY_1, DAY_4, provider, model, project, Granularity.DAILY, null);
  }

  /** Three calls over two days: two openai models and one google model, one untagged. */
  private void stubThreeCalls() {
    when(repository.findInRange("c1", DAY_1, DAY_4)).thenReturn(List.of(
        event("openai", "gpt-4o", "bot", "0.30", DAY_1.plusSeconds(3_600)),
        event("openai", "gpt-4o-mini", "bot", "0.05", DAY_1.plusSeconds(7_200)),
        event("google", "gemini-2.0-flash", null, "0.10", DAY_3.plusSeconds(60))));
  }

  @Nested
  class Validation {

    @Test
    void rejectsStartEqualToEnd() {
      assertThatThrownBy(() -> service.summarize("c1",
          query(DAY_1, DAY_1, Granularity.DAILY, null)))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("start must be before end");
      verifyNoInteractions(repository);
    }

    @Test
    void rejectsStartAfterEnd() {
      assertThatThrownBy(() -> service.summarize("c1",
          query(DAY_2, DAY_1, Granularity.DAILY, null)))
          .isInstanceOf(ValidationException.class);
      verifyNoInteractions(repository);
    }

    @Test
    void rejectsRangeWithTooManyBuckets() {
      // 5,001 hourly buckets is just over the limit.
      Instant end = DAY_1.plusSeconds(3_600L * (MetricsService.MAX_BUCKETS + 1));

      assertThatThrownBy(() -> service.summarize("c1",
          query(DAY_1, end, Granularity.HOURLY, null)))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("coarser granularity");
      verifyNoInteractions(repository);
    }

    @Test
    void acceptsRangeAtTheBucketLimit() {
      Instant end = DAY_1.plusSeconds(3_600L * MetricsService.MAX_BUCKETS);
      when(repository.findInRange("c1", DAY_1, end)).thenReturn(List.of());

      UsageSummary summary =
          service.summarize("c1", query(DAY_1, end, Granularity.HOURLY, null));

      assertThat(summary.timeSeries()).hasSize(MetricsService.MAX_BUCKETS);
    }
  }

  @Nested
  class Filtering {

    @BeforeEach
    void setUp() {
      stubThreeCalls();
    }

    @Test
    void noFiltersCountsEveryCallInRange() {
      UsageSummary summary = service.summarize("c1", filtered(null, null, null));

      assertThat(summary.totals().requestCount()).isEqualTo(3);
      assertThat(summary.totals().totalCost()).isEqualByComparingTo("0.45");
      verify(repository).findInRange("c1", DAY_1, DAY_4);
    }

    @Test
    void providerFilterIsCaseInsensitive() {
      UsageSummary summary = service.summarize("c1", filtered(" OpenAI ", null, null));

      assertThat(summary.totals().requestCount()).isEqualTo(2);
    }

    @Test
    void modelFilterMatchesOneModel() {
      UsageSummary summary = service.summarize("c1", filtered(null, "GPT-4o", null));

      assertThat(summary.totals().requestCount()).isEqualTo(1);
      assertThat(summary.totals().totalCost()).isEqualByComparingTo("0.30");
    }

    @Test
    void projectFilterMatchesExactTag() {
      UsageSummary summary = service.summarize("c1", filtered(null, null, "bot"));

      assertThat(summary.totals().requestCount()).isEqualTo(2);
    }

    @Test
    void filtersCombine() {
      UsageSummary summary = service.summarize("c1", filtered("google", null, "bot"));

      assertThat(summary.totals().requestCount()).isZero();
      assertThat(summary.totals().averageLatencyMs()).isNull();
    }
  }

  @Nested
  class TimeSeries {

    @Test
    void dailySeriesIncludesEmptyBuckets() {
      stubThreeCalls();

      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, null));

      assertThat(summary.timeSeries())
          .extracting(UsageSummary.TimePoint::bucketStart)
          .containsExactly(DAY_1, DAY_2, DAY_3);
      assertThat(summary.timeSeries())
          .extracting(UsageSummary.TimePoint::requestCount)
          .containsExactly(2L, 0L, 1L);
      assertThat(summary.timeSeries().get(0).totalCost()).isEqualByComparingTo("0.35");
      assertThat(summary.timeSeries().get(0).totalTokens()).isEqualTo(3_000);
      assertThat(summary.timeSeries().get(1).totalCost()).isEqualByComparingTo("0");
    }

    @Test
    void firstBucketIsAlignedToTheStartOfItsPeriod() {
      Instant midday = DAY_1.plusSeconds(12 * 3_600);
      when(repository.findInRange("c1", midday, DAY_3)).thenReturn(List.of());

      UsageSummary summary =
          service.summarize("c1", query(midday, DAY_3, Granularity.DAILY, null));

      assertThat(summary.timeSeries())
          .extracting(UsageSummary.TimePoint::bucketStart)
          .containsExactly(DAY_1, DAY_2);
    }

    @Test
    void monthlySeriesHasOneBucketPerCalendarMonth() {
      Instant midOctober = Instant.parse("2026-10-15T00:00:00Z");
      Instant midDecember = Instant.parse("2026-12-15T00:00:00Z");
      when(repository.findInRange("c1", midOctober, midDecember)).thenReturn(List.of());

      UsageSummary summary =
          service.summarize("c1", query(midOctober, midDecember, Granularity.MONTHLY, null));

      assertThat(summary.timeSeries())
          .extracting(UsageSummary.TimePoint::bucketStart)
          .containsExactly(DAY_1, Instant.parse("2026-11-01T00:00:00Z"),
              Instant.parse("2026-12-01T00:00:00Z"));
    }
  }

  @Nested
  class GroupingAndBreakdown {

    @BeforeEach
    void setUp() {
      stubThreeCalls();
    }

    @Test
    void noGroupByReturnsNoGroups() {
      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, null));

      assertThat(summary.groups()).isEmpty();
    }

    @Test
    void groupsByProviderSortedByKey() {
      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, GroupBy.PROVIDER));

      assertThat(summary.groups()).extracting(UsageSummary.Group::key)
          .containsExactly("google", "openai");
      assertThat(summary.groups().get(1).stats().requestCount()).isEqualTo(2);
    }

    @Test
    void groupsByModelUseProviderSlashModelKeys() {
      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, GroupBy.MODEL));

      assertThat(summary.groups()).extracting(UsageSummary.Group::key)
          .containsExactly("google/gemini-2.0-flash", "openai/gpt-4o", "openai/gpt-4o-mini");
    }

    @Test
    void groupsByProjectPutUntaggedCallsInTheirOwnGroup() {
      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, GroupBy.PROJECT));

      assertThat(summary.groups()).extracting(UsageSummary.Group::key)
          .containsExactly(GroupBy.UNTAGGED, "bot");
    }

    @Test
    void costBreakdownListsEachModelMostExpensiveFirst() {
      UsageSummary summary =
          service.summarize("c1", query(DAY_1, DAY_4, Granularity.DAILY, null));

      assertThat(summary.costBreakdown())
          .extracting(UsageSummary.ModelCost::modelId)
          .containsExactly("gpt-4o", "gemini-2.0-flash", "gpt-4o-mini");
      assertThat(summary.costBreakdown().get(0).totalCost()).isEqualByComparingTo("0.30");
    }
  }
}
