// Generated using Claude Code.

package com.mangomango.ledger.budget;

import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.common.Money;
import com.mangomango.ledger.common.NotFoundException;
import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operation 4 plus the budget checks that Log Token Usage and Recommendation rely on. */
@Service
public class BudgetService {

  private final BudgetRuleRepository ruleRepository;
  private final BudgetAlertRepository alertRepository;
  private final UsageEventRepository usageRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /** Creates the service. */
  public BudgetService(
      BudgetRuleRepository ruleRepository,
      BudgetAlertRepository alertRepository,
      UsageEventRepository usageRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.ruleRepository = ruleRepository;
    this.alertRepository = alertRepository;
    this.usageRepository = usageRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /** Creates a new rule and reports current spending against it. */
  @Transactional
  public BudgetRuleResponse create(String clientId, BudgetRuleRequest request) {
    Instant now = clock.instant();
    BudgetRule rule = new BudgetRule(clientId, now);
    apply(rule, request, now);
    ruleRepository.save(rule);
    return BudgetRuleResponse.of("created", rule, status(rule, now, List.of()));
  }

  /** Replaces an existing rule and re-evaluates current spending against it. */
  @Transactional
  public BudgetRuleResponse update(String clientId, UUID ruleId, BudgetRuleRequest request) {
    BudgetRule rule = find(clientId, ruleId);
    Instant now = clock.instant();
    apply(rule, request, now);
    return BudgetRuleResponse.of("updated", rule, status(rule, now, List.of()));
  }

  /** Returns one rule with its current spending. */
  @Transactional(readOnly = true)
  public BudgetRuleResponse get(String clientId, UUID ruleId) {
    BudgetRule rule = find(clientId, ruleId);
    return BudgetRuleResponse.of("ok", rule, status(rule, clock.instant(), List.of()));
  }

  /** Returns every rule the client owns with current spending. */
  @Transactional(readOnly = true)
  public List<BudgetRuleResponse> list(String clientId) {
    Instant now = clock.instant();
    return ruleRepository.findByClientIdOrderByCreatedAtAsc(clientId).stream()
        .map(r -> BudgetRuleResponse.of("ok", r, status(r, now, List.of())))
        .toList();
  }

  /** Deletes a rule and its alert history. */
  @Transactional
  public void delete(String clientId, UUID ruleId) {
    BudgetRule rule = find(clientId, ruleId);
    alertRepository.deleteByRuleId(rule.getId());
    ruleRepository.delete(rule);
  }

  /**
   * Evaluates every rule that applies to a just-recorded event. Thresholds crossed for the first
   * time in the current period are recorded and published for webhook delivery after commit.
   */
  @Transactional
  public List<BudgetStatus> evaluateAfterUsage(UsageEvent event) {
    Instant now = clock.instant();
    List<BudgetStatus> statuses = new ArrayList<>();
    for (BudgetRule rule : applicableRules(event)) {
      BudgetStatus current = status(rule, now, List.of());
      Set<Integer> alreadyFired =
          alertRepository.findByRuleIdAndPeriodStart(rule.getId(), current.periodStart()).stream()
              .map(BudgetAlert::getThresholdPercent)
              .collect(Collectors.toSet());
      List<Integer> newlyCrossed =
          current.crossedThresholds().stream().filter(t -> !alreadyFired.contains(t)).toList();
      for (int threshold : newlyCrossed) {
        alertRepository.save(new BudgetAlert(rule.getId(), current.periodStart(), threshold, now));
        if (rule.getCallbackUrl() != null) {
          eventPublisher.publishEvent(
              new BudgetAlertEvent(
                  rule.getClientId(),
                  rule.getId(),
                  threshold,
                  current.percentUsed(),
                  current.spent(),
                  current.limit(),
                  current.periodStart(),
                  rule.getCallbackUrl()));
        }
      }
      statuses.add(withNewlyCrossed(current, newlyCrossed));
    }
    return statuses;
  }

  /** Returns current status of every rule that applies to an event, without firing alerts. */
  @Transactional(readOnly = true)
  public List<BudgetStatus> statusesFor(UsageEvent event) {
    Instant now = clock.instant();
    return applicableRules(event).stream().map(r -> status(r, now, List.of())).toList();
  }

  /** Returns the current status of every rule the client owns. */
  @Transactional(readOnly = true)
  public List<BudgetStatus> statusesForClient(String clientId) {
    Instant now = clock.instant();
    return ruleRepository.findByClientIdOrderByCreatedAtAsc(clientId).stream()
        .map(r -> status(r, now, List.of()))
        .toList();
  }

  private List<BudgetRule> applicableRules(UsageEvent event) {
    return ruleRepository.findByClientIdOrderByCreatedAtAsc(event.getClientId()).stream()
        .filter(r -> appliesTo(r, event))
        .toList();
  }

  static boolean appliesTo(BudgetRule rule, UsageEvent event) {
    return switch (rule.getScope()) {
      case CLIENT -> true;
      case PROJECT -> rule.getScopeValue().equals(event.getProjectTag());
      case PROVIDER -> rule.getScopeValue().equals(event.getProvider());
    };
  }

  BudgetStatus status(BudgetRule rule, Instant now, List<Integer> newlyCrossed) {
    Instant start = rule.getPeriod().start(now);
    Instant end = rule.getPeriod().end(now);
    BigDecimal spent =
        Money.round(
            switch (rule.getScope()) {
              case CLIENT -> usageRepository.sumCost(rule.getClientId(), start, end);
              case PROJECT ->
                  usageRepository.sumCostForProject(
                      rule.getClientId(), rule.getScopeValue(), start, end);
              case PROVIDER ->
                  usageRepository.sumCostForProvider(
                      rule.getClientId(), rule.getScopeValue(), start, end);
            });
    BigDecimal percent = Money.percent(spent, rule.getLimitAmount());
    List<Integer> crossed =
        rule.getThresholds().stream()
            .filter(t -> percent.compareTo(BigDecimal.valueOf(t)) >= 0)
            .sorted()
            .toList();
    BigDecimal remaining = rule.getLimitAmount().subtract(spent).max(BigDecimal.ZERO);
    return new BudgetStatus(
        rule.getId(),
        rule.getScope(),
        rule.getScopeValue(),
        start,
        end,
        rule.getLimitAmount(),
        spent,
        Money.round(remaining),
        percent,
        crossed,
        newlyCrossed);
  }

  private static BudgetStatus withNewlyCrossed(BudgetStatus s, List<Integer> newlyCrossed) {
    return new BudgetStatus(
        s.ruleId(),
        s.scope(),
        s.scopeValue(),
        s.periodStart(),
        s.periodEnd(),
        s.limit(),
        s.spent(),
        s.remaining(),
        s.percentUsed(),
        s.crossedThresholds(),
        newlyCrossed);
  }

  private BudgetRule find(String clientId, UUID ruleId) {
    return ruleRepository
        .findByIdAndClientId(ruleId, clientId)
        .orElseThrow(() -> new NotFoundException("Budget rule " + ruleId + " not found"));
  }

  private static void apply(BudgetRule rule, BudgetRuleRequest request, Instant now) {
    String scopeValue = request.scopeValue() == null ? null : request.scopeValue().trim();
    if (request.scope() == BudgetScope.CLIENT) {
      if (scopeValue != null && !scopeValue.isEmpty()) {
        throw new ValidationException("scopeValue must be omitted for CLIENT scope");
      }
      scopeValue = null;
    } else if (scopeValue == null || scopeValue.isEmpty()) {
      throw new ValidationException("scopeValue is required for " + request.scope() + " scope");
    } else if (request.scope() == BudgetScope.PROVIDER) {
      scopeValue = ModelKey.normalize(scopeValue);
    }
    validateCallbackUrl(request.callbackUrl());
    List<Integer> thresholds = request.thresholds().stream().distinct().sorted().toList();
    rule.update(
        request.scope(),
        scopeValue,
        request.period(),
        request.limitAmount(),
        thresholds,
        request.callbackUrl(),
        now);
  }

  private static void validateCallbackUrl(String url) {
    if (url == null) {
      return;
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      if (uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) {
        throw new ValidationException("callbackUrl must be an absolute http(s) URL");
      }
    } catch (URISyntaxException ex) {
      throw new ValidationException("callbackUrl is not a valid URL");
    }
  }
}
