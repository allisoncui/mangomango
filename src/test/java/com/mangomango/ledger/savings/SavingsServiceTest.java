// Generated using Claude Code.

package com.mangomango.ledger.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.usage.RequestStatus;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for Operation 6 (Cost Savings) with the usage repository and catalog mocked. */
@ExtendWith(MockitoExtension.class)
class SavingsServiceTest {

  private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant END = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant CALL_TIME = Instant.parse("2026-10-10T12:00:00Z");
  private static final Instant LONG_AGO = Instant.parse("2025-01-01T00:00:00Z");

  /** The model the client actually used: $4 per million tokens, tier 3. */
  private static final ModelPrice USED = price("openai", "used", "4.00", 1_000_000, 3);
  /** Same tier and context window, a quarter of the price. */
  private static final ModelPrice CHEAPER = price("google", "cheaper", "1.00", 1_000_000, 3);
  /** Cheapest, but below the tier the client used. */
  private static final ModelPrice WEAK = price("google", "weak", "0.10", 1_000_000, 1);
  /** Cheap and capable, but its context window is too small for the largest call. */
  private static final ModelPrice SMALL = price("anthropic", "small", "0.10", 1_000, 5);

  @Mock private UsageEventRepository usageRepository;
  @Mock private CatalogService catalogService;

  private SavingsService service;

  @BeforeEach
  void setUp() {
    service = new SavingsService(usageRepository, catalogService);
  }

  private static ModelPrice price(String provider, String model, String perMillion, int context,
      int tier) {
    BigDecimal p = new BigDecimal(perMillion);
    return new ModelPrice(provider, model, 1, p, p, null, context, tier, LONG_AGO, LONG_AGO);
  }

  /** One call on {@link #USED}: 250k input + 250k output tokens at $4/M, so $2.00. */
  private static UsageEvent usedCall(String project) {
    return new UsageEvent("c1", "k-" + project, "openai", "used", 250_000, 250_000, 0,
        new BigDecimal("2.000000"), 1, CALL_TIME, 300, RequestStatus.SUCCESS, project, CALL_TIME);
  }

  private static SavingsRequest request(String project, ModelPrice... alternatives) {
    List<SavingsRequest.ModelRef> refs = Arrays.stream(alternatives)
        .map(p -> new SavingsRequest.ModelRef(p.getProvider(), p.getModelId()))
        .toList();
    return new SavingsRequest(START, END, project, refs);
  }

  /** Makes {@code price} the current version and the version in effect at any time. */
  private void stubCatalog(ModelPrice price) {
    when(catalogService.currentOrLatest(price.key())).thenReturn(Optional.of(price));
    when(catalogService.priceAt(eq(price.key()), any())).thenReturn(Optional.of(price));
  }

  @Nested
  class Repricing {

    @BeforeEach
    void setUp() {
      when(usageRepository.findInRange("c1", START, END)).thenReturn(List.of(usedCall(null)));
      when(catalogService.currentOrLatest(USED.key())).thenReturn(Optional.of(USED));
    }

    @Test
    void reportsActualCostAndSavingsCheapestFirst() {
      stubCatalog(USED);
      stubCatalog(CHEAPER);

      SavingsResponse response = service.analyze("c1", request(null, USED, CHEAPER));

      assertThat(response.requestCount()).isEqualTo(1);
      assertThat(response.actualCost()).isEqualByComparingTo("2.00");
      assertThat(response.largestRequestTokens()).isEqualTo(500_000);
      assertThat(response.highestCapabilityTierUsed()).isEqualTo(3);
      assertThat(response.alternatives())
          .extracting(SavingsResponse.Alternative::modelId)
          .containsExactly("cheaper", "used");
      SavingsResponse.Alternative cheaper = response.alternatives().get(0);
      assertThat(cheaper.projectedCost()).isEqualByComparingTo("0.50");
      assertThat(cheaper.savings()).isEqualByComparingTo("1.50");
      assertThat(cheaper.savingsPercent()).isEqualByComparingTo("75");
      assertThat(cheaper.currentlyUsed()).isFalse();
    }

    @Test
    void currentlyUsedModelShowsZeroSavingsAndRecordedLatency() {
      stubCatalog(USED);

      SavingsResponse response = service.analyze("c1", request(null, USED));

      SavingsResponse.Alternative used = response.alternatives().get(0);
      assertThat(used.savings()).isEqualByComparingTo("0");
      assertThat(used.currentlyUsed()).isTrue();
      assertThat(used.averageLatencyMs()).isEqualTo(300.0);
    }

    @Test
    void usesTheAlternativesPriceInEffectAtEachCall() {
      // At call time the alternative cost $2/M; it has since dropped to $1/M.
      ModelPrice cheaperThen = price("google", "cheaper", "2.00", 1_000_000, 3);
      when(catalogService.currentOrLatest(CHEAPER.key())).thenReturn(Optional.of(CHEAPER));
      when(catalogService.priceAt(CHEAPER.key(), CALL_TIME)).thenReturn(Optional.of(cheaperThen));

      SavingsResponse response = service.analyze("c1", request(null, CHEAPER));

      assertThat(response.alternatives().get(0).projectedCost()).isEqualByComparingTo("1.00");
      assertThat(response.alternatives().get(0).requestsPricedAtCurrentRate()).isZero();
    }

    @Test
    void callsBeforeTheAlternativeExistedArePricedAtItsCurrentRate() {
      when(catalogService.currentOrLatest(CHEAPER.key())).thenReturn(Optional.of(CHEAPER));
      when(catalogService.priceAt(CHEAPER.key(), CALL_TIME)).thenReturn(Optional.empty());

      SavingsResponse response = service.analyze("c1", request(null, CHEAPER));

      assertThat(response.alternatives().get(0).projectedCost()).isEqualByComparingTo("0.50");
      assertThat(response.alternatives().get(0).requestsPricedAtCurrentRate()).isEqualTo(1);
    }

    @Test
    void repricingChargesCachedInputAtTheAlternativesCachedRate() {
      // 200k input (100k of it cached) + 50k output, so any mixed-up argument changes the cost.
      UsageEvent cachedCall = new UsageEvent("c1", "k-cached", "openai", "used", 200_000, 50_000,
              100_000, new BigDecimal("1.000000"), 1, CALL_TIME, 300, RequestStatus.SUCCESS, null,
              CALL_TIME);
      when(usageRepository.findInRange("c1", START, END)).thenReturn(List.of(cachedCall));
      // $3/M input, $15/M output, $0.30/M cached input.
      ModelPrice tiered = new ModelPrice("google", "tiered", 1, new BigDecimal("3.00"),
              new BigDecimal("15.00"), new BigDecimal("0.30"), 1_000_000, 3, LONG_AGO, LONG_AGO);
      stubCatalog(tiered);

      SavingsResponse response = service.analyze("c1", request(null, tiered));

      // 100k uncached x $3/M = 0.30, 100k cached x $0.30/M = 0.03, 50k output x $15/M = 0.75.
      assertThat(response.alternatives().get(0).projectedCost()).isEqualByComparingTo("1.08");
    }
  }

  @Nested
  class Exclusions {

    @BeforeEach
    void setUp() {
      when(usageRepository.findInRange("c1", START, END)).thenReturn(List.of(usedCall(null)));
      when(catalogService.currentOrLatest(USED.key())).thenReturn(Optional.of(USED));
    }

    @Test
    void excludesModelsBelowTheTierUsed() {
      when(catalogService.currentOrLatest(WEAK.key())).thenReturn(Optional.of(WEAK));

      SavingsResponse response = service.analyze("c1", request(null, WEAK));

      assertThat(response.alternatives()).isEmpty();
      assertThat(response.excluded()).singleElement()
          .satisfies(e -> assertThat(e.reason()).contains("capability tier 1 < tier used 3"));
    }

    @Test
    void excludesModelsWhoseContextWindowIsTooSmall() {
      when(catalogService.currentOrLatest(SMALL.key())).thenReturn(Optional.of(SMALL));

      SavingsResponse response = service.analyze("c1", request(null, SMALL));

      assertThat(response.excluded()).singleElement()
          .satisfies(e -> assertThat(e.reason()).contains("context window 1000 < largest request"));
    }

    @Test
    void keepsModelsWhoseContextWindowEqualsTheLargestRequest() {
      // usedCall is 250k input + 250k output, so the largest request is exactly 500k tokens.
      ModelPrice exact = price("google", "exact", "1.00", 500_000, 3);
      stubCatalog(exact);

      SavingsResponse response = service.analyze("c1", request(null, exact));

      assertThat(response.largestRequestTokens()).isEqualTo(500_000);
      assertThat(response.excluded()).isEmpty();
      assertThat(response.alternatives())
              .extracting(SavingsResponse.Alternative::modelId)
              .containsExactly("exact");
    }

    @Test
    void excludesModelsNotInTheCatalog() {
      ModelKey unknown = ModelKey.of("openai", "no-such-model");
      when(catalogService.currentOrLatest(unknown)).thenReturn(Optional.empty());

      SavingsResponse response = service.analyze("c1", new SavingsRequest(START, END, null,
          List.of(new SavingsRequest.ModelRef("OpenAI", "No-Such-Model"))));

      assertThat(response.excluded()).singleElement()
          .satisfies(e -> {
            assertThat(e.modelId()).isEqualTo("no-such-model");
            assertThat(e.reason()).isEqualTo("not in the catalog");
          });
    }
  }

  @Nested
  class Scope {

    @Test
    void rejectsStartNotBeforeEnd() {
      assertThatThrownBy(() -> service.analyze("c1", new SavingsRequest(END, START, null, null)))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("start must be before end");
      verifyNoInteractions(usageRepository, catalogService);
    }

    @Test
    void projectTagIsTrimmedAndRestrictsTheCallsAnalyzed() {
      when(usageRepository.findInRange("c1", START, END))
          .thenReturn(List.of(usedCall("bot"), usedCall("other")));
      stubCatalog(USED);

      SavingsResponse response = service.analyze("c1", request(" bot ", USED));

      assertThat(response.projectTag()).isEqualTo("bot");
      assertThat(response.requestCount()).isEqualTo(1);
      assertThat(response.actualCost()).isEqualByComparingTo("2.00");
    }

    @Test
    void noAlternativesComparesEveryActiveCatalogModel() {
      when(usageRepository.findInRange("c1", START, END)).thenReturn(List.of(usedCall(null)));
      when(catalogService.activeModels()).thenReturn(List.of(USED, CHEAPER));
      stubCatalog(USED);
      stubCatalog(CHEAPER);

      SavingsResponse response = service.analyze("c1", new SavingsRequest(START, END, null, null));

      assertThat(response.alternatives())
          .extracting(SavingsResponse.Alternative::modelId)
          .containsExactlyInAnyOrder("used", "cheaper");
    }

    @Test
    void noUsageInRangeGivesZeroCostsAndPercent() {
      when(usageRepository.findInRange("c1", START, END)).thenReturn(List.of());
      when(catalogService.currentOrLatest(CHEAPER.key())).thenReturn(Optional.of(CHEAPER));

      SavingsResponse response = service.analyze("c1", request(null, CHEAPER));

      assertThat(response.actualCost()).isEqualByComparingTo("0");
      assertThat(response.highestCapabilityTierUsed()).isZero();
      assertThat(response.alternatives().get(0).projectedCost()).isEqualByComparingTo("0");
      assertThat(response.alternatives().get(0).savingsPercent()).isEqualByComparingTo("0");
    }
  }
}
