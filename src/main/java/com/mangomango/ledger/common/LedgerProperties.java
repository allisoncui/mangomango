package com.mangomango.ledger.common;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service configuration bound from {@code ledger.*} properties.
 *
 * @param adminClients client IDs allowed to write to the model catalog
 * @param seedCatalog whether to load sample catalog entries on startup
 * @param recommendationLookbackDays days of history used for routing statistics
 */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
    Set<String> adminClients, boolean seedCatalog, int recommendationLookbackDays) {

  /** Applies defaults for unset properties. */
  public LedgerProperties {
    adminClients = adminClients == null ? Set.of() : Set.copyOf(adminClients);
    recommendationLookbackDays =
        recommendationLookbackDays <= 0 ? 30 : recommendationLookbackDays;
  }
}
