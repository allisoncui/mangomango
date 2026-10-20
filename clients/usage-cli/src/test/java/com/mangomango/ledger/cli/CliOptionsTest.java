package com.mangomango.ledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CliOptionsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);

  private static CliOptions parse(String... args) {
    return CliOptions.parse(args, CLOCK);
  }

  @Test
  void clientIdAloneIsEnoughAndEverythingElseDefaults() {
    CliOptions options = parse("--client-id", "acme");

    assertThat(options.clientId()).isEqualTo("acme");
    assertThat(options.baseUrl()).isEqualTo("http://localhost:8080");
    assertThat(options.project()).isNull();
    assertThat(options.range().month()).isEqualTo(YearMonth.of(2026, 10));
  }

  @Test
  void readsEveryOption() {
    CliOptions options =
        parse(
            "--client-id", "acme",
            "--project", "chatbot",
            "--month", "2026-09",
            "--base-url", "https://ledger.example.com");

    assertThat(options.clientId()).isEqualTo("acme");
    assertThat(options.project()).isEqualTo("chatbot");
    assertThat(options.range().month()).isEqualTo(YearMonth.of(2026, 9));
    assertThat(options.baseUrl()).isEqualTo("https://ledger.example.com");
  }

  @Test
  void stripsTrailingSlashFromBaseUrl() {
    assertThat(parse("--client-id", "acme", "--base-url", "http://localhost:9090/").baseUrl())
        .isEqualTo("http://localhost:9090");
  }

  @Test
  void requiresClientId() {
    assertThatThrownBy(() -> parse("--project", "chatbot"))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("--client-id is required");
  }

  @Test
  void rejectsUnknownFlag() {
    assertThatThrownBy(() -> parse("--client-id", "acme", "--group-by", "MODEL"))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("Unknown argument: --group-by");
  }

  @Test
  void rejectsFlagWithNoValue() {
    assertThatThrownBy(() -> parse("--client-id"))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("--client-id needs a value");
  }

  @Test
  void detectsHelpFlags() {
    assertThat(CliOptions.wantsHelp(new String[] {"--client-id", "acme", "--help"})).isTrue();
    assertThat(CliOptions.wantsHelp(new String[] {"-h"})).isTrue();
    assertThat(CliOptions.wantsHelp(new String[] {"--client-id", "acme"})).isFalse();
  }

  @Test
  void helpTextNamesTheRequiredFlag() {
    assertThat(CliOptions.usageText()).contains("--client-id").contains("--month <YYYY-MM>");
  }
}
