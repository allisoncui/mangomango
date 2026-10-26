// Generated using Claude Code.

package com.mangomango.ledger.metrics;

import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.common.Money;
import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operation 2: Get Usage Metrics &amp; Aggregations. */
@Service
public class MetricsService {

  /** Upper bound on time-series points so one request cannot produce an unbounded response. */
  static final int MAX_BUCKETS = 5000;

  private final UsageEventRepository repository;

  /** Creates the service. */
  public MetricsService(UsageEventRepository repository) {
    this.repository = repository;
  }

  /**
   * Aggregates a client's usage in {@code [start, end)}, optionally filtered and grouped.
   *
   * @param query the filters, granularity and grouping
   * @return the aggregated summary
   */
  @Transactional(readOnly = true)
  public UsageSummary summarize(String clientId, MetricsQuery query) {
    if (!query.start().isBefore(query.end())) {
      throw new ValidationException("start must be before end");
    }
    List<Instant> buckets = buckets(query);
    String provider = ModelKey.normalize(query.provider());
    String model = ModelKey.normalize(query.model());
    String project =
            query.project() == null || query.project().isBlank()
                    ? null
                    : query.project().trim();
    List<UsageEvent> events =
        repository.findInRange(clientId, query.start(), query.end()).stream()
            .filter(e -> provider == null || provider.equals(e.getProvider()))
            .filter(e -> model == null || model.equals(e.getModelId()))
            .filter(e -> project == null || project.equals(e.getProjectTag()))
            .toList();

    return new UsageSummary(
        query.start(),
        query.end(),
        query.granularity(),
        query.groupBy(),
        UsageStats.of(events),
        costBreakdown(events),
        timeSeries(events, buckets, query.granularity()),
        query.groupBy() == null ? List.of() : groups(events, query.groupBy()));
  }

  private static List<Instant> buckets(MetricsQuery query) {
    List<Instant> buckets = new ArrayList<>();
    for (Instant b = query.granularity().bucketStart(query.start());
        b.isBefore(query.end());
        b = query.granularity().next(b)) {
      buckets.add(b);
      if (buckets.size() > MAX_BUCKETS) {
        throw new ValidationException(
            "Range produces more than " + MAX_BUCKETS + " buckets; use a coarser granularity");
      }
    }
    return buckets;
  }

  private static List<UsageSummary.ModelCost> costBreakdown(List<UsageEvent> events) {
    Map<String, List<UsageEvent>> byModel =
        events.stream().collect(Collectors.groupingBy(GroupBy.MODEL::keyOf));
    return byModel.values().stream()
        .map(
            list -> {
              UsageStats s = UsageStats.of(list);
              UsageEvent first = list.get(0);
              return new UsageSummary.ModelCost(
                  first.getProvider(),
                  first.getModelId(),
                  s.requestCount(),
                  s.totalTokens(),
                  s.totalCost());
            })
        .sorted(
                Comparator.comparing(UsageSummary.ModelCost::totalCost)
                        .reversed()
                        .thenComparing(UsageSummary.ModelCost::provider)
                        .thenComparing(UsageSummary.ModelCost::modelId))
        .toList();
  }

  private static List<UsageSummary.TimePoint> timeSeries(
      List<UsageEvent> events, List<Instant> buckets, Granularity granularity) {
    Map<Instant, List<UsageEvent>> byBucket =
        events.stream()
            .collect(Collectors.groupingBy(e -> granularity.bucketStart(e.getRequestTimestamp())));
    return buckets.stream()
        .map(
            b -> {
              List<UsageEvent> inBucket = byBucket.getOrDefault(b, List.of());
              long tokens = inBucket.stream().mapToLong(UsageEvent::totalTokens).sum();
              BigDecimal cost =
                  inBucket.stream().map(UsageEvent::getCost).reduce(BigDecimal.ZERO,
                      BigDecimal::add);
              return new UsageSummary.TimePoint(b, inBucket.size(), tokens, Money.round(cost));
            })
        .toList();
  }

  private static List<UsageSummary.Group> groups(List<UsageEvent> events, GroupBy groupBy) {
    Map<String, List<UsageEvent>> grouped =
        events.stream()
            .collect(Collectors.groupingBy(groupBy::keyOf, TreeMap::new, Collectors.toList()));
    return grouped.entrySet().stream()
        .map(e -> new UsageSummary.Group(e.getKey(), UsageStats.of(e.getValue())))
        .toList();
  }
}
