package com.mangomango.ledger.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for budget rules. */
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, UUID> {

  /** Returns a client's rules, oldest first. */
  List<BudgetRule> findByClientIdOrderByCreatedAtAsc(String clientId);

  /** Returns a rule only if it belongs to {@code clientId}. */
  Optional<BudgetRule> findByIdAndClientId(UUID id, String clientId);
}
