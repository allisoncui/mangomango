package com.mangomango.ledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MonthRangeTest {

  @Test
  void coversTheWholeMonthAsHalfOpenRange() {
    MonthRange range = MonthRange.of(YearMonth.of(2026, 10));

    assertThat(range.start()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    assertThat(range.end()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
  }

  @Test
  void rollsOverIntoTheNextYearInDecember() {
    assertThat(MonthRange.of(YearMonth.of(2026, 12)).end())
        .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
  }

  @Test
  void parsesYearMonth() {
    assertThat(MonthRange.parse("2026-01").start())
        .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void rejectsMalformedMonth() {
    assertThatThrownBy(() -> MonthRange.parse("2026-10-05"))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("--month must look like YYYY-MM");
  }

  @Test
  void currentMonthIsTakenInUtcNotTheLocalZone() {
    // 00:30 UTC on 1 November is still 31 October in New York; the report is UTC-based.
    Clock clock =
        Clock.fixed(Instant.parse("2026-11-01T00:30:00Z"), ZoneId.of("America/New_York"));

    assertThat(MonthRange.current(clock).month()).isEqualTo(YearMonth.of(2026, 11));
  }
}
