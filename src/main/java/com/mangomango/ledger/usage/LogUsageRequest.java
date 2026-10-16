package com.mangomango.ledger.usage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request body for reporting one AI model call.
 *
 * @param provider provider name, e.g. {@code openai}
 * @param modelId model identifier as listed in the catalog
 * @param inputTokens total input tokens, including any cached input tokens
 * @param outputTokens output tokens
 * @param cachedInputTokens optional portion of input tokens served from cache
 * @param idempotencyKey client-chosen key; a retried report with the same key is not re-counted
 * @param requestTimestamp when the call was made; selects the price in effect
 * @param latencyMs end-to-end latency of the call in milliseconds
 * @param status whether the call succeeded
 * @param projectTag optional project the spending is attributed to
 */
public record LogUsageRequest(
    @NotBlank String provider,
    @NotBlank String modelId,
    @NotNull @PositiveOrZero Long inputTokens,
    @NotNull @PositiveOrZero Long outputTokens,
    @PositiveOrZero Long cachedInputTokens,
    @NotBlank @Size(max = 200) String idempotencyKey,
    @NotNull Instant requestTimestamp,
    @NotNull @PositiveOrZero Long latencyMs,
    @NotNull RequestStatus status,
    @Size(max = 100) String projectTag) {}
