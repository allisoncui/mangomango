package com.mangomango.ledger.budget;

import com.mangomango.ledger.common.ClientHeader;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operation 4: Manage Budget &amp; Alert Rules. */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

  private final BudgetService budgetService;

  /** Creates the controller. */
  public BudgetController(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  /** Creates a rule. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BudgetRuleResponse create(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @Valid @RequestBody BudgetRuleRequest request) {
    return budgetService.create(clientId, request);
  }

  /** Replaces a rule. */
  @PutMapping("/{ruleId}")
  public BudgetRuleResponse update(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @PathVariable UUID ruleId,
      @Valid @RequestBody BudgetRuleRequest request) {
    return budgetService.update(clientId, ruleId, request);
  }

  /** Lists the client's rules. */
  @GetMapping
  public List<BudgetRuleResponse> list(@RequestHeader(ClientHeader.NAME) String clientId) {
    return budgetService.list(clientId);
  }

  /** Returns one rule. */
  @GetMapping("/{ruleId}")
  public BudgetRuleResponse get(
      @RequestHeader(ClientHeader.NAME) String clientId, @PathVariable UUID ruleId) {
    return budgetService.get(clientId, ruleId);
  }

  /** Deletes one rule. */
  @DeleteMapping("/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestHeader(ClientHeader.NAME) String clientId, @PathVariable UUID ruleId) {
    budgetService.delete(clientId, ruleId);
  }
}
