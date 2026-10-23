// Generated using Claude Code.

package com.mangomango.ledger.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BudgetPeriodTest {

  private static final Instant AT = Instant.parse("2026-10-15T17:30:00Z"); // a Thursday

  @Test
  void monthlyPeriodCoversCalendarMonth() {
    assertThat(BudgetPeriod.MONTHLY.start(AT)).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    assertThat(BudgetPeriod.MONTHLY.end(AT)).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
  }

  @Test
  void weeklyPeriodStartsMonday() {
    assertThat(BudgetPeriod.WEEKLY.start(AT)).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
    assertThat(BudgetPeriod.WEEKLY.end(AT)).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
  }

  @Test
  void dailyPeriodCoversUtcDay() {
    assertThat(BudgetPeriod.DAILY.start(AT)).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
    assertThat(BudgetPeriod.DAILY.end(AT)).isEqualTo(Instant.parse("2026-10-16T00:00:00Z"));
  }
}
