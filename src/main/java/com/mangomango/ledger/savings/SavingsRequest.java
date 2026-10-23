// Generated using Claude Code.

package com.mangomango.ledger.savings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * Request body for a cost-savings analysis.
 *
 * @param start range start (inclusive)
 * @param end range end (exclusive)
 * @param projectTag optional project to restrict the analysis to
 * @param alternatives models to compare; when empty, every catalog model is compared
 */
public record SavingsRequest(
    @NotNull Instant start,
    @NotNull Instant end,
    String projectTag,
    List<@Valid ModelRef> alternatives) {

  /**
   * Reference to a catalog model.
   *
   * @param provider provider name
   * @param modelId model identifier
   */
  public record ModelRef(@NotBlank String provider, @NotBlank String modelId) {}
}
