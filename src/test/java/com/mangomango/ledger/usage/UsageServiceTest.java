// Generated using Claude Code.

package com.mangomango.ledger.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.budget.BudgetScope;
import com.mangomango.ledger.budget.BudgetService;
import com.mangomango.ledger.budget.BudgetStatus;
import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.ConflictException;
import com.mangomango.ledger.common.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for Operation 1 (Log Token Usage) with the repository, catalog and budgets mocked. */
@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
  private static final Instant CALL_TIME = Instant.parse("2026-10-15T11:00:00Z");
  private static final ModelKey GPT_4O = ModelKey.of("openai", "gpt-4o");

  /** Version 3 of gpt-4o: $2.50 input, $10.00 output, $1.25 cached input per million tokens. */
  private static final ModelPrice PRICE =
      new ModelPrice("openai", "gpt-4o", 3, new BigDecimal("2.50"), new BigDecimal("10.00"),
          new BigDecimal("1.25"), 128_000, 4, Instant.parse("2026-01-01T00:00:00Z"), NOW);

  @Mock private UsageEventRepository repository;
  @Mock private CatalogService catalogService;
  @Mock private BudgetService budgetService;

  private UsageService service;

  @BeforeEach
  void setUp() {
    service = new UsageService(repository, catalogService, budgetService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 10,000 input tokens (4,000 cached) and 2,000 output tokens on gpt-4o, costing $0.04. */
  private static LogUsageRequest standardRequest() {
    return request("openai", "gpt-4o", 10_000L, 2_000L, 4_000L, CALL_TIME, "support-bot");
  }

  private static LogUsageRequest request(String provider, String model, long input, long output,
      Long cached, Instant timestamp, String project) {
    return new LogUsageRequest(provider, model, input, output, cached, "key-1", timestamp, 850L,
        RequestStatus.SUCCESS, project);
  }

  /** The record a previous call to {@link #standardRequest()} would have stored. */
  private static UsageEvent storedStandardEvent() {
    return new UsageEvent("c1", "key-1", "openai", "gpt-4o", 10_000, 2_000, 4_000,
        new BigDecimal("0.040000"), 3, CALL_TIME, 850, RequestStatus.SUCCESS, "support-bot",
        NOW.minusSeconds(60));
  }

  private static BudgetStatus budget(List<Integer> crossed, List<Integer> newlyCrossed) {
    return new BudgetStatus(UUID.randomUUID(), BudgetScope.CLIENT, null,
        Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"),
        new BigDecimal("10.00"), new BigDecimal("8.50"), new BigDecimal("1.50"),
        new BigDecimal("85"), crossed, newlyCrossed);
  }

  /** Stubs a first-time report: no earlier record, a price in effect, and save echoing back. */
  private void stubNewReport() {
    when(repository.findByClientIdAndIdempotencyKey("c1", "key-1")).thenReturn(Optional.empty());
    when(catalogService.requirePriceAt(GPT_4O, CALL_TIME)).thenReturn(PRICE);
    when(repository.save(any(UsageEvent.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private UsageEvent savedEvent() {
    ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }

  @Nested
  class NewReport {

    @BeforeEach
    void setUp() {
      stubNewReport();
      when(budgetService.evaluateAfterUsage(any())).thenReturn(List.of());
    }

    @Test
    void pricesWithVersionInEffectAtRequestTimestamp() {
      LogUsageResponse response = service.log("c1", standardRequest());

      // 6,000 uncached x $2.50 + 4,000 cached x $1.25 + 2,000 output x $10.00, per million.
      assertThat(response.cost()).isEqualByComparingTo("0.04");
      assertThat(response.pricingVersion()).isEqualTo(3);
      assertThat(response.replayed()).isFalse();
      assertThat(response.recordId()).isEqualTo(savedEvent().getId());
    }

    @Test
    void storesNormalizedModelAndTrimmedProject() {
      service.log("c1",
          request(" OpenAI ", "GPT-4o", 10_000L, 2_000L, 4_000L, CALL_TIME, "  support-bot "));

      UsageEvent saved = savedEvent();
      assertThat(saved.getClientId()).isEqualTo("c1");
      assertThat(saved.getProvider()).isEqualTo("openai");
      assertThat(saved.getModelId()).isEqualTo("gpt-4o");
      assertThat(saved.getProjectTag()).isEqualTo("support-bot");
      assertThat(saved.getRecordedAt()).isEqualTo(NOW);
      verify(repository).flush();
    }

    @Test
    void blankProjectTagIsStoredAsNull() {
      service.log("c1", request("openai", "gpt-4o", 10_000L, 2_000L, 4_000L, CALL_TIME, "   "));

      assertThat(savedEvent().getProjectTag()).isNull();
    }

    @Test
    void missingCachedTokensAreTreatedAsZero() {
      LogUsageResponse response =
          service.log("c1", request("openai", "gpt-4o", 10_000L, 2_000L, null, CALL_TIME, null));

      // 10,000 uncached x $2.50 + 2,000 output x $10.00, per million.
      assertThat(response.cost()).isEqualByComparingTo("0.045");
      assertThat(savedEvent().getCachedInputTokens()).isZero();
    }

    @Test
    void evaluatesBudgetsAgainstTheSavedRecord() {
      service.log("c1", standardRequest());

      UsageEvent saved = savedEvent();
      verify(budgetService).evaluateAfterUsage(saved);
      verify(budgetService, never()).statusesFor(any());
    }
  }

  @Nested
  class Replay {

    private UsageEvent original;

    @BeforeEach
    void setUp() {
      original = storedStandardEvent();
      when(repository.findByClientIdAndIdempotencyKey("c1", "key-1"))
          .thenReturn(Optional.of(original));
    }

    @Test
    void identicalReportReturnsOriginalWithoutSaving() {
      when(budgetService.statusesFor(original)).thenReturn(List.of());

      LogUsageResponse response = service.log("c1", standardRequest());

      assertThat(response.replayed()).isTrue();
      assertThat(response.recordId()).isEqualTo(original.getId());
      assertThat(response.cost()).isEqualByComparingTo("0.04");
      verify(repository, never()).save(any());
      verify(budgetService, never()).evaluateAfterUsage(any());
      verifyNoInteractions(catalogService);
    }

    @Test
    void differentLatencyStatusOrProjectStillReplays() {
      when(budgetService.statusesFor(original)).thenReturn(List.of());
      LogUsageRequest retry = new LogUsageRequest("OpenAI", "GPT-4o", 10_000L, 2_000L, 4_000L,
          "key-1", CALL_TIME, 1_200L, RequestStatus.ERROR, "other-project");

      assertThat(service.log("c1", retry).replayed()).isTrue();
    }

    @Test
    void differentTokenCountsConflict() {
      LogUsageRequest changed =
          request("openai", "gpt-4o", 99_999L, 2_000L, 4_000L, CALL_TIME, "support-bot");

      assertThatThrownBy(() -> service.log("c1", changed))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("key-1");
      verify(repository, never()).save(any());
    }

    @Test
    void differentModelConflicts() {
      LogUsageRequest changed =
          request("openai", "gpt-4o-mini", 10_000L, 2_000L, 4_000L, CALL_TIME, "support-bot");

      assertThatThrownBy(() -> service.log("c1", changed)).isInstanceOf(ConflictException.class);
    }

    @Test
    void differentTimestampConflicts() {
      LogUsageRequest changed = request("openai", "gpt-4o", 10_000L, 2_000L, 4_000L,
          CALL_TIME.plusSeconds(1), "support-bot");

      assertThatThrownBy(() -> service.log("c1", changed)).isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  class Validation {

    @BeforeEach
    void setUp() {
      when(repository.findByClientIdAndIdempotencyKey("c1", "key-1")).thenReturn(Optional.empty());
    }

    @Test
    void rejectsMoreCachedTokensThanInputTokens() {
      LogUsageRequest invalid =
          request("openai", "gpt-4o", 1_000L, 2_000L, 1_001L, CALL_TIME, null);

      assertThatThrownBy(() -> service.log("c1", invalid))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("cachedInputTokens");
      verifyNoInteractions(catalogService);
      verify(repository, never()).save(any());
    }

    @Test
    void rejectsTimestampBeyondAllowedClockSkew() {
      Instant tooLate = NOW.plus(Duration.ofMinutes(5)).plusSeconds(1);
      LogUsageRequest invalid =
          request("openai", "gpt-4o", 1_000L, 2_000L, 0L, tooLate, null);

      assertThatThrownBy(() -> service.log("c1", invalid))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("future");
      verifyNoInteractions(catalogService);
    }

    @Test
    void acceptsTimestampWithinAllowedClockSkew() {
      Instant slightlyAhead = NOW.plus(Duration.ofMinutes(5));
      when(catalogService.requirePriceAt(GPT_4O, slightlyAhead)).thenReturn(PRICE);
      when(repository.save(any(UsageEvent.class))).thenAnswer(inv -> inv.getArgument(0));
      when(budgetService.evaluateAfterUsage(any())).thenReturn(List.of());

      LogUsageResponse response = service.log("c1",
          request("openai", "gpt-4o", 1_000L, 2_000L, 0L, slightlyAhead, null));

      assertThat(response.replayed()).isFalse();
    }

    @Test
    void propagatesUnknownModelErrorWithoutSaving() {
      ModelKey unknown = ModelKey.of("openai", "no-such-model");
      when(catalogService.requirePriceAt(unknown, CALL_TIME))
          .thenThrow(new ValidationException("Model openai/no-such-model is not in the catalog"));

      assertThatThrownBy(() -> service.log("c1",
          request("openai", "no-such-model", 1_000L, 2_000L, 0L, CALL_TIME, null)))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("not in the catalog");
      verify(repository, never()).save(any());
      verifyNoInteractions(budgetService);
    }
  }

  @Nested
  class BudgetFlags {

    @BeforeEach
    void setUp() {
      stubNewReport();
    }

    @Test
    void noApplicableBudgetsRaiseNoFlags() {
      when(budgetService.evaluateAfterUsage(any())).thenReturn(List.of());

      LogUsageResponse response = service.log("c1", standardRequest());

      assertThat(response.budgets()).isEmpty();
      assertThat(response.budgetThresholdBreached()).isFalse();
      assertThat(response.alertTriggered()).isFalse();
    }

    @Test
    void previouslyCrossedThresholdIsBreachedWithoutNewAlert() {
      when(budgetService.evaluateAfterUsage(any()))
          .thenReturn(List.of(budget(List.of(50), List.of())));

      LogUsageResponse response = service.log("c1", standardRequest());

      assertThat(response.budgetThresholdBreached()).isTrue();
      assertThat(response.alertTriggered()).isFalse();
    }

    @Test
    void newlyCrossedThresholdTriggersAlertAndReportsRemainingBudget() {
      BudgetStatus untouched = budget(List.of(), List.of());
      BudgetStatus justCrossed = budget(List.of(50, 80), List.of(80));
      when(budgetService.evaluateAfterUsage(any())).thenReturn(List.of(untouched, justCrossed));

      LogUsageResponse response = service.log("c1", standardRequest());

      assertThat(response.budgetThresholdBreached()).isTrue();
      assertThat(response.alertTriggered()).isTrue();
      assertThat(response.budgets()).containsExactly(untouched, justCrossed);
      assertThat(response.budgets().get(1).remaining()).isEqualByComparingTo("1.50");
    }
  }
}
