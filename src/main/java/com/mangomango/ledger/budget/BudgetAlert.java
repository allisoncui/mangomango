package com.mangomango.ledger.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Records that a threshold already fired for a rule in a period, so it fires only once. */
@Entity
@Table(
    name = "budget_alert",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"rule_id", "period_start", "threshold_percent"}))
public class BudgetAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "threshold_percent", nullable = false)
  private int thresholdPercent;

  @Column(nullable = false)
  private Instant triggeredAt;

  /** Required by JPA. */
  protected BudgetAlert() {}

  /** Creates an alert record. */
  public BudgetAlert(UUID ruleId, Instant periodStart, int thresholdPercent, Instant triggeredAt) {
    this.ruleId = ruleId;
    this.periodStart = periodStart;
    this.thresholdPercent = thresholdPercent;
    this.triggeredAt = triggeredAt;
  }

  public int getThresholdPercent() {
    return thresholdPercent;
  }
}
