package com.mangomango.ledger.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.usage.RequestStatus;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
  private static final Instant MONTH_START = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant MONTH_END = Instant.parse("2026-11-01T00:00:00Z");

  @Mock private BudgetRuleRepository ruleRepository;
  @Mock private BudgetAlertRepository alertRepository;
  @Mock private UsageEventRepository usageRepository;
  @Mock private ApplicationEventPublisher publisher;

  private BudgetService service;

  @BeforeEach
  void setUp() {
    service = new BudgetService(ruleRepository, alertRepository, usageRepository, publisher,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static BudgetRule rule(BudgetScope scope, String value, String callback) {
    BudgetRule rule = new BudgetRule("c1", NOW);
    rule.update(scope, value, BudgetPeriod.MONTHLY, new BigDecimal("10.00"), List.of(50, 80, 100),
        callback, NOW);
    return rule;
  }

  private static UsageEvent event(String provider, String project) {
    return new UsageEvent("c1", "k", provider, "m", 1, 1, 0, BigDecimal.ONE, 1, NOW, 10,
        RequestStatus.SUCCESS, project, NOW);
  }

  @Test
  void firesEachNewlyCrossedThresholdOnceWithWebhook() {
    BudgetRule rule = rule(BudgetScope.CLIENT, null, "https://example.com/hook");
    when(ruleRepository.findByClientIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(rule));
    when(usageRepository.sumCost("c1", MONTH_START, MONTH_END)).thenReturn(new BigDecimal("8.5"));
    when(alertRepository.findByRuleIdAndPeriodStart(rule.getId(), MONTH_START))
        .thenReturn(List.of(new BudgetAlert(rule.getId(), MONTH_START, 50, NOW)));

    List<BudgetStatus> statuses = service.evaluateAfterUsage(event("openai", null));

    BudgetStatus status = statuses.get(0);
    assertThat(status.percentUsed()).isEqualByComparingTo("85");
    assertThat(status.crossedThresholds()).containsExactly(50, 80);
    assertThat(status.newlyCrossedThresholds()).containsExactly(80);
    assertThat(status.remaining()).isEqualByComparingTo("1.5");
    ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
    verify(publisher, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().thresholdPercent()).isEqualTo(80);
    verify(alertRepository, times(1)).save(any());
  }

  @Test
  void recordsAlertButSendsNoWebhookWithoutCallbackUrl() {
    BudgetRule rule = rule(BudgetScope.CLIENT, null, null);
    when(ruleRepository.findByClientIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(rule));
    when(usageRepository.sumCost("c1", MONTH_START, MONTH_END)).thenReturn(new BigDecimal("6"));

    service.evaluateAfterUsage(event("openai", null));

    verify(alertRepository, times(1)).save(any());
    verify(publisher, never()).publishEvent(any());
  }

  @Test
  void projectRuleIgnoresEventsFromOtherProjects() {
    BudgetRule rule = rule(BudgetScope.PROJECT, "support-bot", null);
    when(ruleRepository.findByClientIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(rule));

    assertThat(service.evaluateAfterUsage(event("openai", "other"))).isEmpty();
    verify(usageRepository, never()).sumCostForProject(any(), any(), any(), any());
  }

  @Test
  void providerRuleSumsOnlyThatProvider() {
    BudgetRule rule = rule(BudgetScope.PROVIDER, "anthropic", null);
    when(ruleRepository.findByClientIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(rule));
    when(usageRepository.sumCostForProvider(eq("c1"), eq("anthropic"), any(), any()))
        .thenReturn(new BigDecimal("1"));

    List<BudgetStatus> statuses = service.evaluateAfterUsage(event("anthropic", null));

    assertThat(statuses).singleElement()
        .satisfies(s -> assertThat(s.crossedThresholds()).isEmpty());
  }

  @Test
  void rejectsScopeValueForClientScope() {
    BudgetRuleRequest request = new BudgetRuleRequest(BudgetScope.CLIENT, "x",
        BudgetPeriod.MONTHLY, BigDecimal.TEN, List.of(80), null);

    assertThatThrownBy(() -> service.create("c1", request))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNonHttpCallbackUrl() {
    BudgetRuleRequest request = new BudgetRuleRequest(BudgetScope.CLIENT, null,
        BudgetPeriod.MONTHLY, BigDecimal.TEN, List.of(80), "ftp://example.com/x");

    assertThatThrownBy(() -> service.create("c1", request))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("callbackUrl");
  }
}
