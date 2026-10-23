// Generated using Claude Code.

package com.mangomango.ledger.cli;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * One calendar month in UTC, as the half-open instant range {@code [start, end)} the service's
 * metrics operation expects.
 *
 * @param month the month being reported
 * @param start first instant of the month, inclusive
 * @param end first instant of the following month, exclusive
 */
public record MonthRange(YearMonth month, Instant start, Instant end) {

  /**
   * Returns the UTC range covering {@code month}.
   *
   * @param month the month to cover
   * @return the range
   */
  public static MonthRange of(YearMonth month) {
    return new MonthRange(
        month,
        month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
  }

  /**
   * Parses a {@code YYYY-MM} month.
   *
   * @param text the month to parse
   * @return the range covering that month
   * @throws CliException when {@code text} is not a valid month
   */
  public static MonthRange parse(String text) {
    try {
      return of(YearMonth.parse(text));
    } catch (DateTimeParseException e) {
      throw new CliException("--month must look like YYYY-MM, got: " + text);
    }
  }

  /**
   * Returns the range covering the UTC month {@code clock} is currently in.
   *
   * @param clock source of the current time
   * @return the range
   */
  public static MonthRange current(Clock clock) {
    return of(YearMonth.now(clock.withZone(ZoneOffset.UTC)));
  }
}
