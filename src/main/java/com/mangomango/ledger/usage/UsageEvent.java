package com.mangomango.ledger.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One priced AI model call reported by a client. */
@Entity
@Table(
    name = "usage_event",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "idempotency_key"}),
    indexes = @Index(columnList = "client_id, request_timestamp"))
public class UsageEvent {

  @Id private UUID id;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private String modelId;

  @Column(nullable = false)
  private long inputTokens;

  @Column(nullable = false)
  private long outputTokens;

  @Column(nullable = false)
  private long cachedInputTokens;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal cost;

  @Column(nullable = false)
  private int pricingVersion;

  @Column(name = "request_timestamp", nullable = false)
  private Instant requestTimestamp;

  @Column(nullable = false)
  private long latencyMs;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RequestStatus status;

  private String projectTag;

  @Column(nullable = false)
  private Instant recordedAt;

  /** Required by JPA. */
  protected UsageEvent() {}

  /** Creates a new event with a random ID. */
  public UsageEvent(
      String clientId,
      String idempotencyKey,
      String provider,
      String modelId,
      long inputTokens,
      long outputTokens,
      long cachedInputTokens,
      BigDecimal cost,
      int pricingVersion,
      Instant requestTimestamp,
      long latencyMs,
      RequestStatus status,
      String projectTag,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.clientId = clientId;
    this.idempotencyKey = idempotencyKey;
    this.provider = provider;
    this.modelId = modelId;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.cachedInputTokens = cachedInputTokens;
    this.cost = cost;
    this.pricingVersion = pricingVersion;
    this.requestTimestamp = requestTimestamp;
    this.latencyMs = latencyMs;
    this.status = status;
    this.projectTag = projectTag;
    this.recordedAt = recordedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getProvider() {
    return provider;
  }

  public String getModelId() {
    return modelId;
  }

  public long getInputTokens() {
    return inputTokens;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public long getCachedInputTokens() {
    return cachedInputTokens;
  }

  /** Returns input plus output tokens. */
  public long totalTokens() {
    return inputTokens + outputTokens;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public int getPricingVersion() {
    return pricingVersion;
  }

  public Instant getRequestTimestamp() {
    return requestTimestamp;
  }

  public long getLatencyMs() {
    return latencyMs;
  }

  public RequestStatus getStatus() {
    return status;
  }

  public boolean isError() {
    return status == RequestStatus.ERROR;
  }

  public String getProjectTag() {
    return projectTag;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
