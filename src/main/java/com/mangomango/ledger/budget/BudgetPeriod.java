// Generated using Claude Code.

package com.mangomango.ledger.budget;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/** Budget reset period. All periods are computed in UTC. */
public enum BudgetPeriod {
  DAILY,
  WEEKLY,
  MONTHLY;

  /** Returns the start (inclusive) of the period that contains {@code at}. */
  public Instant start(Instant at) {
    LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate first =
        switch (this) {
          case DAILY -> day;
          case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case MONTHLY -> day.withDayOfMonth(1);
        };
    return first.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  /** Returns the end (exclusive) of the period that contains {@code at}. */
  public Instant end(Instant at) {
    LocalDate start = start(at).atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate next =
        switch (this) {
          case DAILY -> start.plusDays(1);
          case WEEKLY -> start.plusWeeks(1);
          case MONTHLY -> start.plusMonths(1);
        };
    return next.atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}
