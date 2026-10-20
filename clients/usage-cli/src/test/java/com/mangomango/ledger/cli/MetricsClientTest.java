package com.mangomango.ledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsClientTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);

  private static final String SUMMARY_JSON =
      """
      {
        "totals": {
          "requestCount": 3, "inputTokens": 3000, "outputTokens": 600,
          "cachedInputTokens": 500, "totalTokens": 3600, "totalCost": 0.045000,
          "errorRate": 0.3333333333333333, "averageLatencyMs": 812.5, "p95LatencyMs": 1900
        },
        "costBreakdown": [
          {"provider": "openai", "modelId": "gpt-4o",
           "requestCount": 2, "totalTokens": 2400, "totalCost": 0.040000}
        ],
        "timeSeries": [{"bucketStart": "2026-10-01T00:00:00Z", "requestCount": 3}],
        "fieldAddedLater": "ignored"
      }
      """;

  private StubLedger ledger;
  private MetricsClient client;

  @BeforeEach
  void startLedger() throws IOException {
    ledger = new StubLedger();
    client = new MetricsClient();
  }

  @AfterEach
  void stopLedger() {
    ledger.stop();
  }

  private CliOptions options(String... args) {
    String[] withBaseUrl = new String[args.length + 2];
    System.arraycopy(args, 0, withBaseUrl, 0, args.length);
    withBaseUrl[args.length] = "--base-url";
    withBaseUrl[args.length + 1] = ledger.baseUrl();
    return CliOptions.parse(withBaseUrl, CLOCK);
  }

  @Test
  void callsTheMetricsOperationWithTheClientHeaderAndMonth() {
    ledger.respondWith(200, SUMMARY_JSON);

    client.fetch(options("--client-id", "acme", "--month", "2026-10", "--project", "chatbot"));

    assertThat(ledger.lastPath()).isEqualTo(MetricsClient.PATH);
    assertThat(ledger.lastClientId()).isEqualTo("acme");
    assertThat(ledger.lastQuery())
        .contains("start=2026-10-01T00%3A00%3A00Z")
        .contains("end=2026-11-01T00%3A00%3A00Z")
        .contains("project=chatbot");
  }

  @Test
  void leavesOutTheProjectFilterWhenNotAskedFor() {
    ledger.respondWith(200, SUMMARY_JSON);

    client.fetch(options("--client-id", "acme"));

    assertThat(ledger.lastQuery()).doesNotContain("project=");
  }

  @Test
  void parsesTheResponseAndIgnoresFieldsItDoesNotPrint() {
    ledger.respondWith(200, SUMMARY_JSON);

    UsageSummaryDto summary = client.fetch(options("--client-id", "acme"));

    assertThat(summary.totals().requestCount()).isEqualTo(3);
    assertThat(summary.totals().totalTokens()).isEqualTo(3600);
    assertThat(summary.totals().totalCost()).isEqualByComparingTo("0.045");
    assertThat(summary.totals().p95LatencyMs()).isEqualTo(1900L);
    assertThat(summary.costBreakdown()).hasSize(1);
    assertThat(summary.costBreakdown().get(0).modelId()).isEqualTo("gpt-4o");
  }

  @Test
  void treatsMissingBreakdownAsEmpty() {
    ledger.respondWith(200, "{}");

    assertThat(client.fetch(options("--client-id", "acme")).costBreakdown()).isEmpty();
  }

  @Test
  void surfacesTheServicesErrorMessage() {
    ledger.respondWith(400, "{\"error\":\"bad\",\"message\":\"end must be after start\"}");

    assertThatThrownBy(() -> client.fetch(options("--client-id", "acme")))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("HTTP 400")
        .hasMessageContaining("end must be after start");
  }

  @Test
  void fallsBackToTheRawBodyWhenAnErrorIsNotJson() {
    ledger.respondWith(503, "upstream unavailable");

    assertThatThrownBy(() -> client.fetch(options("--client-id", "acme")))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("HTTP 503")
        .hasMessageContaining("upstream unavailable");
  }

  @Test
  void reportsSuccessBodyItCannotRead() {
    ledger.respondWith(200, "this is not json");

    assertThatThrownBy(() -> client.fetch(options("--client-id", "acme")))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("Could not read the response");
  }

  @Test
  void reportsServiceItCannotReach() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    assertThatThrownBy(
            () ->
                client.fetch(
                    CliOptions.parse(
                        new String[] {
                          "--client-id", "acme", "--base-url", "http://127.0.0.1:" + closedPort
                        },
                        CLOCK)))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("Could not reach the ledger service");
  }

  @Test
  void rejectsBaseUrlWithoutHost() {
    assertThatThrownBy(
            () ->
                client.fetch(
                    CliOptions.parse(
                        new String[] {"--client-id", "acme", "--base-url", "localhost:8080"},
                        CLOCK)))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("--base-url must be an absolute http(s) URL");
  }
}
