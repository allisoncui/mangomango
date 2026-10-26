// Generated using Claude Code.

package com.mangomango.ledger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mangomango.ledger.budget.BudgetAlertEvent;
import com.mangomango.ledger.budget.WebhookNotifier;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Exercises every operation over real HTTP against an in-memory H2 database. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ledger.seed-catalog=false")
class LedgerApiTest {

  @LocalServerPort private int port;

  @MockitoBean private WebhookNotifier webhookNotifier;

  private String client;
  private String suffix;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    // Unique client and model names isolate tests that share one application context.
    suffix = UUID.randomUUID().toString().substring(0, 8);
    client = "client-" + suffix;
  }

  private static RequestSpecification as(String clientId) {
    return given().header("X-Client-Id", clientId).contentType(ContentType.JSON);
  }

  private String addModel(String provider, String name, String in, String out, int ctx, int tier,
      Instant effective) {
    String model = name + "-" + suffix;
    Map<String, Object> body = new HashMap<>();
    body.put("provider", provider);
    body.put("modelId", model);
    body.put("inputPricePerMillion", in);
    body.put("outputPricePerMillion", out);
    body.put("contextWindow", ctx);
    body.put("capabilityTier", tier);
    body.put("effectiveDate", effective.toString());
    as("admin").body(body).post("/api/v1/catalog").then().statusCode(201);
    return model;
  }

  private static Map<String, Object> usage(String provider, String model, long in, long out,
      Instant at, long latency, String status, String project) {
    Map<String, Object> body = new HashMap<>();
    body.put("provider", provider);
    body.put("modelId", model);
    body.put("inputTokens", in);
    body.put("outputTokens", out);
    body.put("idempotencyKey", UUID.randomUUID().toString());
    body.put("requestTimestamp", at.toString());
    body.put("latencyMs", latency);
    body.put("status", status);
    body.put("projectTag", project);
    return body;
  }

  private Instant recently() {
    return Instant.now().minus(1, ChronoUnit.MINUTES);
  }

  @Test
  void catalogKeepsPriceHistoryAndOnlyAdminsWrite() {
    Instant past = Instant.parse("2025-01-01T00:00:00Z");
    String model = addModel("openai", "gpt", "1.00", "2.00", 128_000, 3, past);
    addModel("openai", "gpt", "0.50", "1.00", 128_000, 3, Instant.parse("2026-01-01T00:00:00Z"));

    given().get("/api/v1/catalog/openai/{m}/history", model).then()
        .statusCode(200)
        .body("version", contains(1, 2));
    given().get("/api/v1/catalog/openai/{m}", model).then()
        .statusCode(200)
        .body("version", equalTo(2))
        .body("inputPricePerMillion", equalTo(0.5f));
    as("not-admin").body(Map.of("provider", "x", "modelId", "y", "inputPricePerMillion", 1,
            "outputPricePerMillion", 1, "contextWindow", 10, "capabilityTier", 1))
        .post("/api/v1/catalog").then()
        .statusCode(403);
  }

  @Test
  void logUsagePricesAtRequestTimeAndIsIdempotent() {
    String model = addModel("openai", "gpt", "1.00", "2.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));
    addModel("openai", "gpt", "10.00", "20.00", 128_000, 3,
        Instant.parse("2026-01-01T00:00:00Z"));

    Map<String, Object> oldCall = usage("OpenAI", model, 1_000_000, 500_000,
        Instant.parse("2025-06-01T00:00:00Z"), 300, "SUCCESS", null);
    String id = as(client).body(oldCall).post("/api/v1/usage").then()
        .statusCode(201)
        .body("cost", equalTo(2.0f))
        .body("pricingVersion", equalTo(1))
        .body("replayed", equalTo(false))
        .extract().path("recordId");

    as(client).body(oldCall).post("/api/v1/usage").then()
        .statusCode(200)
        .body("recordId", equalTo(id))
        .body("replayed", equalTo(true));

    as(client).body(usage("openai", model, 1_000_000, 0, recently(), 300, "SUCCESS", null))
        .post("/api/v1/usage").then()
        .statusCode(201)
        .body("cost", equalTo(10.0f))
        .body("pricingVersion", equalTo(2));
  }

  @Test
  void logUsageRejectsUnknownModelAndInvalidPayload() {
    as(client).body(usage("openai", "does-not-exist", 1, 1, recently(), 1, "SUCCESS", null))
        .post("/api/v1/usage").then()
        .statusCode(400)
        .body("message", containsString("not in the catalog"));

    as(client).body(Map.of("provider", "openai")).post("/api/v1/usage").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"))
        .body("details", notNullValue());

    given().contentType(ContentType.JSON)
        .body(usage("openai", "x", 1, 1, recently(), 1, "SUCCESS", null))
        .post("/api/v1/usage").then()
        .statusCode(400);
  }

  @Test
  void logUsageRejectsReusedKeyForDifferentCallButScopesKeysPerClient() {
    String model = addModel("openai", "gpt", "1.00", "2.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));
    Map<String, Object> call = usage("openai", model, 1_000, 100, recently(), 300, "SUCCESS", null);
    as(client).body(call).post("/api/v1/usage").then().statusCode(201);

    Map<String, Object> changed = new HashMap<>(call);
    changed.put("inputTokens", 2_000);
    as(client).body(changed).post("/api/v1/usage").then()
        .statusCode(409)
        .body("error", equalTo("conflict"))
        .body("message", containsString((String) call.get("idempotencyKey")));

    // Idempotency keys belong to one client, so another client may use the same key.
    as("other-" + suffix).body(changed).post("/api/v1/usage").then()
        .statusCode(201)
        .body("replayed", equalTo(false));
  }

  @Test
  void logUsageRejectsBlankClientAndInconsistentTokenCounts() {
    String model = addModel("openai", "gpt", "1.00", "2.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));

    as("   ").body(usage("openai", model, 1, 1, recently(), 1, "SUCCESS", null))
        .post("/api/v1/usage").then()
        .statusCode(400)
        .body("message", containsString("X-Client-Id"));

    Map<String, Object> tooManyCached = usage("openai", model, 100, 1, recently(), 1, "SUCCESS",
        null);
    tooManyCached.put("cachedInputTokens", 101);
    as(client).body(tooManyCached).post("/api/v1/usage").then()
        .statusCode(400)
        .body("message", containsString("cachedInputTokens"));

    as(client).body(usage("openai", model, 1, 1, Instant.now().plus(1, ChronoUnit.HOURS), 1,
            "SUCCESS", null))
        .post("/api/v1/usage").then()
        .statusCode(400)
        .body("message", containsString("future"));
  }

  @Test
  void budgetThresholdAlertsFireOnceAndRulesAreManageable() {
    String model = addModel("anthropic", "claude", "1.00", "1.00", 200_000, 4,
        Instant.parse("2025-01-01T00:00:00Z"));
    String ruleId = as(client).body(Map.of(
            "scope", "PROJECT", "scopeValue", "bot", "period", "MONTHLY",
            "limitAmount", 1.00, "thresholds", List.of(50, 100),
            "callbackUrl", "https://example.com/hook"))
        .post("/api/v1/budgets").then()
        .statusCode(201)
        .body("status", equalTo("created"))
        .body("currentSpend", equalTo(0.0f))
        .extract().path("ruleId");

    // $0.60 on project "bot" crosses 50%.
    as(client).body(usage("anthropic", model, 600_000, 0, recently(), 100, "SUCCESS", "bot"))
        .post("/api/v1/usage").then()
        .statusCode(201)
        .body("alertTriggered", equalTo(true))
        .body("budgetThresholdBreached", equalTo(true))
        .body("budgets[0].newlyCrossedThresholds", contains(50))
        .body("budgets[0].remaining", equalTo(0.4f));
    verify(webhookNotifier, timeout(2000).times(1)).send(any(BudgetAlertEvent.class));

    // Another $0.10 stays past 50% but crosses nothing new.
    as(client).body(usage("anthropic", model, 100_000, 0, recently(), 100, "SUCCESS", "bot"))
        .post("/api/v1/usage").then()
        .body("alertTriggered", equalTo(false))
        .body("budgets[0].newlyCrossedThresholds", empty());
    // A different project does not touch this rule.
    as(client).body(usage("anthropic", model, 900_000, 0, recently(), 100, "SUCCESS", "other"))
        .post("/api/v1/usage").then()
        .body("budgets", empty());
    verify(webhookNotifier, times(1)).send(any(BudgetAlertEvent.class));

    as(client).get("/api/v1/budgets/{id}", ruleId).then()
        .statusCode(200)
        .body("percentConsumed", equalTo(70.0f))
        .body("crossedThresholds", contains(50));
    as("someone-else").get("/api/v1/budgets/{id}", ruleId).then().statusCode(404);
    as(client).delete("/api/v1/budgets/{id}", ruleId).then().statusCode(204);
    as(client).get("/api/v1/budgets/{id}", ruleId).then().statusCode(404);
  }

  @Test
  void metricsAggregateAndGroupUsage() {
    String a = addModel("openai", "a", "1.00", "1.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));
    String b = addModel("google", "b", "2.00", "2.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));
    Instant at = Instant.parse("2026-02-10T10:15:00Z");
    as(client).body(usage("openai", a, 1000, 1000, at, 100, "SUCCESS", "p1"))
        .post("/api/v1/usage");
    as(client).body(usage("openai", a, 1000, 1000, at, 300, "ERROR", "p1"))
        .post("/api/v1/usage");
    as(client).body(usage("google", b, 1000, 0, at.plusSeconds(86_400), 50, "SUCCESS", "p2"))
        .post("/api/v1/usage");

    as(client)
        .queryParam("start", "2026-02-10T00:00:00Z")
        .queryParam("end", "2026-02-12T00:00:00Z")
        .queryParam("granularity", "DAILY")
        .queryParam("groupBy", "PROVIDER")
        .get("/api/v1/usage/metrics").then()
        .statusCode(200)
        .body("totals.requestCount", equalTo(3))
        .body("totals.totalTokens", equalTo(5000))
        .body("timeSeries", hasSize(2))
        .body("timeSeries[0].requestCount", equalTo(2))
        .body("groups.key", contains("google", "openai"))
        .body("groups[1].stats.errorRate", equalTo(0.5f))
        .body("groups[1].stats.averageLatencyMs", equalTo(200.0f))
        .body("groups[1].stats.p95LatencyMs", equalTo(300))
        .body("costBreakdown[0].provider", equalTo("openai"));

    as(client)
        .queryParam("start", "2026-02-10T00:00:00Z")
        .queryParam("end", "2026-02-12T00:00:00Z")
        .queryParam("project", "p2")
        .get("/api/v1/usage/metrics").then()
        .body("totals.requestCount", equalTo(1));
  }

  @Test
  void recommendationUsesCatalogAndHistory() {
    addModel("openai", "cheap", "0.10", "0.40", 128_000, 2,
        Instant.parse("2025-01-01T00:00:00Z"));
    addModel("google", "mid", "0.50", "1.00", 1_000_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));

    as(client).body(Map.of("estimatedPromptTokens", 2000, "minCapabilityTier", 2,
            "optimizeFor", "LOWEST_COST"))
        .post("/api/v1/recommendations").then()
        .statusCode(200)
        .body("recommended", notNullValue())
        .body("recommended.scoreBreakdown.cost", notNullValue())
        .body("weights.cost", notNullValue());
  }

  @Test
  void savingsRepricesUsageUnderAlternatives() {
    Instant past = Instant.parse("2025-01-01T00:00:00Z");
    String used = addModel("openai", "used", "4.00", "4.00", 1_000_000, 3, past);
    String cheaper = addModel("google", "cheaper", "1.00", "1.00", 1_000_000, 3, past);
    String weak = addModel("google", "weak", "0.10", "0.10", 1_000_000, 1, past);
    String small = addModel("anthropic", "small", "0.10", "0.10", 1_000, 5, past);
    Instant at = Instant.parse("2026-03-05T00:00:00Z");
    as(client).body(usage("openai", used, 500_000, 0, at, 100, "SUCCESS", null))
        .post("/api/v1/usage").then().statusCode(201);

    as(client).body(Map.of(
            "start", "2026-03-01T00:00:00Z",
            "end", "2026-04-01T00:00:00Z",
            "alternatives", List.of(
                Map.of("provider", "google", "modelId", cheaper),
                Map.of("provider", "google", "modelId", weak),
                Map.of("provider", "anthropic", "modelId", small),
                Map.of("provider", "openai", "modelId", used))))
        .post("/api/v1/savings/analysis").then()
        .statusCode(200)
        .body("actualCost", equalTo(2.0f))
        .body("highestCapabilityTierUsed", equalTo(3))
        .body("alternatives.modelId", contains(cheaper, used))
        .body("alternatives[0].savings", equalTo(1.5f))
        .body("alternatives[0].savingsPercent", equalTo(75.0f))
        .body("alternatives[1].currentlyUsed", equalTo(true))
        .body("alternatives[1].averageLatencyMs", equalTo(100.0f))
        .body("excluded.modelId", contains(weak, small));
  }

  @Test
  void catalogListsActiveVersionsAndRejectsUnknownOrInvalidModels() {
    String model = addModel("openai", "listed", "1.00", "2.00", 128_000, 3,
        Instant.parse("2025-01-01T00:00:00Z"));
    addModel("openai", "listed", "0.50", "1.00", 128_000, 3,
        Instant.parse("2026-01-01T00:00:00Z"));
    // A version that is not yet in effect must not replace the active one.
    addModel("openai", "listed", "9.00", "9.00", 128_000, 3,
        Instant.now().plus(30, ChronoUnit.DAYS));

    given().get("/api/v1/catalog").then()
        .statusCode(200)
        .body("findAll { it.modelId == '" + model + "' }.version", contains(2));

    given().get("/api/v1/catalog/openai/{m}", "missing-" + suffix).then()
        .statusCode(404)
        .body("error", equalTo("not_found"));
    given().get("/api/v1/catalog/openai/{m}/history", "missing-" + suffix).then()
        .statusCode(404)
        .body("error", equalTo("not_found"));

    as("admin").body(Map.of("provider", "openai", "modelId", "bad-" + suffix,
            "inputPricePerMillion", -1, "outputPricePerMillion", 1, "contextWindow", 10,
            "capabilityTier", 9))
        .post("/api/v1/catalog").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"))
        .body("details", notNullValue());
  }

  @Test
  void budgetRulesCanBeListedAndUpdatedOnlyByTheirOwner() {
    String ruleId = as(client).body(Map.of(
            "scope", "CLIENT", "period", "MONTHLY",
            "limitAmount", 10.00, "thresholds", List.of(80)))
        .post("/api/v1/budgets").then()
        .statusCode(201)
        .extract().path("ruleId");
    as(client).body(Map.of(
            "scope", "PROVIDER", "scopeValue", "openai", "period", "DAILY",
            "limitAmount", 1.00, "thresholds", List.of(50, 100)))
        .post("/api/v1/budgets").then()
        .statusCode(201);

    as(client).get("/api/v1/budgets").then()
        .statusCode(200)
        .body("$", hasSize(2))
        .body("status", contains("ok", "ok"));
    as("someone-else").get("/api/v1/budgets").then()
        .statusCode(200)
        .body("$", empty());

    Map<String, Object> replacement = Map.of(
        "scope", "PROJECT", "scopeValue", "bot", "period", "WEEKLY",
        "limitAmount", 2.50, "thresholds", List.of(25, 75));
    as(client).body(replacement).put("/api/v1/budgets/{id}", ruleId).then()
        .statusCode(200)
        .body("status", equalTo("updated"))
        .body("ruleId", equalTo(ruleId))
        .body("scope", equalTo("PROJECT"))
        .body("period", equalTo("WEEKLY"))
        .body("limitAmount", equalTo(2.5f))
        .body("thresholds", contains(25, 75));
    as(client).get("/api/v1/budgets/{id}", ruleId).then()
        .body("scopeValue", equalTo("bot"));

    as("someone-else").body(replacement).put("/api/v1/budgets/{id}", ruleId).then()
        .statusCode(404);
    as(client).body(replacement).put("/api/v1/budgets/{id}", UUID.randomUUID()).then()
        .statusCode(404);
    as(client).get("/api/v1/budgets/{id}", "not-a-uuid").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
  }

  @Test
  void budgetRulesRejectInvalidScopesThresholdsAndCallbacks() {
    as(client).body(Map.of("scope", "CLIENT", "scopeValue", "bot", "period", "MONTHLY",
            "limitAmount", 1.00, "thresholds", List.of(50)))
        .post("/api/v1/budgets").then()
        .statusCode(400)
        .body("message", containsString("scopeValue"));
    as(client).body(Map.of("scope", "PROJECT", "period", "MONTHLY",
            "limitAmount", 1.00, "thresholds", List.of(50)))
        .post("/api/v1/budgets").then()
        .statusCode(400)
        .body("message", containsString("scopeValue"));
    as(client).body(Map.of("scope", "CLIENT", "period", "MONTHLY",
            "limitAmount", 1.00, "thresholds", List.of(50), "callbackUrl", "ftp://example.com"))
        .post("/api/v1/budgets").then()
        .statusCode(400)
        .body("message", containsString("callbackUrl"));
    as(client).body(Map.of("scope", "CLIENT", "period", "MONTHLY",
            "limitAmount", 0, "thresholds", List.of()))
        .post("/api/v1/budgets").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"));
    as(client).body(Map.of("scope", "TEAM", "period", "MONTHLY",
            "limitAmount", 1.00, "thresholds", List.of(50)))
        .post("/api/v1/budgets").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
  }

  @Test
  void metricsRejectInvalidRangesAndParameters() {
    as(client)
        .queryParam("start", "2026-02-12T00:00:00Z")
        .queryParam("end", "2026-02-10T00:00:00Z")
        .get("/api/v1/usage/metrics").then()
        .statusCode(400)
        .body("message", containsString("start must be before end"));
    as(client)
        .queryParam("end", "2026-02-10T00:00:00Z")
        .get("/api/v1/usage/metrics").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
    as(client)
        .queryParam("start", "2026-02-10T00:00:00Z")
        .queryParam("end", "2026-02-12T00:00:00Z")
        .queryParam("granularity", "YEARLY")
        .get("/api/v1/usage/metrics").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
    given()
        .queryParam("start", "2026-02-10T00:00:00Z")
        .queryParam("end", "2026-02-12T00:00:00Z")
        .get("/api/v1/usage/metrics").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
  }

  @Test
  void recommendationRejectsInvalidRequests() {
    as(client).body(Map.of("taskComplexity", "MEDIUM"))
        .post("/api/v1/recommendations").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"));
    as(client).body(Map.of("estimatedPromptTokens", 1000, "minCapabilityTier", 9))
        .post("/api/v1/recommendations").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"));
    as(client).body(Map.of("estimatedPromptTokens", 1000, "optimizeFor", "FASTEST_EVER"))
        .post("/api/v1/recommendations").then()
        .statusCode(400)
        .body("error", equalTo("malformed_request"));
  }

  @Test
  void savingsRejectInvalidRanges() {
    as(client).body(Map.of("start", "2026-04-01T00:00:00Z", "end", "2026-03-01T00:00:00Z"))
        .post("/api/v1/savings/analysis").then()
        .statusCode(400)
        .body("message", containsString("start must be before end"));
    as(client).body(Map.of("start", "2026-03-01T00:00:00Z"))
        .post("/api/v1/savings/analysis").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"));
    as(client).body(Map.of("start", "2026-03-01T00:00:00Z", "end", "2026-04-01T00:00:00Z",
            "alternatives", List.of(Map.of("provider", "google"))))
        .post("/api/v1/savings/analysis").then()
        .statusCode(400)
        .body("error", equalTo("validation_error"));
  }
}
