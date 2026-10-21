package com.mangomango.ledger.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Reads usage metrics from the ledger service. One GET; it never writes to the service. */
public class MetricsClient {

  /** Path of the service's usage-metrics operation. */
  public static final String PATH = "/api/v1/usage/metrics";

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  /**
   * Fetches the metrics described by {@code options}.
   *
   * @param options parsed command line
   * @return the service's response
   * @throws CliException when the service cannot be reached, refuses the request, or returns a
   *     body this client cannot read
   */
  public UsageSummaryDto fetch(CliOptions options) {
    URI uri = uri(options);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("X-Client-Id", options.clientId())
            .timeout(TIMEOUT)
            .GET()
            .build();

    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      throw new CliException(
          "Could not reach the ledger service at " + options.baseUrl() + ": " + e.getMessage());
    }

    if (response.statusCode() / 100 != 2) {
      throw new CliException(
          "The ledger service returned HTTP "
              + response.statusCode()
              + ": "
              + errorMessage(response.body()));
    }
    try {
      return mapper.readValue(response.body(), UsageSummaryDto.class);
    } catch (IOException e) {
      throw new CliException("Could not read the response from " + uri + ": " + e.getMessage());
    }
  }

  private static URI uri(CliOptions options) {
    StringBuilder query =
        new StringBuilder("?start=")
            .append(encode(options.range().start().toString()))
            .append("&end=")
            .append(encode(options.range().end().toString()));
    if (options.project() != null) {
      query.append("&project=").append(encode(options.project()));
    }
    try {
      URI uri = new URI(options.baseUrl() + PATH + query);
      if (uri.getScheme() == null || uri.getHost() == null) {
        throw new CliException("--base-url must be an absolute http(s) URL: " + options.baseUrl());
      }
      return uri;
    } catch (URISyntaxException e) {
      throw new CliException("--base-url is not a valid URL: " + options.baseUrl());
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /** Returns the service's error message, or the raw body when it is not a JSON error. */
  private String errorMessage(String body) {
    if (body == null || body.isBlank()) {
      return "(no response body)";
    }
    try {
      JsonNode message = mapper.readTree(body).path("message");
      return message.isTextual() ? message.asText() : body.strip();
    } catch (IOException e) {
      return body.strip();
    }
  }
}
