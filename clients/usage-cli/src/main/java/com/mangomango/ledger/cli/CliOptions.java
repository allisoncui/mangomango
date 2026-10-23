// Generated using Claude Code.

package com.mangomango.ledger.cli;

import java.time.Clock;
import java.util.List;

/**
 * Parsed command line for the usage reporter.
 *
 * @param baseUrl root URL of the ledger service, without a trailing slash
 * @param clientId value sent in the {@code X-Client-Id} header
 * @param project optional project tag filter
 * @param range the UTC month to report on
 */
public record CliOptions(String baseUrl, String clientId, String project, MonthRange range) {

  private static final String DEFAULT_BASE_URL = "http://localhost:8080";

  /**
   * Returns true when {@code args} asks for the help text.
   *
   * @param args raw command-line arguments
   * @return true when help was requested
   */
  public static boolean wantsHelp(String[] args) {
    return List.of(args).contains("--help") || List.of(args).contains("-h");
  }

  /**
   * Parses the command line.
   *
   * @param args raw command-line arguments
   * @param clock source of the current time, used when {@code --month} is omitted
   * @return the parsed options
   * @throws CliException when an argument is missing, unknown or malformed
   */
  public static CliOptions parse(String[] args, Clock clock) {
    String baseUrl = DEFAULT_BASE_URL;
    String clientId = null;
    String project = null;
    String month = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--base-url" -> baseUrl = value(args, i);
        case "--client-id" -> clientId = value(args, i);
        case "--project" -> project = value(args, i);
        case "--month" -> month = value(args, i);
        default -> throw new CliException("Unknown argument: " + args[i]);
      }
      i++;
    }

    if (clientId == null || clientId.isBlank()) {
      throw new CliException("--client-id is required");
    }
    return new CliOptions(
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl,
        clientId,
        project,
        month == null ? MonthRange.current(clock) : MonthRange.parse(month));
  }

  /** Returns the help text, without a trailing newline. */
  public static String usageText() {
    return """
        Usage: usage-cli --client-id <id> [options]

        Prints one month of AI model usage recorded for a client by the
        mangomango ledger service: tokens, cost, and cost per model.

        Required:
          --client-id <id>     client whose usage is reported

        Options:
          --month <YYYY-MM>    month to report, UTC (default: current month)
          --project <tag>      only count calls tagged with this project
          --base-url <url>     service root (default: http://localhost:8080)
          -h, --help           show this message

        Exits 0 on success, 1 on any error.""";
  }

  private static String value(String[] args, int index) {
    if (index + 1 >= args.length || args[index + 1].isBlank()) {
      throw new CliException(args[index] + " needs a value");
    }
    return args[index + 1];
  }
}
