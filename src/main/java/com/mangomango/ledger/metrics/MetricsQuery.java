// Generated using Claude Code.

package com.mangomango.ledger.metrics;

import java.time.Instant;

/**
 * Parameters of a metrics request.
 *
 * @param start range start (inclusive)
 * @param end range end (exclusive)
 * @param provider optional provider filter
 * @param model optional model identifier filter
 * @param project optional project tag filter
 * @param granularity time-series bucket size
 * @param groupBy optional grouping dimension
 */
public record MetricsQuery(
    Instant start,
    Instant end,
    String provider,
    String model,
    String project,
    Granularity granularity,
    GroupBy groupBy) {}
