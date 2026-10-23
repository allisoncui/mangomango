// Generated using Claude Code.

package com.mangomango.ledger.usage;

import com.mangomango.ledger.budget.BudgetService;
import com.mangomango.ledger.budget.BudgetStatus;
import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.CostCalculator;
import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.ConflictException;
import com.mangomango.ledger.common.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operation 1: Log Token Usage. */
@Service
public class UsageService {

  /** How far in the future a request timestamp may be, to tolerate client clock skew. */
  private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

  private final UsageEventRepository repository;
  private final CatalogService catalogService;
  private final BudgetService budgetService;
  private final Clock clock;

  /** Creates the service. */
  public UsageService(
      UsageEventRepository repository,
      CatalogService catalogService,
      BudgetService budgetService,
      Clock clock) {
    this.repository = repository;
    this.catalogService = catalogService;
    this.budgetService = budgetService;
    this.clock = clock;
  }

  /**
   * Prices and records one call, or returns the original record if the idempotency key was
   * already used by this client for the same call.
   *
   * @throws ConflictException if the idempotency key was already used for a different call
   */
  @Transactional
  public LogUsageResponse log(String clientId, LogUsageRequest request) {
    Optional<UsageEvent> existing =
        repository.findByClientIdAndIdempotencyKey(clientId, request.idempotencyKey());
    if (existing.isPresent()) {
      UsageEvent original = existing.get();
      // A reused key with different billable fields is a client bug, not a retry; replaying
      // the original would silently drop the new call.
      if (!describesSameCall(original, request)) {
        throw new ConflictException("idempotencyKey '" + request.idempotencyKey()
            + "' was already used for a different call");
      }
      return response(original, true, budgetService.statusesFor(original));
    }

    long cached = cachedTokens(request);
    if (cached > request.inputTokens()) {
      throw new ValidationException("cachedInputTokens cannot exceed inputTokens");
    }
    Instant now = clock.instant();
    if (request.requestTimestamp().isAfter(now.plus(MAX_CLOCK_SKEW))) {
      throw new ValidationException("requestTimestamp is in the future");
    }
    ModelKey key = ModelKey.of(request.provider(), request.modelId());
    ModelPrice price = catalogService.requirePriceAt(key, request.requestTimestamp());
    BigDecimal cost =
        CostCalculator.cost(price, request.inputTokens(), cached, request.outputTokens());
    String project = blankToNull(request.projectTag());

    UsageEvent event =
        repository.save(
            new UsageEvent(
                clientId,
                request.idempotencyKey(),
                key.provider(),
                key.modelId(),
                request.inputTokens(),
                request.outputTokens(),
                cached,
                cost,
                price.getVersion(),
                request.requestTimestamp(),
                request.latencyMs(),
                request.status(),
                project,
                now));
    repository.flush();
    return response(event, false, budgetService.evaluateAfterUsage(event));
  }

  private static LogUsageResponse response(
      UsageEvent event, boolean replayed, List<BudgetStatus> budgets) {
    return new LogUsageResponse(
        event.getId(),
        replayed,
        event.getCost(),
        event.getPricingVersion(),
        budgets,
        budgets.stream().anyMatch(BudgetStatus::anyThresholdCrossed),
        budgets.stream().anyMatch(b -> !b.newlyCrossedThresholds().isEmpty()));
  }

  /**
   * True when a retried request reports the same call as the stored record. Only the fields that
   * determine cost are compared; latency, status and project tag may legitimately differ when a
   * client re-sends a report.
   */
  private static boolean describesSameCall(UsageEvent event, LogUsageRequest request) {
    ModelKey key = ModelKey.of(request.provider(), request.modelId());
    return event.getProvider().equals(key.provider())
        && event.getModelId().equals(key.modelId())
        && event.getInputTokens() == request.inputTokens()
        && event.getOutputTokens() == request.outputTokens()
        && event.getCachedInputTokens() == cachedTokens(request)
        && event.getRequestTimestamp().equals(request.requestTimestamp());
  }

  private static long cachedTokens(LogUsageRequest request) {
    return request.cachedInputTokens() == null ? 0 : request.cachedInputTokens();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
