// Generated using Claude Code.

package com.mangomango.ledger.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.budget.BudgetScope;
import com.mangomango.ledger.budget.BudgetService;
import com.mangomango.ledger.budget.BudgetStatus;
import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.LedgerProperties;
import com.mangomango.ledger.usage.RequestStatus;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  @Mock private CatalogService catalogService;
  @Mock private BudgetService budgetService;
  @Mock private UsageEventRepository usageRepository;

  private RecommendationService service;

  @BeforeEach
  void setUp() {
    service = new RecommendationService(catalogService, budgetService, usageRepository,
        new LedgerProperties(Set.of(), false, 30), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static ModelPrice model(String provider, String id, String in, String out, int ctx,
      int tier) {
    return new ModelPrice(provider, id, 1, new BigDecimal(in), new BigDecimal(out), null, ctx,
        tier, Instant.EPOCH, Instant.EPOCH);
  }

  private static RecommendationRequest request(Integer minContext, TaskComplexity complexity,
      Long maxLatency, Optimization optimization) {
    return new RecommendationRequest(1000L, 500L, minContext, complexity, null, maxLatency,
        optimization);
  }

  @Test
  void picksCheapestQualifyingModelWithFallbackFromAnotherProvider() {
    when(catalogService.activeModels()).thenReturn(List.of(
        model("openai", "big", "5", "15", 128_000, 5),
        model("openai", "small", "0.1", "0.4", 128_000, 2),
        model("google", "flash", "0.2", "0.8", 1_000_000, 2)));
    when(usageRepository.findInRange(eq("c1"), any(), any())).thenReturn(List.of());
    when(budgetService.statusesForClient("c1")).thenReturn(List.of());

    RecommendationResponse response =
        service.recommend("c1", request(null, null, null, Optimization.LOWEST_COST));

    assertThat(response.recommended().modelId()).isEqualTo("small");
    assertThat(response.fallback().provider()).isEqualTo("google");
    assertThat(response.budgetPressure()).isFalse();
    assertThat(response.rankedOptions()).hasSize(3);
  }

  @Test
  void excludesModelsBelowContextWindowOrTier() {
    when(catalogService.activeModels()).thenReturn(List.of(
        model("openai", "tiny-ctx", "0.1", "0.4", 4_000, 5),
        model("openai", "low-tier", "0.1", "0.4", 200_000, 1),
        model("anthropic", "ok", "3", "15", 200_000, 4)));
    when(usageRepository.findInRange(eq("c1"), any(), any())).thenReturn(List.of());
    when(budgetService.statusesForClient("c1")).thenReturn(List.of());

    RecommendationResponse response =
        service.recommend("c1", request(100_000, TaskComplexity.HIGH, null, null));

    assertThat(response.recommended().modelId()).isEqualTo("ok");
    assertThat(response.fallback()).isNull();
    assertThat(response.excluded())
        .extracting(RecommendationResponse.Exclusion::modelId)
        .containsExactlyInAnyOrder("tiny-ctx", "low-tier");
  }

  @Test
  void excludesModelsWhoseRecordedLatencyExceedsTolerance() {
    when(catalogService.activeModels()).thenReturn(List.of(
        model("openai", "slow", "0.1", "0.4", 128_000, 3),
        model("google", "fast", "1", "4", 128_000, 3)));
    UsageEvent slowCall = new UsageEvent("c1", "k", "openai", "slow", 10, 10, 0, BigDecimal.ONE,
        1, NOW, 5000, RequestStatus.SUCCESS, null, NOW);
    when(usageRepository.findInRange(eq("c1"), any(), any())).thenReturn(List.of(slowCall));
    when(budgetService.statusesForClient("c1")).thenReturn(List.of());

    RecommendationResponse response =
        service.recommend("c1", request(null, null, 2000L, null));

    assertThat(response.recommended().modelId()).isEqualTo("fast");
    assertThat(response.excluded()).singleElement()
        .satisfies(e -> assertThat(e.reason()).contains("latency"));
  }

  @Test
  void budgetPressureShiftsWeightTowardCost() {
    Map<String, Double> normal = RecommendationService.weights(Optimization.HIGHEST_CAPABILITY,
        false);
    Map<String, Double> pressured = RecommendationService.weights(
        Optimization.HIGHEST_CAPABILITY, true);

    assertThat(pressured.get("cost")).isGreaterThan(normal.get("cost"));
    assertThat(pressured.values().stream().mapToDouble(Double::doubleValue).sum())
        .isCloseTo(1.0, offset(0.001));
  }

  @Test
  void reportsBudgetPressureWhenThresholdIsCrossed() {
    when(catalogService.activeModels()).thenReturn(List.of(
        model("openai", "a", "1", "1", 128_000, 3)));
    when(usageRepository.findInRange(eq("c1"), any(), any())).thenReturn(List.of());
    BudgetStatus crossed = new BudgetStatus(UUID.randomUUID(), BudgetScope.CLIENT, null, NOW, NOW,
        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("100"), List.of(80, 100),
        List.of());
    when(budgetService.statusesForClient("c1")).thenReturn(List.of(crossed));

    assertThat(service.recommend("c1", request(null, null, null, null)).budgetPressure())
        .isTrue();
  }
}
