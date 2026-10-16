package com.mangomango.ledger.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable pricing version of a model. New prices are stored as new rows so earlier
 * requests can still be priced with the version that was in effect at their timestamp.
 */
@Entity
@Table(
    name = "model_price",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "model_id", "version"}),
    indexes = @Index(columnList = "provider, model_id, effective_from"))
public class ModelPrice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String provider;

  @Column(name = "model_id", nullable = false)
  private String modelId;

  @Column(nullable = false)
  private int version;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal inputPricePerMillion;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal outputPricePerMillion;

  @Column(precision = 19, scale = 6)
  private BigDecimal cachedInputPricePerMillion;

  @Column(nullable = false)
  private int contextWindow;

  @Column(nullable = false)
  private int capabilityTier;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(nullable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected ModelPrice() {}

  /** Creates a new pricing version; {@code provider} and {@code modelId} must be normalized. */
  public ModelPrice(
      String provider,
      String modelId,
      int version,
      BigDecimal inputPricePerMillion,
      BigDecimal outputPricePerMillion,
      BigDecimal cachedInputPricePerMillion,
      int contextWindow,
      int capabilityTier,
      Instant effectiveFrom,
      Instant createdAt) {
    this.provider = provider;
    this.modelId = modelId;
    this.version = version;
    this.inputPricePerMillion = inputPricePerMillion;
    this.outputPricePerMillion = outputPricePerMillion;
    this.cachedInputPricePerMillion = cachedInputPricePerMillion;
    this.contextWindow = contextWindow;
    this.capabilityTier = capabilityTier;
    this.effectiveFrom = effectiveFrom;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getProvider() {
    return provider;
  }

  public String getModelId() {
    return modelId;
  }

  public int getVersion() {
    return version;
  }

  public BigDecimal getInputPricePerMillion() {
    return inputPricePerMillion;
  }

  public BigDecimal getOutputPricePerMillion() {
    return outputPricePerMillion;
  }

  /** Returns the cached-input price, falling back to the normal input price when unset. */
  public BigDecimal effectiveCachedInputPricePerMillion() {
    return cachedInputPricePerMillion == null ? inputPricePerMillion : cachedInputPricePerMillion;
  }

  public BigDecimal getCachedInputPricePerMillion() {
    return cachedInputPricePerMillion;
  }

  public int getContextWindow() {
    return contextWindow;
  }

  public int getCapabilityTier() {
    return capabilityTier;
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Returns the normalized provider and model identity. */
  public ModelKey key() {
    return new ModelKey(provider, modelId);
  }
}
