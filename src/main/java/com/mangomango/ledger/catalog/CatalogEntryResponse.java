// Generated using Claude Code.

package com.mangomango.ledger.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One pricing version as returned to clients.
 *
 * @param entryId catalog row ID
 * @param provider provider name
 * @param modelId model identifier
 * @param version pricing version number for this model, starting at 1
 * @param inputPricePerMillion dollars per million input tokens
 * @param outputPricePerMillion dollars per million output tokens
 * @param cachedInputPricePerMillion dollars per million cached input tokens, or null
 * @param contextWindow maximum tokens per request
 * @param capabilityTier 1 (basic) to 5 (frontier)
 * @param effectiveDate when this version starts applying
 */
public record CatalogEntryResponse(
    Long entryId,
    String provider,
    String modelId,
    int version,
    BigDecimal inputPricePerMillion,
    BigDecimal outputPricePerMillion,
    BigDecimal cachedInputPricePerMillion,
    int contextWindow,
    int capabilityTier,
    Instant effectiveDate) {

  /** Converts an entity to its response form. */
  public static CatalogEntryResponse from(ModelPrice p) {
    return new CatalogEntryResponse(
        p.getId(),
        p.getProvider(),
        p.getModelId(),
        p.getVersion(),
        p.getInputPricePerMillion(),
        p.getOutputPricePerMillion(),
        p.getCachedInputPricePerMillion(),
        p.getContextWindow(),
        p.getCapabilityTier(),
        p.getEffectiveFrom());
  }
}
