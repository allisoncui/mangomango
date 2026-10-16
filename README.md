# mangomango
Advanced SWE Fall 2026

A provider-neutral ledger for AI model usage. Client programs report each AI call (provider, model,
token counts, latency, status); the service prices it with the catalog price in effect at the
request's timestamp, stores it, enforces budgets across providers, and serves metrics, model
recommendations and cost-savings analyses computed from its own stored data. It never calls AI
providers itself.

## Running

Requires Java 21 and Maven.

```bash
mvn verify                      # Checkstyle, unit + API tests, JaCoCo report, PMD
mvn spring-boot:run             # starts on :8080 with in-memory H2 and a sample catalog
```

To use PostgreSQL instead of H2:

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The JaCoCo report is written to `target/site/jacoco/index.html`.

## API

Every request identifies the calling program with an `X-Client-Id` header. Client IDs listed in
`ledger.admin-clients` (default: `admin`) may write to the catalog. Timestamps are ISO-8601 UTC;
money is US dollars.

| # | Operation | Endpoint |
|---|-----------|----------|
| 1 | Log Token Usage | `POST /api/v1/usage` |
| 2 | Usage Metrics & Aggregations | `GET /api/v1/usage/metrics` |
| 3 | Routing Recommendation | `POST /api/v1/recommendations` |
| 4 | Budget & Alert Rules | `POST/GET /api/v1/budgets`, `GET/PUT/DELETE /api/v1/budgets/{ruleId}` |
| 5 | Model Catalog & Pricing | `POST/GET /api/v1/catalog`, `GET /api/v1/catalog/{provider}/{model}[/history]` |
| 6 | Cost Savings Analysis | `POST /api/v1/savings/analysis` |

### 1. Log Token Usage

```bash
curl -X POST localhost:8080/api/v1/usage -H 'X-Client-Id: demo' -H 'Content-Type: application/json' -d '{
  "provider": "openai", "modelId": "gpt-4o",
  "inputTokens": 10000, "outputTokens": 2000, "cachedInputTokens": 4000,
  "idempotencyKey": "req-1", "requestTimestamp": "2026-10-15T12:00:00Z",
  "latencyMs": 850, "status": "SUCCESS", "projectTag": "support-bot"}'
```

`inputTokens` includes `cachedInputTokens`. Returns `201` with `recordId`, `cost`,
`pricingVersion`, the status of every applicable budget (`remaining`, `percentUsed`,
`crossedThresholds`, `newlyCrossedThresholds`), `budgetThresholdBreached` and `alertTriggered`.
Re-sending the same `idempotencyKey` returns `200` with the original record and `replayed: true`.
An unknown model or invalid payload returns `400`.

### 2. Usage Metrics

```
GET /api/v1/usage/metrics?start=2026-10-01T00:00:00Z&end=2026-11-01T00:00:00Z
    &granularity=DAILY&groupBy=MODEL&provider=openai&project=support-bot
```

`granularity`: `HOURLY` | `DAILY` (default) | `MONTHLY`. `groupBy`: `PROVIDER` | `MODEL` | `PROJECT`.
Returns totals, cost per provider/model, a time series (empty buckets included), and per-group
request count, error rate, average and p95 latency.

### 3. Routing Recommendation

```json
{ "estimatedPromptTokens": 3000, "expectedOutputTokens": 500, "minContextWindow": 32000,
  "taskComplexity": "MEDIUM", "maxLatencyMs": 2000, "optimizeFor": "LOWEST_COST" }
```

Models are filtered by context window (at least prompt + output tokens), capability tier
(`taskComplexity` LOW/MEDIUM/HIGH maps to tier 1/3/4, or pass `minCapabilityTier`) and the
client's recorded average latency over the last 30 days. Survivors are scored on cost, latency,
capability and reliability (1 − error rate). Models with no recorded history get a neutral
0.5 for latency and reliability. When any of the client's budgets is past an alert threshold,
cost weight increases. Returns the recommended model, a fallback from a different provider, the
weights, every option's score breakdown, and excluded models with reasons.

### 4. Budget Rules

```json
{ "scope": "PROJECT", "scopeValue": "support-bot", "period": "MONTHLY",
  "limitAmount": 50.00, "thresholds": [50, 80, 100], "callbackUrl": "https://example.com/hook" }
```

`scope`: `CLIENT` (no `scopeValue`) | `PROJECT` | `PROVIDER`. `period`: `DAILY` | `WEEKLY` |
`MONTHLY` (UTC calendar periods). Responses include `currentSpend`, `percentConsumed` and
`crossedThresholds`. When a logged call crosses a threshold for the first time in a period, the
service POSTs a JSON alert to `callbackUrl` after the record is committed.

### 5. Model Catalog

```json
{ "provider": "openai", "modelId": "gpt-4o", "inputPricePerMillion": 2.50,
  "outputPricePerMillion": 10.00, "cachedInputPricePerMillion": 1.25,
  "contextWindow": 128000, "capabilityTier": 4, "effectiveDate": "2026-11-01T00:00:00Z" }
```

Each write adds a new version and never overwrites earlier prices. Admin clients only (`403`
otherwise); any client can read. The seeded sample catalog uses illustrative prices.

### 6. Cost Savings

```json
{ "start": "2026-10-01T00:00:00Z", "end": "2026-11-01T00:00:00Z", "projectTag": "support-bot",
  "alternatives": [ { "provider": "google", "modelId": "gemini-2.0-flash" } ] }
```

Omit `alternatives` to compare every catalog model. Re-prices the recorded token volumes under
each alternative using the price in effect at each call's timestamp. Excludes alternatives whose
context window is below the largest recorded call or whose tier is below the highest tier used.
Returns actual cost, projected cost, savings ($ and %), recorded average latency, and exclusions.

## Project layout

```
src/main/java/com/mangomango/ledger/
  catalog/         Op 5 + CostCalculator (shared pricing model)
  usage/           Op 1
  metrics/         Op 2 + UsageStats (shared aggregation, p95)
  recommendation/  Op 3
  budget/          Op 4 + threshold alerts and webhooks
  savings/         Op 6
  common/          config, errors, client header
```
