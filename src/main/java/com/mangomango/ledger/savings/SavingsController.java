// Generated using Claude Code.

package com.mangomango.ledger.savings;

import com.mangomango.ledger.common.ClientHeader;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operation 6: Analyze Cost Savings. */
@RestController
@RequestMapping("/api/v1/savings")
public class SavingsController {

  private final SavingsService savingsService;

  /** Creates the controller. */
  public SavingsController(SavingsService savingsService) {
    this.savingsService = savingsService;
  }

  /** Compares the caller's actual spending with alternative models. */
  @PostMapping("/analysis")
  public SavingsResponse analyze(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @Valid @RequestBody SavingsRequest request) {
    return savingsService.analyze(clientId, request);
  }
}
