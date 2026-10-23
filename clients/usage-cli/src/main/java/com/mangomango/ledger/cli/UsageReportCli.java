// Generated using Claude Code.

package com.mangomango.ledger.cli;

import java.io.PrintStream;
import java.time.Clock;

/**
 * Client application 1: a terminal usage reporter.
 *
 * <p>Fetches one month of recorded usage for a client from the ledger service and prints a
 * plain-text summary. Read-only: one GET, nothing written back.
 */
public final class UsageReportCli {

  private final PrintStream out;
  private final PrintStream err;
  private final MetricsClient client;
  private final Clock clock;

  /**
   * Creates the application.
   *
   * @param out stream the report is written to
   * @param err stream errors are written to
   * @param client source of usage metrics
   * @param clock source of the current time, used when {@code --month} is omitted
   */
  public UsageReportCli(PrintStream out, PrintStream err, MetricsClient client, Clock clock) {
    this.out = out;
    this.err = err;
    this.client = client;
    this.clock = clock;
  }

  /**
   * Runs the reporter.
   *
   * @param args raw command-line arguments
   * @return 0 when the report was printed, 1 on any error
   */
  public int run(String[] args) {
    if (CliOptions.wantsHelp(args)) {
      out.println(CliOptions.usageText());
      return 0;
    }
    try {
      CliOptions options = CliOptions.parse(args, clock);
      new ReportFormatter(out).print(options, client.fetch(options));
      return 0;
    } catch (CliException e) {
      err.println("error: " + e.getMessage());
      return 1;
    }
  }

  /**
   * Command-line entry point.
   *
   * @param args raw command-line arguments
   */
  public static void main(String[] args) {
    UsageReportCli cli =
        new UsageReportCli(System.out, System.err, new MetricsClient(), Clock.systemUTC());
    System.exit(cli.run(args));
  }
}
