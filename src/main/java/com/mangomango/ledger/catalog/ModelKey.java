// Generated using Claude Code.

package com.mangomango.ledger.catalog;

import java.util.Locale;

/**
 * Normalized identity of a model across the catalog and usage records.
 *
 * @param provider lower-case provider name, e.g. {@code openai}
 * @param modelId lower-case model identifier
 */
public record ModelKey(String provider, String modelId) {

  /** Builds a key, trimming and lower-casing both parts. */
  public static ModelKey of(String provider, String modelId) {
    return new ModelKey(normalize(provider), normalize(modelId));
  }

  /** Trims and lower-cases a provider or model name; returns null for null input. */
  public static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return provider + "/" + modelId;
  }
}
