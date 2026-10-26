// Generated using Claude Code.

package com.mangomango.ledger.savings;

import com.mangomango.ledger.catalog.CatalogService;
import com.mangomango.ledger.catalog.CostCalculator;
import com.mangomango.ledger.catalog.ModelKey;
import com.mangomango.ledger.catalog.ModelPrice;
import com.mangomango.ledger.common.Money;
import com.mangomango.ledger.common.ValidationException;
import com.mangomango.ledger.metrics.UsageStats;
import com.mangomango.ledger.usage.UsageEvent;
import com.mangomango.ledger.usage.UsageEventRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operation 6: Analyze Cost Savings. */
@Service
public class SavingsService {

  private final UsageEventRepository usageRepository;
  private final CatalogService catalogService;

  /** Creates the service. */
  public SavingsService(UsageEventRepository usageRepository, CatalogService catalogService) {
    this.usageRepository = usageRepository;
    this.catalogService = catalogService;
  }

  /** Re-prices the client's recorded token volumes under each alternative model. */
  @Transactional(readOnly = true)
  public SavingsResponse analyze(String clientId, SavingsRequest request) {
    if (!request.start().isBefore(request.end())) {
      throw new ValidationException("start must be before end");
    }
    String project =
        request.projectTag() == null || request.projectTag().isBlank()
            ? null
            : request.projectTag().trim();
    List<UsageEvent> inRange =
            usageRepository.findInRange(clientId, request.start(), request.end()).stream()
                    .filter(e -> project == null || project.equals(e.getProjectTag()))
                    .toList();

    // A used model's tier sets the floor for alternatives. If a used model is missing from the
    // catalog its tier is unknown, so its calls are left out rather than lowering the floor.
    Map<ModelKey, Integer> tierByModel = new HashMap<>();
    for (UsageEvent e : inRange) {
      ModelKey key = new ModelKey(e.getProvider(), e.getModelId());
      if (!tierByModel.containsKey(key)) {
        catalogService.currentOrLatest(key)
                .ifPresent(p -> tierByModel.put(key, p.getCapabilityTier()));
      }
    }
    List<String> unknownModelsUsed =
            inRange.stream()
                    .map(e -> new ModelKey(e.getProvider(), e.getModelId()))
                    .filter(key -> !tierByModel.containsKey(key))
                    .map(ModelKey::toString)
                    .distinct()
                    .sorted()
                    .toList();
    List<UsageEvent> events =
            inRange.stream()
                    .filter(
                            e -> tierByModel.containsKey(
                                    new ModelKey(e.getProvider(), e.getModelId())))
                    .toList();

    BigDecimal actual =
            Money.round(events.stream().map(UsageEvent::getCost).reduce(BigDecimal.ZERO,
                    BigDecimal::add));
    long largest = events.stream().mapToLong(UsageEvent::totalTokens).max().orElse(0);
    int highestTier = tierByModel.values().stream().mapToInt(Integer::intValue).max().orElse(0);

    List<SavingsResponse.Alternative> alternatives = new ArrayList<>();
    List<SavingsResponse.Excluded> excluded = new ArrayList<>();
    for (ModelKey alt : alternativesToCompare(request)) {
      Optional<ModelPrice> current = catalogService.currentOrLatest(alt);
      if (current.isEmpty()) {
        excluded.add(new SavingsResponse.Excluded(alt.provider(), alt.modelId(),
            "not in the catalog"));
        continue;
      }
      ModelPrice price = current.get();
      if (price.getContextWindow() < largest) {
        excluded.add(new SavingsResponse.Excluded(alt.provider(), alt.modelId(),
            "context window " + price.getContextWindow() + " < largest request " + largest));
        continue;
      }
      if (price.getCapabilityTier() < highestTier) {
        excluded.add(new SavingsResponse.Excluded(alt.provider(), alt.modelId(),
            "capability tier " + price.getCapabilityTier() + " < tier used " + highestTier));
        continue;
      }
      alternatives.add(reprice(alt, price, events, actual));
    }
    alternatives.sort(Comparator.comparing(SavingsResponse.Alternative::projectedCost));
    return new SavingsResponse(
        request.start(),
        request.end(),
        project,
        events.size(),
        actual,
        largest,
        highestTier,
        alternatives,
        excluded,
        unknownModelsUsed);
  }

  private SavingsResponse.Alternative reprice(
      ModelKey alt, ModelPrice current, List<UsageEvent> events, BigDecimal actual) {
    BigDecimal projected = BigDecimal.ZERO;
    long atCurrentRate = 0;
    List<UsageEvent> ownHistory = new ArrayList<>();
    for (UsageEvent e : events) {
      Optional<ModelPrice> then = catalogService.priceAt(alt, e.getRequestTimestamp());
      if (then.isEmpty()) {
        atCurrentRate++;
      }
      projected =
          projected.add(
              CostCalculator.cost(
                  then.orElse(current),
                  e.getInputTokens(),
                  e.getCachedInputTokens(),
                  e.getOutputTokens()));
      if (alt.provider().equals(e.getProvider()) && alt.modelId().equals(e.getModelId())) {
        ownHistory.add(e);
      }
    }
    projected = Money.round(projected);
    BigDecimal savings = actual.subtract(projected);
    return new SavingsResponse.Alternative(
        alt.provider(),
        alt.modelId(),
        projected,
        savings,
        Money.percent(savings, actual),
        UsageStats.of(ownHistory).averageLatencyMs(),
        !ownHistory.isEmpty(),
        atCurrentRate);
  }

  private Set<ModelKey> alternativesToCompare(SavingsRequest request) {
    if (request.alternatives() == null || request.alternatives().isEmpty()) {
      return catalogService.activeModels().stream()
          .map(ModelPrice::key)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    return request.alternatives().stream()
        .map(r -> ModelKey.of(r.provider(), r.modelId()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
