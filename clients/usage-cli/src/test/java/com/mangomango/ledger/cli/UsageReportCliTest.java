// Generated using Claude Code.

package com.mangomango.ledger.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageReportCliTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);

  private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

  /** A metrics client that answers from memory and records what it was asked for. */
  private static final class FakeMetricsClient extends MetricsClient {

    private final CliException failure;
    private CliOptions received;

    private FakeMetricsClient(CliException failure) {
      this.failure = failure;
    }

    @Override
    public UsageSummaryDto fetch(CliOptions options) {
      this.received = options;
      if (failure != null) {
        throw failure;
      }
      return new UsageSummaryDto(
          new UsageSummaryDto.Stats(
              3, 3000, 600, 500, 3600, new BigDecimal("0.045000"), 0.25, 812.5, 1900L),
          List.of());
    }
  }

  private int run(FakeMetricsClient client, String... args) {
    return new UsageReportCli(out, err, client, CLOCK).run(args);
  }

  private String stdout() {
    out.flush();
    return outBuffer.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    err.flush();
    return errBuffer.toString(StandardCharsets.UTF_8);
  }

  @Test
  void printsTheReportAndSucceeds() {
    FakeMetricsClient client = new FakeMetricsClient(null);

    int status = run(client, "--client-id", "acme", "--month", "2026-10", "--project", "chatbot");

    assertThat(status).isZero();
    assertThat(stdout()).contains("AI model usage - 2026-10").contains("Totals");
    assertThat(stderr()).isEmpty();
    assertThat(client.received.clientId()).isEqualTo("acme");
    assertThat(client.received.project()).isEqualTo("chatbot");
  }

  @Test
  void defaultsToTheCurrentMonth() {
    FakeMetricsClient client = new FakeMetricsClient(null);

    run(client, "--client-id", "acme");

    assertThat(client.received.range().month()).isEqualTo(YearMonth.of(2026, 10));
  }

  @Test
  void helpGoesToStdoutAndSucceeds() {
    int status = run(new FakeMetricsClient(null), "--help");

    assertThat(status).isZero();
    assertThat(stdout()).contains("Usage: usage-cli --client-id");
  }

  @Test
  void badArgumentsFail() {
    int status = run(new FakeMetricsClient(null), "--client-id", "acme", "--group-by", "MODEL");

    assertThat(status).isEqualTo(1);
    assertThat(stderr()).contains("error: Unknown argument: --group-by");
    assertThat(stdout()).isEmpty();
  }

  @Test
  void serviceFailureFails() {
    FakeMetricsClient client =
        new FakeMetricsClient(new CliException("Could not reach the ledger service"));

    int status = run(client, "--client-id", "acme");

    assertThat(status).isEqualTo(1);
    assertThat(stderr()).contains("error: Could not reach the ledger service");
  }
}
