# mangomango
Advanced SWE Fall 2026

A provider-neutral ledger for AI model usage. Client programs report each AI call (provider, model,
token counts, latency, status); the service prices it with the catalog price in effect at the
request's timestamp, stores it, enforces budgets across providers, and serves metrics, model
recommendations and cost-savings analyses computed from its own stored data. It never calls AI
providers itself.

## Build, run and test

Requires Java 21 and Maven. All build, run and test configuration is in the repository:
[`pom.xml`](pom.xml), [`src/main/resources/application.yml`](src/main/resources/application.yml),
[`docker-compose.yml`](docker-compose.yml) and [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

```bash
mvn verify                      # Checkstyle, unit + API tests, JaCoCo report, PMD
mvn test                        # unit + API tests only
mvn test -Dtest=UsageServiceTest   # one test class
mvn spring-boot:run             # starts on :8080 with in-memory H2 and a sample catalog
```

To use PostgreSQL instead of H2:

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Testing

The whole suite runs push-button through **Maven Surefire** (3.5.4, inherited from the
`spring-boot-starter-parent` in [`pom.xml`](pom.xml)): `mvn test` runs all 93 unit and API tests in
one command, and `mvn test -Dtest=<ClassName>` runs a single class.

| What | Tool | Where |
|---|---|---|
| Test runner | Maven Surefire, via `mvn test` | Configured by the Spring Boot parent in [`pom.xml`](pom.xml); results in `target/surefire-reports/` |
| Unit tests | JUnit 5 (5.12.2), AssertJ | `src/test/java/com/mangomango/ledger/<package>/*Test.java`; related cases are grouped into `@Nested` classes (e.g. `UsageServiceTest.Validation`, `.NewReport`, `.Replay`, `.BudgetFlags`) that share `@BeforeEach` setup |
| Mocking | Mockito (`@Mock`, Spring `@MockitoBean`) | Repositories, `CatalogService`, `BudgetService` and the webhook sender are mocked; clocks are fixed with `Clock.fixed` |
| API tests | REST Assured against the running app on a random port with H2 | [`LedgerApiTest.java`](src/test/java/com/mangomango/ledger/LedgerApiTest.java) |
| Coverage | JaCoCo | Report at `target/site/jacoco/index.html` after `mvn verify`; committed copy in [`reports/jacoco/`](reports/jacoco/index.html) |

All test dependencies and plugins are configured in [`pom.xml`](pom.xml). The CLI client has its
own tests; see [`clients/usage-cli`](clients/usage-cli).

### Style checking and static analysis

- **Checkstyle** with the **Google Java Style** ruleset (`google_checks.xml`, bundled with
  Checkstyle), configured in [`pom.xml`](pom.xml). It checks main and test code, and any warning
  fails the build.
- **PMD** with its default Maven ruleset, run during `mvn verify`.
- Both run in CI on every pull request.

### Reports for the `t2-iteration-1` release

Generated from the tagged commit's source with `mvn verify checkstyle:checkstyle` (93 tests, 0 failures):

- **Checkstyle** (Google Java Style, Checkstyle 10.26.1, main + test code): **clean, 0 violations**.
  Report: [`reports/checkstyle-result.xml`](reports/checkstyle-result.xml).
- **Branch coverage (JaCoCo): 86.1%** (217 of 252 branches); 854 of 884 lines (96.6%).
  Report: [`reports/jacoco/index.html`](reports/jacoco/index.html) (also `jacoco.csv` / `jacoco.xml`).

## API

The full specification (field types and ranges, every status code, side effects, call order and
test cases) is in [`SPEC.md`](SPEC.md). This section is a quick reference.

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
Re-sending the same `idempotencyKey` for the same call returns `200` with the original record and
`replayed: true`; reusing it for a different call (different model, token counts or timestamp)
returns `409`. Keys are scoped per client. An unknown model, invalid payload, blank `X-Client-Id`,
`cachedInputTokens` greater than `inputTokens`, or a timestamp more than 5 minutes in the future
returns `400`.

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
Calls on models missing from the catalog are left out of the analysis and listed in
`unknownModelsUsed`.

### Status codes

| Code | Meaning |
|---|---|
| `200` | Success (reads, updates, idempotent replay) |
| `201` | Created (new usage record, budget rule or catalog version) |
| `204` | Budget rule deleted |
| `400` | `validation_error` (invalid field or business rule) or `malformed_request` (bad JSON, missing header or parameter, wrong type) |
| `403` | Non-admin client writing to the catalog |
| `404` | Model or budget rule not found for this client |
| `409` | Idempotency key reused for a different call, or concurrent duplicate reports |

Errors return `{"error": ..., "message": ..., "details": [...]}`. Per-endpoint codes are in
[`SPEC.md`](SPEC.md) section 5.

### Call order

1. A model must be in the catalog, with a price in effect at the call's timestamp, before usage for
   it can be logged.
2. Usage must be logged before metrics, savings or recommendation history reflect it.
3. A budget rule must exist before a usage report can trigger its alert. Alerts are evaluated only
   when usage is logged, not when a rule is created or updated.
4. Changing a catalog price never re-prices calls already logged.

## Usage scenarios

Start the service with `mvn spring-boot:run` (sample catalog loaded), then:

```bash
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
MONTH=$(date -u +%Y-%m-01T00:00:00Z)
END=$(date -u -v+1d +%Y-%m-%dT00:00:00Z)   # Linux: date -u -d tomorrow +%Y-%m-%dT00:00:00Z
api() { curl -s -H 'X-Client-Id: demo' -H 'Content-Type: application/json' "$@"; }
```

### Scenario 1: track spending against a budget

1. Create a $1 monthly budget with alerts at 50%, 80% and 100%:
   ```bash
   api -X POST localhost:8080/api/v1/budgets \
     -d '{"scope":"CLIENT","period":"MONTHLY","limitAmount":1.00,"thresholds":[50,80,100]}'
   ```
   `201`, `currentSpend: 0`.
2. Report a call (100k input and 20k output tokens on gpt-4o):
   ```bash
   api -X POST localhost:8080/api/v1/usage -d '{"provider":"openai","modelId":"gpt-4o",
     "inputTokens":100000,"outputTokens":20000,"idempotencyKey":"req-1",
     "requestTimestamp":"'$NOW'","latencyMs":850,"status":"SUCCESS","projectTag":"bot"}'
   ```
   `201`, `cost: 0.45`, budget at 45%, `alertTriggered: false`.
3. Report a second call with `"idempotencyKey":"req-2"` and the same body: `201`, budget at 90%,
   `newlyCrossedThresholds: [50, 80]`, `alertTriggered: true`.
4. Check the month's spending:
   ```bash
   api "localhost:8080/api/v1/usage/metrics?start=$MONTH&end=$END&groupBy=MODEL"
   ```
   `requestCount: 2`, `totalCost: 0.90`.

### Scenario 2: choose a cheaper model

1. Ask for the cheapest model for a medium-complexity task:
   ```bash
   api -X POST localhost:8080/api/v1/recommendations \
     -d '{"estimatedPromptTokens":3000,"taskComplexity":"MEDIUM","optimizeFor":"LOWEST_COST"}'
   ```
   Returns the recommended model, a fallback from another provider, and the tier-2 models excluded
   with reasons. After Scenario 1 the budget is past 50%, so `budgetPressure` is `true`.
2. See what this month's usage would have cost on other models:
   ```bash
   api -X POST localhost:8080/api/v1/savings/analysis -d '{"start":"'$MONTH'","end":"'$END'"}'
   ```

### Scenario 3: update a price (admin)

1. Add a new gpt-4o price effective now:
   ```bash
   curl -s -X POST localhost:8080/api/v1/catalog -H 'X-Client-Id: admin' -H 'Content-Type: application/json' \
     -d '{"provider":"openai","modelId":"gpt-4o","inputPricePerMillion":2.00,"outputPricePerMillion":8.00,
          "contextWindow":128000,"capabilityTier":4}'
   ```
   `201` with `stored.version: 2`.
2. `GET /api/v1/catalog/openai/gpt-4o/history` lists versions 1 and 2. Calls logged earlier keep
   their original cost; new calls are priced at version 2.

## Bad usage examples

Each of these is rejected or behaves differently than a careless client might expect.

| Attempt | Result | Why |
|---|---|---|
| Log usage for `"modelId":"gpt-5"` (not in the catalog) | `400` "not in the catalog" | Every call must be priced from the catalog; add the model first (call order 1). |
| Re-send `req-1` with different `inputTokens` | `409` | Silently replaying the original would drop the new call from the ledger. |
| Report a call with `requestTimestamp` an hour from now | `400` | Timestamps more than 5 minutes ahead are treated as client clock errors. |
| Report `cachedInputTokens: 500` with `inputTokens: 100` | `400` | Cached tokens are a part of input tokens and cannot exceed them. |
| Send `X-Client-Id: "  "`, or omit the header | `400` | Usage must belong to an identifiable client. |
| `POST /api/v1/catalog` as client `demo` | `403` | Only clients in `ledger.admin-clients` can change prices. |
| Create a `CLIENT` budget with `"scopeValue":"bot"`, or a `PROJECT` budget without one | `400` | `scopeValue` must be absent for client-wide rules and present for project and provider rules. |
| Create a budget with `"callbackUrl":"ftp://example.com"` | `400` | Webhooks are sent only to absolute `http(s)` URLs. |
| `GET /api/v1/budgets/{ruleId}` with another client's ID | `404` | Rules are private to the client that created them. |
| Metrics with `start` after `end` | `400` | The range would be empty. |
| Metrics with `end=$NOW` right after logging a call at `$NOW` | `200`, call missing | `end` is exclusive (`start <= t < end`); query up to a later time. |

## Client applications

Separate programs that use the service over HTTP. Each is built on its own.

| Client | Directory | What it does |
|---|---|---|
| Usage reporter (CLI) | [`clients/usage-cli`](clients/usage-cli) | Read-only. Prints a month of recorded tokens, cost and cost per model for a client, from operation 2. |
| Cost-controlled agent | *not built yet* | Calls operation 3 before each LLM call and operation 1 after, and downgrades or falls back when a budget is breached. |

```bash
cd clients/usage-cli && mvn verify
java -jar target/usage-cli.jar --client-id acme --month 2026-10 --project chatbot
```

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
clients/usage-cli/ terminal usage reporter (its own Maven project)
```

## Third-party code

This repository contains no third-party source code; all code was written for this project.
External libraries (e.g. Spring Boot, JUnit) are fetched by Maven at build time from the
dependencies declared in `pom.xml` and `clients/usage-cli/pom.xml`.

## Project management

Tasks and who completed them are tracked on our Trello board:
[mangomango](https://trello.com/invite/b/6adf900fd5c26d6acf0f3a22/ATTIe58929af2d5cdc0ac71d65c4c829023aA05699F5/mangomango),
with columns Backlog, In Progress, In Review and Done. Each card is assigned to its owner and links
to the pull request that completed it. Our mentor and instructor have been invited to the board.

## AI usage

For this project, we used Claude Code to assist with code generation and troubleshooting.

### How AI-generated code is marked

All 89 Java, XML and YAML files on the tagged release — main code, test code and build
configuration alike — carry a `Generated using Claude Code.` header comment at the top of the file:

- `//` on line 1 of all 84 Java sources and tests;
- `#` on line 1 of the three YAML files (`application.yml`, `docker-compose.yml`,
  `.github/workflows/ci.yml`);
- `<!-- -->` on line 2 of `pom.xml` and `clients/usage-cli/pom.xml`, directly below the XML
  declaration, which must come first.

The one unmarked configuration file is `.gitignore`: six lines of standard Java/IDE ignore
patterns.

The Markdown documentation (this README, [`SPEC.md`](SPEC.md) and
[`clients/usage-cli/README.md`](clients/usage-cli/README.md)) was also drafted with AI help, as the
prompts table below records, but carries no inline header. The contents of [`reports/`](reports) are
Checkstyle and JaCoCo tool output rather than written code.

### Token usage

On average, each person used approximately 43.3 million tokens with Claude Code (Claude Opus 5.5), including 42.2 million 
cached input tokens and 166,921 output tokens, with an estimated total of 216 million tokens across the five-person team.

### Prompts

For brevity, our use cases are summarized in the table below. While not every prompt and response
is documented here, these examples illustrate the types of assistance we received from the AI tool
throughout the development process, as well as the files affected by the changes.

| Task | Prompt | Summary of Response | File(s) Affected |
|---|---|---|---|
| Set up general Spring Boot project structure | "Set up a Spring Boot/Maven starter for a provider-neutral AI usage ledger that never calls providers itself. For the Log Token Usage demo, create application, controller, DTO, entity, repository and service skeletons, plus catalog/pricing and budget-rule skeletons, leaving room for metrics, recommendations and savings analysis. Use PostgreSQL for storage and H2 for tests. Structure and configuration only; business logic will come later." | Establishes a Spring Boot/Maven starter structure for the AI usage ledger and its initial usage-logging demo. | `pom.xml`; `LedgerApplication.java`; `usage/` (`UsageController`, `LogUsageRequest`/`LogUsageResponse`, `UsageEvent`, `UsageEventRepository`, `UsageService`); skeletons in `catalog/` and `budget/`; `src/main/resources/application.yml` |
| Set up persistent database code and model setup | "Connect the ledger to PostgreSQL with credentials from environment variables, keeping H2 as the default for local runs and tests. Define entities and repositories for usage records, budget rules and versioned catalog prices. Make client ID + idempotency key unique, keep price history by effective date instead of overwriting, and use decimals for money. Don't assume a particular cloud host." | Configures PostgreSQL and H2 profiles and establishes persistence for usage, budgets, and pricing history. | `application.yml` (H2 default, `postgres` profile); `docker-compose.yml`; `pom.xml`; `UsageEvent`/`UsageEventRepository`, `BudgetRule`/`BudgetRuleRepository`, `BudgetAlert`/`BudgetAlertRepository`, `ModelPrice`/`ModelPriceRepository`; `common/Money.java` |
| Set up parameter validation and unit testing for model layer | "Add validation to this class based on our proposal and write JUnit 5/AssertJ tests covering valid, missing, invalid and boundary values. Keep checks that need the database, like whether a model exists in the catalog, in the service layer and test them with Mockito. Point out validation rules the proposal leaves unspecified instead of inventing them." | Adds class-appropriate validation and unit tests for usage, pricing, or budget models/DTOs, distinguishing local validation from service checks. | `LogUsageRequest.java` + `UsageServiceTest.java`; `CatalogEntryRequest.java` + `CatalogServiceTest.java`, `CostCalculatorTest.java`; `BudgetRuleRequest.java` + `BudgetServiceTest.java`, `BudgetPeriodTest.java`; `LedgerApiTest.java`, as applicable |
| PR description | "Write a PR description for these changes: [diff]. Explain the problem, the resulting behavior, affected API operations and any persistence or configuration changes. List only tests actually run, note remaining limitations, and don't claim anything unverified." | Produces a PR description grounded in the supplied changes and actual validation results. | GitHub PR description (no repository files) |
| Fixing Checkstyle errors / updating Checkstyle configuration | "Fix these Checkstyle errors: [files and output]. We follow Google Java Style, enforced through Maven and GitHub Actions. Preserve behavior, and don't relax checks just to make the build pass. Run the Maven check and report the result." | Corrects style violations while maintaining the proposed Google Java Style and build enforcement. | Affected Java files; `pom.xml` (`maven-checkstyle-plugin` with `google_checks.xml`) and `.github/workflows/ci.yml` only if necessary |
| Updating README | "Update the project layout section of README.md to match the actual directory tree and briefly explain each part's role. Don't list proposed components as if they already exist." | Updates the documented directory structure to reflect the actual repository. | `README.md` |
| Add client README setup guide | "Write a setup and testing guide for the client we've actually built, based on its real code: prerequisites, starting the service and client, supported commands, how to verify output and common errors. Make clear that the client, not the ledger, calls AI providers. Include curl examples and use placeholders instead of real credentials." | Produces a setup and testing guide for the implemented demo client, consistent with its actual API usage. | `clients/usage-cli/README.md` |
