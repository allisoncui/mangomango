// Generated using Claude Code.

package com.mangomango.ledger.common;

import java.util.List;

/**
 * Error body returned for every failed request.
 *
 * @param error short machine-readable error code
 * @param message human-readable summary
 * @param details per-field problems, empty when not applicable
 */
public record ErrorResponse(String error, String message, List<String> details) {}
