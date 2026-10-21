# usage-cli — terminal usage reporter

Client application 1 of the proposal: a read-only consumer of the ledger service's reports.

It calls the service's **usage metrics** operation (`GET /api/v1/usage/metrics`, operation 2) once
for one UTC calendar month and prints a plain-text summary: tokens, cost, and cost per model. It
writes nothing back and talks to the service over HTTP only — no shared code, classes or database.

## Build

Requires Java 21 or newer and Maven. From this directory:

```bash
mvn verify          # Checkstyle, 31 unit tests, JaCoCo report, PMD
```

This produces a self-contained `target/usage-cli.jar`.

## Run

Start the ledger service first (`mvn spring-boot:run` in the repository root), then:

```bash
java -jar target/usage-cli.jar --client-id acme
java -jar target/usage-cli.jar --client-id acme --month 2026-10 --project chatbot
java -jar target/usage-cli.jar --client-id acme --base-url https://ledger.example.com
java -jar target/usage-cli.jar --help
```

| Option | Meaning |
|---|---|
| `--client-id <id>` | **required** — client whose usage is reported, sent as `X-Client-Id` |
| `--month <YYYY-MM>` | month to report, UTC (default: the current month) |
| `--project <tag>` | only count calls tagged with this project |
| `--base-url <url>` | service root (default: `http://localhost:8080`) |
| `-h`, `--help` | show the help text |

Exits `0` on success and `1` on any error, with the reason on stderr.

## Example output

```
AI model usage - 2026-10
  client   acme
  project  chatbot
  period   2026-10-01T00:00:00Z .. 2026-11-01T00:00:00Z (UTC)

Totals
  requests       5
  input tokens   83,200 (6,000 cached)
  output tokens  15,300
  total tokens   98,500
  total cost     $0.2672
  error rate     20.00%
  avg latency    2172.0 ms
  p95 latency    4800 ms

Cost by model
  MODEL                          REQUESTS       TOKENS         COST
  anthropic/claude-sonnet-4             2       28,000      $0.1416
  openai/gpt-4o                         2       35,300      $0.1205
  google/gemini-2.0-flash               1       35,200      $0.0051
```

## Layout

```
src/main/java/com/mangomango/ledger/cli/
  UsageReportCli.java    main, help, exit codes
  CliOptions.java        argument parsing, validation, help text
  MonthRange.java        YYYY-MM -> the UTC [start, end) range the service expects
  MetricsClient.java     the one HTTP GET, and service/transport error reporting
  UsageSummaryDto.java   the part of the response this client prints
  ReportFormatter.java   plain-text rendering
  CliException.java      errors shown to the user as a message, not a stack trace
```

460 lines of main code. Tests use JUnit 5 and AssertJ; `MetricsClient` is tested against a stub
HTTP server (`com.sun.net.httpserver`), so the suite needs no running service.

## Notes

- Deliberately minimal for the MVP: one month, one project filter, totals and cost per model. The
  service's other metrics parameters (`granularity`, `groupBy`, provider/model filters) and the
  time series it returns are not exposed — the response fields this client does not print are
  ignored, so adding them later is additive.
- `maven-pmd-plugin` 3.26.0 bundles PMD 7.7.0, which cannot read the class files of JDKs newer
  than 21 and crashes analysing this client's `java.net.http` usage. The POM therefore pins the
  PMD engine to 7.28.0. CI runs Java 21, where either version works.
- The Spring Boot parent is used for dependency management only; this client has no Spring
  runtime. Its POM overrides the parent's `maven-shade-plugin` configuration, which is set up for
  repackaging Spring applications.
