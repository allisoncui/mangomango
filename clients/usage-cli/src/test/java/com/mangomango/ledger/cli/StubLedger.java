// Generated using Claude Code.

package com.mangomango.ledger.cli;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** A stand-in for the ledger service that records the request it was sent. */
final class StubLedger {

  private final HttpServer server;

  private int status = 200;
  private String body = "{}";
  private String lastPath;
  private String lastQuery;
  private String lastClientId;

  StubLedger() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastPath = exchange.getRequestURI().getPath();
          lastQuery = exchange.getRequestURI().getRawQuery();
          lastClientId = exchange.getRequestHeaders().getFirst("X-Client-Id");
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
  }

  void respondWith(int newStatus, String newBody) {
    this.status = newStatus;
    this.body = newBody;
  }

  String baseUrl() {
    return "http://"
        + InetAddress.getLoopbackAddress().getHostAddress()
        + ":"
        + server.getAddress().getPort();
  }

  String lastPath() {
    return lastPath;
  }

  String lastQuery() {
    return lastQuery;
  }

  String lastClientId() {
    return lastClientId;
  }

  void stop() {
    server.stop(0);
  }
}
