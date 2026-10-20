package com.mangomango.ledger.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final PrintStream stream = new PrintStream(buffer, true, StandardCharsets.UTF_8);

  private String render(UsageSummaryDto summary, String... args) {
    new ReportFormatter(stream).print(CliOptions.parse(args, CLOCK), summary);
    stream.flush();
    return buffer.toString(StandardCharsets.UTF_8);
  }

  private static UsageSummaryDto summary(UsageSummaryDto.Stats totals) {
    return new UsageSummaryDto(
        totals,
        List.of(
            new UsageSummaryDto.ModelCost(
                "openai", "gpt-4o", 2, 2400, new BigDecimal("0.040000"))));
  }

  private static UsageSummaryDto.Stats stats(long requests, Double avgLatency, Long p95) {
    return new UsageSummaryDto.Stats(
        requests, 3000, 600, 500, 3600, new BigDecimal("0.045000"), 0.25, avgLatency, p95);
  }

  @Test
  void printsHeaderTotalsAndCostByModel() {
    String report =
        render(summary(stats(3, 812.5, 1900L)), "--client-id", "acme", "--project", "chatbot",
            "--month", "2026-10");

    assertThat(report)
        .contains("AI model usage - 2026-10")
        .contains("client   acme")
        .contains("project  chatbot")
        .contains("period   2026-10-01T00:00:00Z .. 2026-11-01T00:00:00Z (UTC)")
        .contains("requests       3")
        .contains("input tokens   3,000 (500 cached)")
        .contains("total tokens   3,600")
        .contains("total cost     $0.0450")
        .contains("error rate     25.00%")
        .contains("avg latency    812.5 ms")
        .contains("p95 latency    1900 ms")
        .contains("Cost by model")
        .contains("openai/gpt-4o")
        .contains("$0.0400");
  }

  @Test
  void showsAllProjectsWhenNoProjectFilterWasGiven() {
    assertThat(render(summary(stats(3, 812.5, 1900L)), "--client-id", "acme"))
        .contains("project  (all)");
  }

  @Test
  void saysSoWhenNothingWasRecorded() {
    String report =
        render(new UsageSummaryDto(stats(0, null, null), List.of()), "--client-id", "acme");

    assertThat(report)
        .contains("no usage recorded for this client in this period")
        .doesNotContain("Cost by model");
  }

  @Test
  void rendersMissingLatenciesAsDash() {
    String report = render(summary(stats(3, null, null)), "--client-id", "acme");

    assertThat(report).contains("avg latency    - ms").contains("p95 latency    - ms");
  }
}
