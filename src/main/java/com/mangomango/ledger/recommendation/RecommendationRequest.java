package com.mangomango.ledger.recommendation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Requirements for a routing recommendation.
 *
 * @param estimatedPromptTokens expected input tokens of the upcoming call
 * @param expectedOutputTokens expected output tokens; defaults to 500
 * @param minContextWindow optional minimum context window
 * @param taskComplexity optional difficulty, mapped to a minimum capability tier
 * @param minCapabilityTier optional explicit minimum tier; overrides taskComplexity when higher
 * @param maxLatencyMs optional latency tolerance; models with higher recorded average are excluded
 * @param optimizeFor what to favor; defaults to LOWEST_COST
 */
public record RecommendationRequest(
    @NotNull @PositiveOrZero Long estimatedPromptTokens,
    @PositiveOrZero Long expectedOutputTokens,
    @PositiveOrZero Integer minContextWindow,
    TaskComplexity taskComplexity,
    @Min(1) @Max(5) Integer minCapabilityTier,
    @PositiveOrZero Long maxLatencyMs,
    Optimization optimizeFor) {}
