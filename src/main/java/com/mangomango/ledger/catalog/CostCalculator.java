// Generated using Claude Code.

package com.mangomango.ledger.catalog;

import com.mangomango.ledger.common.Money;
import java.math.BigDecimal;

/** Prices token counts against a catalog version using one cost model for every provider. */
public final class CostCalculator {

  private CostCalculator() {}

  /**
   * Computes the dollar cost of one request.
   *
   * @param price the pricing version to apply
   * @param inputTokens total input tokens, including cached input tokens
   * @param cachedInputTokens the portion of input tokens served from cache
   * @param outputTokens output tokens
   * @return cost in dollars at {@link Money#SCALE}
   */
  public static BigDecimal cost(
      ModelPrice price, long inputTokens, long cachedInputTokens, long outputTokens) {
    long uncached = inputTokens - cachedInputTokens;
    return Money.round(
        Money.tokenCost(uncached, price.getInputPricePerMillion())
            .add(Money.tokenCost(cachedInputTokens, price.effectiveCachedInputPricePerMillion()))
            .add(Money.tokenCost(outputTokens, price.getOutputPricePerMillion())));
  }
}
