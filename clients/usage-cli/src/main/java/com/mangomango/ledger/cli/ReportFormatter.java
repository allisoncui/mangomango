// Generated using Claude Code.

package com.mangomango.ledger.cli;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Renders a usage summary as plain text. */
public class ReportFormatter {

  private static final String ROW = "  %-28s %10s %12s %12s%n";

  private final PrintStream out;

  /**
   * Creates a formatter.
   *
   * @param out stream the report is written to
   */
  public ReportFormatter(PrintStream out) {
    this.out = out;
  }

  /**
   * Writes the report.
   *
   * @param options the command line the report answers
   * @param summary the service's response
   */
  public void print(CliOptions options, UsageSummaryDto summary) {
    print("AI model usage - %s%n", options.range().month());
    print("  client   %s%n", options.clientId());
    print("  project  %s%n", options.project() == null ? "(all)" : options.project());
    print("  period   %s .. %s (UTC)%n", options.range().start(), options.range().end());

    UsageSummaryDto.Stats totals = summary.totals();
    print("%nTotals%n");
    if (totals == null || totals.requestCount() == 0) {
      print("  no usage recorded for this client in this period%n");
      return;
    }
    print("  requests       %,d%n", totals.requestCount());
    print("  input tokens   %,d (%,d cached)%n", totals.inputTokens(), totals.cachedInputTokens());
    print("  output tokens  %,d%n", totals.outputTokens());
    print("  total tokens   %,d%n", totals.totalTokens());
    print("  total cost     $%s%n", money(totals.totalCost()));
    print("  error rate     %.2f%%%n", totals.errorRate() * 100);
    print("  avg latency    %s ms%n", latency(totals.averageLatencyMs()));
    print("  p95 latency    %s ms%n", totals.p95LatencyMs() == null ? "-" : totals.p95LatencyMs());

    print("%nCost by model%n");
    print(ROW, "MODEL", "REQUESTS", "TOKENS", "COST");
    for (UsageSummaryDto.ModelCost model : summary.costBreakdown()) {
      print(
          ROW,
          model.provider() + "/" + model.modelId(),
          String.format(Locale.US, "%,d", model.requestCount()),
          String.format(Locale.US, "%,d", model.totalTokens()),
          "$" + money(model.totalCost()));
    }
  }

  private void print(String format, Object... args) {
    out.printf(Locale.US, format, args);
  }

  private static String money(BigDecimal amount) {
    return amount == null ? "-" : amount.setScale(4, RoundingMode.HALF_UP).toPlainString();
  }

  private static String latency(Double value) {
    return value == null ? "-" : String.format(Locale.US, "%.1f", value);
  }
}
