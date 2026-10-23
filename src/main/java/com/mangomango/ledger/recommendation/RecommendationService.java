// Generated using Claude Code.

package com.mangomango.ledger.recommendation;

import com.mangomango.ledger.budget.BudgetService;
import com.mangomango.ledger.budget.BudgetStatus;
import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.CostCalculator;
import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.LedgerProperties;
import com.mangomango.ledger.metrics.GroupBy;
import com.mangomango.ledger.metrics.UsageStats;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operation 3: Evaluate Endpoint Routing &amp; Recommendation. */
@Service
public class RecommendationService {

  static final long DEFAULT_OUTPUT_TOKENS = 500;
  static final int MAX_TIER = 5;
  /** Score given to latency or reliability when the client has no history for a model. */
  static final double NO_HISTORY_SCORE = 0.5;
  /** Extra cost weight applied when the client is past a budget alert threshold. */
  static final double BUDGET_PRESSURE_BOOST = 0.3;

  private static final String COST = "cost";
  private static final String LATENCY = "latency";
  private static final String CAPABILITY = "capability";
  private static final String RELIABILITY = "reliability";

  private final CatalogService catalogService;
  private final BudgetService budgetService;
  private final UsageEventRepository usageRepository;
  private final LedgerProperties properties;
  private final Clock clock;

  /** Creates the service. */
  public RecommendationService(
      CatalogService catalogService,
      BudgetService budgetService,
      UsageEventRepository usageRepository,
      LedgerProperties properties,
      Clock clock) {
    this.catalogService = catalogService;
    this.budgetService = budgetService;
    this.usageRepository = usageRepository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Scores every catalog model against the request and the client's recorded history. */
  @Transactional(readOnly = true)
  public RecommendationResponse recommend(String clientId, RecommendationRequest request) {
    long outputTokens =
        request.expectedOutputTokens() == null
            ? DEFAULT_OUTPUT_TOKENS
            : request.expectedOutputTokens();
    long requiredContext =
        Math.max(
            request.estimatedPromptTokens() + outputTokens,
            request.minContextWindow() == null ? 0 : request.minContextWindow());
    int minTier = minimumTier(request);

    Instant now = clock.instant();
    Map<String, UsageStats> history =
        usageRepository
            .findInRange(
                clientId, now.minus(Duration.ofDays(properties.recommendationLookbackDays())), now)
            .stream()
            .collect(
                Collectors.groupingBy(
                    GroupBy.MODEL::keyOf,
                    Collectors.collectingAndThen(Collectors.toList(), UsageStats::of)));

    List<RecommendationResponse.Exclusion> excluded = new ArrayList<>();
    List<Candidate> candidates = new ArrayList<>();
    for (ModelPrice price : catalogService.activeModels()) {
      ModelKey key = price.key();
      UsageStats stats = history.get(key.toString());
      String reason = exclusionReason(price, stats, requiredContext, minTier, request);
      if (reason != null) {
        excluded.add(
            new RecommendationResponse.Exclusion(key.provider(), key.modelId(), reason));
        continue;
      }
      BigDecimal cost =
          CostCalculator.cost(price, request.estimatedPromptTokens(), 0, outputTokens);
      candidates.add(new Candidate(price, cost, stats));
    }

    boolean budgetPressure =
        budgetService.statusesForClient(clientId).stream()
            .anyMatch(BudgetStatus::anyThresholdCrossed);
    Optimization optimization =
        request.optimizeFor() == null ? Optimization.LOWEST_COST : request.optimizeFor();
    Map<String, Double> weights = weights(optimization, budgetPressure);
    List<RecommendationResponse.Option> ranked = score(candidates, weights);

    RecommendationResponse.Option best = ranked.isEmpty() ? null : ranked.get(0);
    RecommendationResponse.Option fallback =
        best == null
            ? null
            : ranked.stream()
                .filter(o -> !o.provider().equals(best.provider()))
                .findFirst()
                .orElse(null);
    return new RecommendationResponse(
        best, fallback, budgetPressure, weights, ranked, excluded);
  }

  private static int minimumTier(RecommendationRequest request) {
    int fromComplexity =
        request.taskComplexity() == null ? 1 : request.taskComplexity().minimumTier();
    int explicit = request.minCapabilityTier() == null ? 1 : request.minCapabilityTier();
    return Math.max(fromComplexity, explicit);
  }

  private static String exclusionReason(
      ModelPrice price,
      UsageStats stats,
      long requiredContext,
      int minTier,
      RecommendationRequest request) {
    if (price.getContextWindow() < requiredContext) {
      return "context window " + price.getContextWindow() + " < required " + requiredContext;
    }
    if (price.getCapabilityTier() < minTier) {
      return "capability tier " + price.getCapabilityTier() + " < required " + minTier;
    }
    if (request.maxLatencyMs() != null
        && stats != null
        && stats.averageLatencyMs() != null
        && stats.averageLatencyMs() > request.maxLatencyMs()) {
      return String.format(
          "recorded average latency %.0f ms > tolerance %d ms",
          stats.averageLatencyMs(), request.maxLatencyMs());
    }
    return null;
  }

  /** Returns normalized component weights for a preference, shifted toward cost if needed. */
  static Map<String, Double> weights(Optimization optimization, boolean budgetPressure) {
    double[] w =
        switch (optimization) {
          case LOWEST_COST -> new double[] {0.6, 0.15, 0.1, 0.15};
          case LOWEST_LATENCY -> new double[] {0.15, 0.6, 0.1, 0.15};
          case HIGHEST_CAPABILITY -> new double[] {0.15, 0.1, 0.6, 0.15};
        };
    if (budgetPressure) {
      w[0] += BUDGET_PRESSURE_BOOST;
    }
    double sum = w[0] + w[1] + w[2] + w[3];
    Map<String, Double> weights = new LinkedHashMap<>();
    weights.put(COST, round(w[0] / sum));
    weights.put(LATENCY, round(w[1] / sum));
    weights.put(CAPABILITY, round(w[2] / sum));
    weights.put(RELIABILITY, round(w[3] / sum));
    return weights;
  }

  static List<RecommendationResponse.Option> score(
      List<Candidate> candidates, Map<String, Double> weights) {
    BigDecimal minCost =
        candidates.stream().map(Candidate::cost).min(Comparator.naturalOrder()).orElse(null);
    double minLatency =
        candidates.stream()
            .map(Candidate::stats)
            .filter(s -> s != null && s.averageLatencyMs() != null)
            .mapToDouble(UsageStats::averageLatencyMs)
            .min()
            .orElse(0);

    List<RecommendationResponse.Option> options = new ArrayList<>();
    for (Candidate c : candidates) {
      Map<String, Double> parts = new LinkedHashMap<>();
      parts.put(COST, round(ratio(minCost, c.cost())));
      Double latency = c.stats() == null ? null : c.stats().averageLatencyMs();
      parts.put(
          LATENCY,
          round(
              latency == null
                  ? NO_HISTORY_SCORE
                  : latency <= 0 ? 1.0 : Math.min(1.0, Math.max(minLatency, 1) / latency)));
      parts.put(CAPABILITY, round((double) c.price().getCapabilityTier() / MAX_TIER));
      parts.put(
          RELIABILITY,
          round(c.stats() == null ? NO_HISTORY_SCORE : 1.0 - c.stats().errorRate()));
      double total =
          parts.entrySet().stream().mapToDouble(e -> e.getValue() * weights.get(e.getKey())).sum();
      options.add(
          new RecommendationResponse.Option(
              c.price().getProvider(),
              c.price().getModelId(),
              c.price().getCapabilityTier(),
              c.price().getContextWindow(),
              c.cost(),
              latency,
              c.stats() == null ? null : c.stats().errorRate(),
              c.stats() == null ? 0 : c.stats().requestCount(),
              round(total),
              parts));
    }
    options.sort(
        Comparator.comparingDouble(RecommendationResponse.Option::score)
            .reversed()
            .thenComparing(RecommendationResponse.Option::estimatedCost));
    return options;
  }

  private static double ratio(BigDecimal min, BigDecimal value) {
    if (value.signum() == 0) {
      return 1.0;
    }
    return min.doubleValue() / value.doubleValue();
  }

  private static double round(double value) {
    return Math.round(value * 10_000) / 10_000.0;
  }

  /** A qualifying model with its estimated cost and recorded history (null if none). */
  record Candidate(ModelPrice price, BigDecimal cost, UsageStats stats) {}
}
