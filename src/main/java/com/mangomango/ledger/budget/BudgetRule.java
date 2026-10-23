// Generated using Claude Code.

package com.mangomango.ledger.budget;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A spending limit with alert thresholds, owned by one client. */
@Entity
@Table(name = "budget_rule")
public class BudgetRule {

  @Id private UUID id;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BudgetScope scope;

  private String scopeValue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BudgetPeriod period;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal limitAmount;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "budget_rule_threshold", joinColumns = @JoinColumn(name = "rule_id"))
  @OrderColumn(name = "position")
  @Column(name = "threshold_percent")
  private List<Integer> thresholds = new ArrayList<>();

  private String callbackUrl;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  /** Required by JPA. */
  protected BudgetRule() {}

  /** Creates a rule with a random ID. */
  public BudgetRule(String clientId, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.clientId = clientId;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  /** Replaces every editable field. */
  public void update(
      BudgetScope scope,
      String scopeValue,
      BudgetPeriod period,
      BigDecimal limitAmount,
      List<Integer> thresholds,
      String callbackUrl,
      Instant updatedAt) {
    this.scope = scope;
    this.scopeValue = scopeValue;
    this.period = period;
    this.limitAmount = limitAmount;
    this.thresholds.clear();
    this.thresholds.addAll(thresholds);
    this.callbackUrl = callbackUrl;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public BudgetScope getScope() {
    return scope;
  }

  public String getScopeValue() {
    return scopeValue;
  }

  public BudgetPeriod getPeriod() {
    return period;
  }

  public BigDecimal getLimitAmount() {
    return limitAmount;
  }

  public List<Integer> getThresholds() {
    return List.copyOf(thresholds);
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
