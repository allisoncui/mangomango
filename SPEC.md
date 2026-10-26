# mangomango Ledger: Service Specification

A provider-neutral ledger for AI model usage. This specification turns the team proposal
(Parts 2–4) into concrete types, ranges, rules, endpoint contracts and test cases. Where the
implementation refines or departs from the proposal, section 7 says how and why.

Section numbers are stable so commits, tests and reviews can refer to them (e.g. "R1.3", "5.4",
"T4.2").

## 1. Conventions

| Item | Rule |
|---|---|
| Base path | `/api/v1` |
| Client identity | Every request except catalog reads carries an `X-Client-Id` header. There is no authentication; the header is trusted. Data is always scoped to the calling client. |
| Administrators | Client IDs listed in `ledger.admin-clients` (default `admin`) may write to the catalog. |
| Timestamps | ISO-8601 instants in UTC, e.g. `2026-10-15T12:00:00Z`. |
| Time ranges | `start` is inclusive, `end` is exclusive: `start <= t < end`. `start` must be before `end`. |
| Money | US dollars, `BigDecimal`, stored and returned at 6 decimal places, rounded half-up. Percentages have 2 decimal places. |
| Prices | Dollars per million tokens. |
| Names | `provider` and `modelId` are trimmed and lower-cased before storage and lookup, so `" OpenAI "` equals `openai`. |
| Periods and buckets | Calendar periods and time-series buckets are aligned in UTC. Weeks start on Monday. |
| Errors | Every error response has the body `{"error": <code>, "message": <text>, "details": [<field problems>]}`. |

### 1.1 Error codes

| HTTP | `error` | When |
|---|---|---|
| 400 | `validation_error` | A body field fails its constraint (with one `details` entry per field), or a business rule is violated (section 3). |
| 400 | `malformed_request` | Unparseable JSON, missing `X-Client-Id` header, missing required query parameter, or a wrongly typed value (bad enum, timestamp or UUID). |
| 403 | `forbidden` | A non-administrator writes to the catalog. |
| 404 | `not_found` | The model or budget rule does not exist for this client. |
| 409 | `conflict` | An idempotency key is reused for a different call, or two concurrent reports with the same key collide. |

## 2. Data model

### 2.1 Usage event (one reported AI call)

| Field | Type | Range / rule |
|---|---|---|
| `recordId` | UUID | Assigned by the service. |
| `clientId` | string | From `X-Client-Id`, trimmed; not blank. |
| `idempotencyKey` | string | 1–200 chars; unique per client. |
| `provider`, `modelId` | string | Not blank; normalized; must be in the catalog with a price in effect at `requestTimestamp`. |
| `inputTokens` | long | ≥ 0; includes cached input tokens. |
| `outputTokens` | long | ≥ 0. |
| `cachedInputTokens` | long | Optional, default 0; 0 ≤ cached ≤ `inputTokens`. |
| `requestTimestamp` | instant | Required; no later than now + 5 minutes. |
| `latencyMs` | long | ≥ 0. |
| `status` | enum | `SUCCESS` or `ERROR`. |
| `projectTag` | string | Optional, ≤ 100 chars; trimmed; blank is stored as null. |
| `cost` | decimal | Computed (R1.1). |
| `pricingVersion` | int | Catalog version used for `cost`. |
| `recordedAt` | instant | Server time when stored. |

### 2.2 Catalog entry (one pricing version of a model)

| Field | Type | Range / rule |
|---|---|---|
| `entryId` | long | Assigned by the service. |
| `provider`, `modelId` | string | Not blank; normalized. |
| `version` | int | 1, 2, 3, … per model, assigned by the service. |
| `inputPricePerMillion` | decimal | ≥ 0, required. |
| `outputPricePerMillion` | decimal | ≥ 0, required. |
| `cachedInputPricePerMillion` | decimal | ≥ 0, optional. When absent, cached tokens are priced at the input price. |
| `contextWindow` | int | ≥ 1; maximum input plus output tokens per call. |
| `capabilityTier` | int | 1 (basic) to 5 (frontier). |
| `effectiveDate` | instant | Optional; defaults to now. May be in the future. |

### 2.3 Budget rule

| Field | Type | Range / rule |
|---|---|---|
| `ruleId` | UUID | Assigned by the service. |
| `scope` | enum | `CLIENT`, `PROJECT` or `PROVIDER`. |
| `scopeValue` | string | Must be absent for `CLIENT`; required for `PROJECT` (a project tag) and `PROVIDER` (a provider, normalized). Trimmed. |
| `period` | enum | `DAILY`, `WEEKLY` or `MONTHLY`. |
| `limitAmount` | decimal | > 0. |
| `thresholds` | list of int | Not empty; each 1–1000 (percent of limit). Stored de-duplicated and ascending. |
| `callbackUrl` | string | Optional; absolute `http` or `https` URL with a host. |

### 2.4 Budget alert

One record per (rule, period start, threshold), written the first time that threshold is crossed in
that period. Used only to make alerts fire once (R4.3).

## 3. Rules

### R1. Pricing and logging

- **R1.1 Cost.** `cost = (input − cached) × inputPrice + cached × cachedPrice + output × outputPrice`,
  each divided by 1,000,000, rounded to 6 decimals. `cachedPrice` falls back to `inputPrice`.
- **R1.2 Price in effect.** A call is priced with the version whose `effectiveDate` is the latest one
  ≤ `requestTimestamp`; ties go to the higher version. A model with no such version is rejected
  (400), with a message distinguishing "not in the catalog" from "no price in effect at <time>".
- **R1.3 Idempotency.** Keys are scoped per client. If `(clientId, idempotencyKey)` already exists:
  - if provider, model, input, output and cached tokens and `requestTimestamp` all match the stored
    record, the original record is returned with `replayed: true` and status 200, and nothing is
    stored or re-evaluated;
  - otherwise the request fails with 409. Latency, status and project tag are not compared.
- **R1.4 Clock skew.** `requestTimestamp` more than 5 minutes after the server clock is rejected (400).
- **R1.5 Cached tokens.** `cachedInputTokens > inputTokens` is rejected (400).
- **R1.6 Order.** Validation and pricing happen before anything is stored; a rejected call stores
  nothing and fires no alerts.

### R2. Metrics

- **R2.1 Filtering.** Only the caller's events with `start <= requestTimestamp < end` are counted,
  then filtered by `provider`, `model` (both normalized) and `project` (exact match) when given.
- **R2.2 Statistics.** For any set of events: request count; input, output, cached and total tokens
  (total = input + output); total cost; average cost and tokens per request; error rate =
  `ERROR` calls / all calls; average latency; p95 latency by nearest rank
  (`sorted[ceil(0.95 × n) − 1]`). Latency fields are null when there are no events.
- **R2.3 Time series.** One point per bucket (`HOURLY`, `DAILY` default, `MONTHLY`) from the bucket
  containing `start` up to `end`, including empty buckets. More than 5,000 buckets is rejected (400).
- **R2.4 Grouping.** With `groupBy` = `PROVIDER`, `MODEL` (key `provider/model`) or `PROJECT`
  (untagged events grouped as `(untagged)`), each group carries the R2.2 statistics, sorted by key.
- **R2.5 Cost breakdown.** Always returned: cost per provider/model, most expensive first.

### R3. Recommendation

- **R3.1 Defaults.** `expectedOutputTokens` defaults to 500; `optimizeFor` to `LOWEST_COST`.
- **R3.2 Required context** = max(`estimatedPromptTokens` + `expectedOutputTokens`,
  `minContextWindow`).
- **R3.3 Minimum tier** = max(tier from `taskComplexity` (`LOW` 1, `MEDIUM` 3, `HIGH` 4),
  `minCapabilityTier`), default 1.
- **R3.4 History.** The caller's events from the last 30 days (`ledger.recommendation-lookback-days`)
  are grouped per model to give average latency, error rate and sample size.
- **R3.5 Exclusion.** Each currently active catalog model is excluded, with a reason, if its context
  window < required context, its tier < minimum tier, or `maxLatencyMs` is set and its recorded
  average latency exceeds it. Models with no history are never excluded for latency.
- **R3.6 Scores (0–1).** Cost = cheapest estimated cost / this model's cost. Latency = fastest
  recorded average / this model's average (0.5 with no history). Capability = tier / 5.
  Reliability = 1 − error rate (0.5 with no history).
- **R3.7 Weights** (cost, latency, capability, reliability): `LOWEST_COST` 0.6/0.15/0.1/0.15,
  `LOWEST_LATENCY` 0.15/0.6/0.1/0.15, `HIGHEST_CAPABILITY` 0.15/0.1/0.6/0.15.
- **R3.8 Budget pressure.** If any of the caller's budget rules has a crossed threshold in its current
  period, 0.3 is added to the cost weight and the weights are renormalized to sum to 1.
- **R3.9 Ranking.** Total score = Σ weight × score, ranked descending, ties broken by lower cost. The
  first is `recommended`; `fallback` is the best-ranked model from a different provider, or null.

### R4. Budgets

- **R4.1 Spend.** A rule's spend is the sum of `cost` of the client's events in the current period
  (`[periodStart, periodEnd)`), restricted to the project tag or provider for those scopes.
- **R4.2 Status.** `percentUsed = spend / limit × 100`; `remaining = max(limit − spend, 0)`; a
  threshold is crossed when `percentUsed >= threshold`.
- **R4.3 Alerts fire once.** After each new usage event, every rule that applies to it is evaluated.
  Thresholds crossed for the first time in the current period are recorded (2.4) and returned as
  `newlyCrossedThresholds`. A new period starts with no recorded alerts.
- **R4.4 Webhook.** For each newly crossed threshold on a rule with a `callbackUrl`, the service
  POSTs `{clientId, ruleId, thresholdPercent, percentUsed, spent, limit, periodStart, callbackUrl}`
  as JSON, asynchronously and only after the usage record is committed. Delivery failures are
  logged and not retried.
- **R4.5 Rule changes** (create, update, get) report current spend and crossed thresholds but never
  fire alerts. Deleting a rule deletes its alert history.

### R5. Catalog

- **R5.1 Versioning.** Each write stores a new version (previous highest + 1) and never modifies or
  deletes earlier versions.
- **R5.2 Active version.** The active version of a model is the one in effect now (R1.2). A model
  whose only versions start in the future has no active version.

### R6. Savings

- **R6.1 Actual cost** = sum of recorded `cost` for the caller's events in `[start, end)`, optionally
  restricted to `projectTag`.
- **R6.2 Alternatives** are the requested models, or every currently active catalog model when the
  list is empty or absent.
- **R6.3 Exclusion.** An alternative is excluded, with a reason, if it is not in the catalog, its
  context window < the largest single call in the range (input + output tokens), or its tier < the
  highest tier among models actually used.
- **R6.4 Re-pricing.** Each recorded call's input, cached and output tokens are priced under the
  alternative using the alternative's price in effect at that call's timestamp. If the alternative
  had no price then, its current price is used and the call is counted in
  `requestsPricedAtCurrentRate`.
- **R6.5 Savings** = actual − projected (negative means the alternative is more expensive);
  `savingsPercent` = savings / actual × 100 (0 when actual is 0). Alternatives are sorted cheapest
  first. Each includes the caller's recorded average latency on that model in the range, if any.

## 4. Call order

1. A model must be in the catalog (5.5), with a price in effect at the call's timestamp, before usage
   for it can be logged (5.1).
2. Usage must be logged (5.1) before metrics (5.2), savings (5.6) or recommendation history (5.3)
   reflect it.
3. A budget rule (5.4) must exist before a usage report can trigger its alert. Alerts are evaluated
   only when usage is logged, not when a rule is created or updated.
4. Changing a catalog price never re-prices calls already logged; savings analysis is the way to see
   the effect of a different price.

## 5. Endpoints

### 5.1 Log Token Usage: `POST /api/v1/usage`

- **Headers:** `X-Client-Id` (required, not blank).
- **Body:** fields of 2.1 that the client supplies.
- **Success:** `201` for a new record, `200` for a replay (R1.3). Body: `recordId`, `replayed`,
  `cost`, `pricingVersion`, `budgets` (one status per applicable rule: `ruleId`, `scope`,
  `scopeValue`, `periodStart`, `periodEnd`, `limit`, `spent`, `remaining`, `percentUsed`,
  `crossedThresholds`, `newlyCrossedThresholds`), `budgetThresholdBreached` (any rule has a crossed
  threshold), `alertTriggered` (any rule has a newly crossed threshold).
- **Side effects:** stores one usage event; records alerts and sends webhooks (R4.3, R4.4). A replay
  has no side effects.
- **Errors:** 400 (invalid field, blank client ID, R1.2, R1.4, R1.5), 409 (R1.3, concurrent
  duplicate).

### 5.2 Usage Metrics: `GET /api/v1/usage/metrics`

- **Headers:** `X-Client-Id`.
- **Query:** `start`, `end` (required); `provider`, `model`, `project` (optional filters);
  `granularity` (`HOURLY` | `DAILY` | `MONTHLY`, default `DAILY`); `groupBy` (`PROVIDER` | `MODEL` |
  `PROJECT`, optional).
- **Success:** `200` with `start`, `end`, `granularity`, `groupBy`, `totals` (R2.2),
  `costBreakdown` (R2.5), `timeSeries` (R2.3), `groups` (R2.4, empty without `groupBy`).
- **Side effects:** none.
- **Errors:** 400 (`start` not before `end`, more than 5,000 buckets, missing or malformed parameter).

### 5.3 Routing Recommendation: `POST /api/v1/recommendations`

- **Headers:** `X-Client-Id`.
- **Body:** `estimatedPromptTokens` (required, ≥ 0); `expectedOutputTokens`, `minContextWindow`,
  `maxLatencyMs` (optional, ≥ 0); `taskComplexity` (`LOW` | `MEDIUM` | `HIGH`); `minCapabilityTier`
  (1–5); `optimizeFor` (`LOWEST_COST` | `LOWEST_LATENCY` | `HIGHEST_CAPABILITY`).
- **Success:** `200` with `recommended` and `fallback` (each: `provider`, `modelId`,
  `capabilityTier`, `contextWindow`, `estimatedCost`, `expectedLatencyMs`, `errorRate`,
  `sampleSize`, `score`, `scoreBreakdown`), `budgetPressure`, `weights`, `rankedOptions`, `excluded`
  (`provider`, `modelId`, `reason`). If every model is excluded, `recommended` and `fallback` are null.
- **Side effects:** none.
- **Errors:** 400 (invalid field).

### 5.4 Budget Rules

| Method and path | Success | Errors |
|---|---|---|
| `POST /api/v1/budgets` | `201`, `status: "created"` | 400 |
| `PUT /api/v1/budgets/{ruleId}` (replaces the rule) | `200`, `status: "updated"` | 400, 404 |
| `GET /api/v1/budgets` | `200`, list of the caller's rules, oldest first | — |
| `GET /api/v1/budgets/{ruleId}` | `200`, `status: "ok"` | 404 |
| `DELETE /api/v1/budgets/{ruleId}` | `204` | 404 |

- **Headers:** `X-Client-Id` on every call. A rule owned by another client is reported as 404.
- **Body (POST, PUT):** fields of 2.3.
- **Response:** `status`, `ruleId`, `scope`, `scopeValue`, `period`, `limitAmount`, `thresholds`,
  `callbackUrl`, `currentSpend`, `percentConsumed`, `crossedThresholds` (R4.1, R4.2).
- **Side effects:** stores, replaces or deletes the rule (R4.5).

### 5.5 Model Catalog

| Method and path | Who | Success | Errors |
|---|---|---|---|
| `POST /api/v1/catalog` | administrators | `201` with `stored` (new version) and `active` (version in effect now, or null) | 400, 403 |
| `GET /api/v1/catalog` | anyone | `200`, active version of every model, sorted by provider then model | — |
| `GET /api/v1/catalog/{provider}/{modelId}` | anyone | `200`, active version | 404 |
| `GET /api/v1/catalog/{provider}/{modelId}/history` | anyone | `200`, every version, oldest first | 404 |

- **Body (POST):** fields of 2.2. Writes require `X-Client-Id`; reads do not.
- **Side effects:** POST stores a new version (R5.1).

### 5.6 Cost Savings: `POST /api/v1/savings/analysis`

- **Headers:** `X-Client-Id`.
- **Body:** `start`, `end` (required); `projectTag` (optional); `alternatives` (optional list of
  `{provider, modelId}`).
- **Success:** `200` with `start`, `end`, `projectTag`, `requestCount`, `actualCost`,
  `largestRequestTokens`, `highestCapabilityTierUsed`, `alternatives` (`provider`, `modelId`,
  `projectedCost`, `savings`, `savingsPercent`, `averageLatencyMs`, `currentlyUsed`,
  `requestsPricedAtCurrentRate`), `excluded`, `unknownModelsUsed` (models used in the range that are not in the catalog; their calls are left out of the analysis).
- **Side effects:** none.
- **Errors:** 400 (`start` not before `end`, invalid field).

## 6. Test cases

Each case maps to the test that verifies it. "—" means not yet covered by an automated test.

### T1. Log Token Usage

| ID | Given / when | Then | Test |
|---|---|---|---|
| T1.1 | gpt-4o at $2.50 / $10.00 / $1.25 cached; 10,000 input (4,000 cached), 2,000 output | cost $0.04, version recorded | `UsageServiceTest.pricesWithVersionInEffectAtRequestTimestamp` |
| T1.2 | Two versions effective 2025-01-01 and 2026-01-01; calls dated 2025-06 and now | priced at v1 and v2 respectively | `LedgerApiTest.logUsagePricesAtRequestTimeAndIsIdempotent` |
| T1.3 | `cachedInputTokens` omitted | treated as 0 | `UsageServiceTest.missingCachedTokensAreTreatedAsZero` |
| T1.4 | Provider `" OpenAI "`, project `"  support-bot "` | stored as `openai`, `support-bot` | `UsageServiceTest.storesNormalizedModelAndTrimmedProject` |
| T1.5 | Blank `projectTag` | stored as null | `UsageServiceTest.blankProjectTagIsStoredAsNull` |
| T1.6 | Same key, identical call | 200, original `recordId`, `replayed: true`, nothing stored | `UsageServiceTest.identicalReportReturnsOriginalWithoutSaving`, `LedgerApiTest.logUsagePricesAtRequestTimeAndIsIdempotent` |
| T1.7 | Same key, different latency, status or project | still a replay | `UsageServiceTest.differentLatencyStatusOrProjectStillReplays` |
| T1.8 | Same key, different tokens, model or timestamp | 409 | `UsageServiceTest.differentTokenCountsConflict`, `differentModelConflicts`, `differentTimestampConflicts`, `LedgerApiTest.logUsageRejectsReusedKeyForDifferentCallButScopesKeysPerClient` |
| T1.9 | Another client uses the same key | 201, new record | `LedgerApiTest.logUsageRejectsReusedKeyForDifferentCallButScopesKeysPerClient` |
| T1.10 | cached 1,001 > input 1,000 | 400 | `UsageServiceTest.rejectsMoreCachedTokensThanInputTokens`, `LedgerApiTest.logUsageRejectsBlankClientAndInconsistentTokenCounts` |
| T1.11 | Timestamp now + 5 min + 1 s / now + 5 min | 400 / accepted | `UsageServiceTest.rejectsTimestampBeyondAllowedClockSkew`, `acceptsTimestampWithinAllowedClockSkew` |
| T1.12 | Unknown model | 400 "not in the catalog", nothing stored | `UsageServiceTest.propagatesUnknownModelErrorWithoutSaving`, `LedgerApiTest.logUsageRejectsUnknownModelAndInvalidPayload` |
| T1.13 | Body missing required fields | 400 with per-field `details` | `LedgerApiTest.logUsageRejectsUnknownModelAndInvalidPayload` |
| T1.14 | Missing / blank `X-Client-Id` | 400 | `LedgerApiTest.logUsageRejectsUnknownModelAndInvalidPayload`, `logUsageRejectsBlankClientAndInconsistentTokenCounts` |
| T1.15 | Rule at 50% crossed earlier / newly crossed 80% | `budgetThresholdBreached` true, `alertTriggered` false / both true | `UsageServiceTest.previouslyCrossedThresholdIsBreachedWithoutNewAlert`, `newlyCrossedThresholdTriggersAlertAndReportsRemainingBudget` |

### T2. Metrics

| ID | Given / when | Then | Test |
|---|---|---|---|
| T2.1 | Events with mixed status and latency | totals, error rate, average and p95 latency per R2.2 | `UsageStatsTest.computesTotalsErrorRateAndLatency` |
| T2.2 | No events | latency fields null | `UsageStatsTest.emptyInputHasNoLatency` |
| T2.3 | One event | p95 equals its latency | `UsageStatsTest.percentileOfSingleValueIsThatValue` |
| T2.4 | Events across providers, `groupBy=PROVIDER`, `DAILY` | per-provider error rate, average and p95 latency; 2 daily buckets; breakdown most expensive first | `LedgerApiTest.metricsAggregateAndGroupUsage` |
| T2.5 | Same events filtered by `project` | only that project's calls counted | `LedgerApiTest.metricsAggregateAndGroupUsage` |
| T2.6 | Event at exactly `end` | not counted | — |
| T2.7 | `start` ≥ `end`; range over 5,000 hourly buckets | 400 | — |

### T3. Recommendation

| ID | Given / when | Then | Test |
|---|---|---|---|
| T3.1 | Several qualifying models | cheapest wins under `LOWEST_COST`; fallback from another provider | `RecommendationServiceTest.picksCheapestQualifyingModelWithFallbackFromAnotherProvider` |
| T3.2 | Models below required context or tier | excluded with reasons | `RecommendationServiceTest.excludesModelsBelowContextWindowOrTier` |
| T3.3 | Recorded average latency above `maxLatencyMs` | excluded | `RecommendationServiceTest.excludesModelsWhoseRecordedLatencyExceedsTolerance` |
| T3.4 | Budget threshold crossed | `budgetPressure: true`, cost weight increased | `RecommendationServiceTest.budgetPressureShiftsWeightTowardCost`, `reportsBudgetPressureWhenThresholdIsCrossed` |
| T3.5 | End to end with catalog and history | recommendation returned over HTTP | `LedgerApiTest.recommendationUsesCatalogAndHistory` |
| T3.6 | Every model excluded | `recommended` and `fallback` null | — |

### T4. Budgets

| ID | Given / when | Then | Test |
|---|---|---|---|
| T4.1 | $10 limit, 50% alert already sent, spend reaches $8.50 | crossed [50, 80], newly [80], one webhook, remaining $1.50 | `BudgetServiceTest.firesEachNewlyCrossedThresholdOnceWithWebhook` |
| T4.2 | Same without `callbackUrl` | alert recorded, no webhook | `BudgetServiceTest.recordsAlertButSendsNoWebhookWithoutCallbackUrl` |
| T4.3 | `PROJECT` / `PROVIDER` rule | counts only that project / provider | `BudgetServiceTest.projectRuleIgnoresEventsFromOtherProjects`, `providerRuleSumsOnlyThatProvider` |
| T4.4 | `scopeValue` with `CLIENT`; `ftp://` callback | 400 | `BudgetServiceTest.rejectsScopeValueForClientScope`, `rejectsNonHttpCallbackUrl` |
| T4.5 | Monthly / weekly / daily period | calendar month / Monday-start week / UTC day | `BudgetPeriodTest` (3 tests) |
| T4.6 | Webhook delivery | JSON POST; unreachable URL does not throw | `HttpWebhookNotifierTest` (2 tests) |
| T4.7 | Rule over HTTP; spend crosses 50% then rises further | create, get, delete; alert and webhook exactly once | `LedgerApiTest.budgetThresholdAlertsFireOnceAndRulesAreManageable` |
| T4.8 | Another client's `ruleId` | 404 | `LedgerApiTest.budgetThresholdAlertsFireOnceAndRulesAreManageable` |
| T4.9 | `PUT` replaces a rule; `GET /budgets` lists rules oldest first | updated rule and spend returned | — |

### T5. Catalog

| ID | Given / when | Then | Test |
|---|---|---|---|
| T5.1 | Non-admin POST | 403 | `CatalogServiceTest.nonAdminCannotWrite`, `LedgerApiTest.catalogKeepsPriceHistoryAndOnlyAdminsWrite` |
| T5.2 | Second write for a model | version 2, names normalized, history keeps v1 | `CatalogServiceTest.newVersionIncrementsAndNormalizesNames`, `LedgerApiTest.catalogKeepsPriceHistoryAndOnlyAdminsWrite` |
| T5.3 | Unknown model / known model with only future prices | "not in the catalog" / "no price in effect" | `CatalogServiceTest.unknownModelIsReportedAsNotInCatalog`, `knownModelWithoutEffectivePriceIsReportedDifferently` |
| T5.4 | Cost formula, cached fallback, zero tokens | per R1.1 | `CostCalculatorTest` (4 tests) |

### T6. Savings

| ID | Given / when | Then | Test |
|---|---|---|---|
| T6.1 | Usage on one model, cheaper and smaller alternatives | re-priced costs, savings, exclusions with reasons | `LedgerApiTest.savingsRepricesUsageUnderAlternatives` |
| T6.2 | Alternative with no price at a call's timestamp | priced at current rate, counted | — |
| T6.3 | No usage in range | actual 0, `savingsPercent` 0 | — |

## 7. Refinements of the proposal

| Proposal | Implementation | Why |
|---|---|---|
| Op 1 returns "remaining budget for current billing cycle" | Returns a status for every applicable rule, each with `remaining` | A client can have several rules (client, project, provider) with different periods. |
| Op 1 idempotency returns the original record | Also returns 409 when the key is reused for a different call | Replaying would silently drop the new call. |
| Op 3 "latency tolerance" | `maxLatencyMs` excludes models whose recorded average exceeds it; models with no history are kept | Latency is known only from the client's own recorded calls. |
| Op 3 "task complexity level" | `LOW`/`MEDIUM`/`HIGH` mapped to minimum tiers 1/3/4, or explicit `minCapabilityTier` | Gives the catalog's capability tier a concrete meaning. |
| Op 4 "evaluates existing usage to set warning status" | Create/update report current spend and crossed thresholds; alerts fire only when usage is logged | Avoids sending a webhook for spending that happened before the rule existed. |
| Op 5 "any client can read" | Catalog reads need no `X-Client-Id` | The catalog is not client-specific. |
| Client ID | Trusted `X-Client-Id` header, no authentication | Authentication was out of scope for Iteration 1. |
| Webhook notification | Asynchronous, after commit, not retried | Keeps logging fast and never notifies for a call that was rolled back. |