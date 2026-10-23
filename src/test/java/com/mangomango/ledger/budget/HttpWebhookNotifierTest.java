// Generated using Claude Code.

package com.mangomango.ledger.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mangomango.ledger.common.AppConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Delivers webhooks to a real local HTTP server rather than a mock. */
class HttpWebhookNotifierTest {

  private HttpServer server;
  private final AtomicReference<String> contentLength = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/hook", exchange -> {
      contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private static BudgetAlertEvent alert(String url) {
    return new BudgetAlertEvent("c1", UUID.randomUUID(), 80, new BigDecimal("85.00"),
        new BigDecimal("8.5"), BigDecimal.TEN, Instant.parse("2026-10-01T00:00:00Z"), url);
  }

  private static HttpWebhookNotifier newNotifier() {
    return new HttpWebhookNotifier(new AppConfig().restClient(RestClient.builder()));
  }

  @Test
  void postsJsonWithContentLength() {
    HttpWebhookNotifier notifier = newNotifier();

    notifier.send(alert("http://127.0.0.1:" + server.getAddress().getPort() + "/hook"));

    assertThat(contentLength.get()).isNotNull();
    assertThat(body.get()).contains("\"thresholdPercent\":80").contains("\"clientId\":\"c1\"");
  }

  @Test
  void unreachableUrlDoesNotThrow() {
    HttpWebhookNotifier notifier = newNotifier();

    assertThatCode(() -> notifier.send(alert("http://127.0.0.1:1/hook")))
        .doesNotThrowAnyException();
  }
}
