package com.mangomango.ledger.metrics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Time-series bucket size. Buckets are aligned in UTC. */
public enum Granularity {
  HOURLY,
  DAILY,
  MONTHLY;

  /** Returns the start of the bucket containing {@code at}. */
  public Instant bucketStart(Instant at) {
    ZonedDateTime t = at.atZone(ZoneOffset.UTC);
    return switch (this) {
      case HOURLY -> t.truncatedTo(ChronoUnit.HOURS).toInstant();
      case DAILY -> t.truncatedTo(ChronoUnit.DAYS).toInstant();
      case MONTHLY -> t.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).toInstant();
    };
  }

  /** Returns the start of the bucket after the one starting at {@code bucketStart}. */
  public Instant next(Instant bucketStart) {
    ZonedDateTime t = bucketStart.atZone(ZoneOffset.UTC);
    return switch (this) {
      case HOURLY -> t.plusHours(1).toInstant();
      case DAILY -> t.plusDays(1).toInstant();
      case MONTHLY -> t.plusMonths(1).toInstant();
    };
  }
}
