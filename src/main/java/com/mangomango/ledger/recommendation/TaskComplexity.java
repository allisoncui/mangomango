// Generated using Claude Code.

package com.mangomango.ledger.recommendation;

/** Coarse task difficulty, mapped to a minimum catalog capability tier. */
public enum TaskComplexity {
  LOW(1),
  MEDIUM(3),
  HIGH(4);

  private final int minimumTier;

  TaskComplexity(int minimumTier) {
    this.minimumTier = minimumTier;
  }

  /** Returns the lowest catalog capability tier suitable for this complexity. */
  public int minimumTier() {
    return minimumTier;
  }
}
