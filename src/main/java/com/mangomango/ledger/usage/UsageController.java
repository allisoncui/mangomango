// Generated using Claude Code.

package com.mangomango.ledger.usage;

import com.mangomango.ledger.common.ClientHeader;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operation 1: Log Token Usage. */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

  private final UsageService usageService;

  /** Creates the controller. */
  public UsageController(UsageService usageService) {
    this.usageService = usageService;
  }

  /**
   * Records a call; returns 201 for a new record and 200 for an idempotent replay. A blank
   * {@code X-Client-Id} is rejected with 400 so usage is never stored under an empty client.
   */
  @PostMapping
  public ResponseEntity<LogUsageResponse> log(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @Valid @RequestBody LogUsageRequest request) {
    LogUsageResponse response = usageService.log(ClientHeader.require(clientId), request);
    return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(response);
  }
}
