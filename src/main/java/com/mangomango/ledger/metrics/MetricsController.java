package com.mangomango.ledger.metrics;

import com.mangomango.ledger.common.ClientHeader;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operation 2: Get Usage Metrics &amp; Aggregations. */
@RestController
@RequestMapping("/api/v1/usage/metrics")
public class MetricsController {

  private final MetricsService metricsService;

  /** Creates the controller. */
  public MetricsController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  /** Aggregates the caller's usage in {@code [start, end)}. */
  @GetMapping
  public UsageSummary summarize(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @RequestParam Instant start,
      @RequestParam Instant end,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String project,
      @RequestParam(defaultValue = "DAILY") Granularity granularity,
      @RequestParam(required = false) GroupBy groupBy) {
    return metricsService.summarize(
        clientId, new MetricsQuery(start, end, provider, model, project, granularity, groupBy));
  }
}
