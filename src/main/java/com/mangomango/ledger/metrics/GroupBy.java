package com.mangomango.ledger.metrics;

import com.mangomango.ledger.usage.UsageEvent;

/** Dimension used to split usage into groups. */
public enum GroupBy {
  PROVIDER,
  MODEL,
  PROJECT;

  /** Placeholder group key for events without a project tag. */
  public static final String UNTAGGED = "(untagged)";

  /** Returns the group key of an event under this dimension. */
  public String keyOf(UsageEvent e) {
    return switch (this) {
      case PROVIDER -> e.getProvider();
      case MODEL -> e.getProvider() + "/" + e.getModelId();
      case PROJECT -> e.getProjectTag() == null ? UNTAGGED : e.getProjectTag();
    };
  }
}
