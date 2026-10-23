// Generated using Claude Code.

package com.mangomango.ledger.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for adding a pricing version to the catalog.
 *
 * @param provider provider name, e.g. {@code openai}
 * @param modelId model identifier
 * @param inputPricePerMillion dollars per million input tokens
 * @param outputPricePerMillion dollars per million output tokens
 * @param cachedInputPricePerMillion optional dollars per million cached input tokens
 * @param contextWindow maximum tokens per request
 * @param capabilityTier 1 (basic) to 5 (frontier)
 * @param effectiveDate when this price starts applying; defaults to now
 */
public record CatalogEntryRequest(
    @NotBlank String provider,
    @NotBlank String modelId,
    @NotNull @DecimalMin("0.0") BigDecimal inputPricePerMillion,
    @NotNull @DecimalMin("0.0") BigDecimal outputPricePerMillion,
    @DecimalMin("0.0") BigDecimal cachedInputPricePerMillion,
    @Min(1) int contextWindow,
    @Min(1) @Max(5) int capabilityTier,
    Instant effectiveDate) {}
