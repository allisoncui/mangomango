// Generated using Claude Code.

package com.mangomango.ledger.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for consistent monetary arithmetic. */
public final class Money {

  /** Scale used for stored and returned dollar amounts. */
  public static final int SCALE = 6;

  private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

  private Money() {}

  /** Returns the cost of {@code tokens} at {@code pricePerMillion} dollars per million tokens. */
  public static BigDecimal tokenCost(long tokens, BigDecimal pricePerMillion) {
    return BigDecimal.valueOf(tokens)
        .multiply(pricePerMillion)
        .divide(ONE_MILLION, SCALE, RoundingMode.HALF_UP);
  }

  /** Rounds an amount to the standard money scale. */
  public static BigDecimal round(BigDecimal amount) {
    return amount.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** Returns {@code part / whole * 100} rounded to two decimals, or zero when whole is zero. */
  public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
    if (whole.signum() == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
  }
}
