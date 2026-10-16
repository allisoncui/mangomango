package com.mangomango.ledger.catalog;

import com.mangomango.ledger.common.ForbiddenException;
import com.mangomango.ledger.common.LedgerProperties;
import com.mangomango.ledger.common.NotFoundException;
import com.mangomango.ledger.common.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the versioned model catalog and resolves the price in effect at a point in time. */
@Service
public class CatalogService {

  private final ModelPriceRepository repository;
  private final LedgerProperties properties;
  private final Clock clock;

  /** Creates the service. */
  public CatalogService(ModelPriceRepository repository, LedgerProperties properties,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Stores a new pricing version without modifying earlier versions. Admin clients only. */
  @Transactional
  public CatalogWriteResponse addVersion(String clientId, CatalogEntryRequest request) {
    if (!properties.adminClients().contains(clientId)) {
      throw new ForbiddenException("Only administrator clients can write to the catalog");
    }
    ModelKey key = ModelKey.of(request.provider(), request.modelId());
    if (key.provider().isEmpty() || key.modelId().isEmpty()) {
      throw new ValidationException("provider and modelId must not be blank");
    }
    int nextVersion =
        repository
            .findTopByProviderAndModelIdOrderByVersionDesc(key.provider(), key.modelId())
            .map(p -> p.getVersion() + 1)
            .orElse(1);
    Instant now = clock.instant();
    ModelPrice saved =
        repository.save(
            new ModelPrice(
                key.provider(),
                key.modelId(),
                nextVersion,
                request.inputPricePerMillion(),
                request.outputPricePerMillion(),
                request.cachedInputPricePerMillion(),
                request.contextWindow(),
                request.capabilityTier(),
                request.effectiveDate() == null ? now : request.effectiveDate(),
                now));
    CatalogEntryResponse active =
        priceAt(key, now).map(CatalogEntryResponse::from).orElse(null);
    return new CatalogWriteResponse(CatalogEntryResponse.from(saved), active);
  }

  /** Returns the version in effect at {@code at}, if the model had a price then. */
  @Transactional(readOnly = true)
  public Optional<ModelPrice> priceAt(ModelKey key, Instant at) {
    return repository.findInEffectAt(key.provider(), key.modelId(), at);
  }

  /**
   * Returns the price in effect at {@code at}, or throws a validation error naming the reason
   * (unknown model, or no price yet effective at that time).
   */
  @Transactional(readOnly = true)
  public ModelPrice requirePriceAt(ModelKey key, Instant at) {
    return priceAt(key, at)
        .orElseThrow(
            () ->
                new ValidationException(
                    repository.existsByProviderAndModelId(key.provider(), key.modelId())
                        ? "No price for " + key + " is in effect at " + at
                        : "Model " + key + " is not in the catalog"));
  }

  /** Returns the version in effect now, or the latest stored version if none is in effect yet. */
  @Transactional(readOnly = true)
  public Optional<ModelPrice> currentOrLatest(ModelKey key) {
    return priceAt(key, clock.instant())
        .or(() -> repository.findTopByProviderAndModelIdOrderByVersionDesc(
            key.provider(), key.modelId()));
  }

  /** Returns the currently active version of every model that has one. */
  @Transactional(readOnly = true)
  public List<ModelPrice> activeModels() {
    Instant now = clock.instant();
    return repository.findAllModelKeys().stream()
        .sorted(Comparator.comparing(ModelKey::provider).thenComparing(ModelKey::modelId))
        .map(k -> priceAt(k, now))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Returns the currently active version of one model. */
  @Transactional(readOnly = true)
  public ModelPrice activeModel(ModelKey key) {
    return priceAt(key, clock.instant())
        .orElseThrow(() -> new NotFoundException("No active catalog entry for " + key));
  }

  /** Returns every stored version of one model, oldest first. */
  @Transactional(readOnly = true)
  public List<ModelPrice> history(ModelKey key) {
    List<ModelPrice> versions =
        repository.findByProviderAndModelIdOrderByVersionAsc(key.provider(), key.modelId());
    if (versions.isEmpty()) {
      throw new NotFoundException("Model " + key + " is not in the catalog");
    }
    return versions;
  }
}
