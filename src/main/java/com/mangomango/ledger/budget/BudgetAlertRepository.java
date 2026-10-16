package com.mangomango.ledger.budget;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for fired budget alerts. */
public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, Long> {

  /** Returns alerts already fired for a rule in the period starting at {@code periodStart}. */
  List<BudgetAlert> findByRuleIdAndPeriodStart(UUID ruleId, Instant periodStart);

  /** Deletes all alert history of a rule. */
  void deleteByRuleId(UUID ruleId);
}
