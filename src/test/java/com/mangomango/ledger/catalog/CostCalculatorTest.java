package com.mangomango.ledger.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

  private static ModelPrice price(String in, String out, String cached) {
    return new ModelPrice("p", "m", 1, new BigDecimal(in), new BigDecimal(out),
        cached == null ? null : new BigDecimal(cached), 1000, 1, Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  void pricesInputAndOutputPerMillionTokens() {
    BigDecimal cost = CostCalculator.cost(price("2.00", "8.00", null), 500_000, 0, 250_000);

    assertThat(cost).isEqualByComparingTo("3.00");
  }

  @Test
  void cachedTokensUseCachedPrice() {
    BigDecimal cost = CostCalculator.cost(price("2.00", "8.00", "0.50"), 1_000_000, 400_000, 0);

    // 600k uncached at $2 + 400k cached at $0.50
    assertThat(cost).isEqualByComparingTo("1.40");
  }

  @Test
  void cachedTokensFallBackToInputPriceWhenNoCachedPrice() {
    BigDecimal cost = CostCalculator.cost(price("2.00", "8.00", null), 1_000_000, 400_000, 0);

    assertThat(cost).isEqualByComparingTo("2.00");
  }

  @Test
  void zeroTokensCostNothing() {
    assertThat(CostCalculator.cost(price("2.00", "8.00", null), 0, 0, 0)).isZero();
  }
}
