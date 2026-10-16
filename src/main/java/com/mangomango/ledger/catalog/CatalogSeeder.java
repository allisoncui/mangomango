package com.mangomango.ledger.catalog;

import com.mangomango.ledger.common.LedgerProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads a small sample catalog into an empty database so the service is usable for demos. The
 * prices are illustrative, not authoritative; administrators should maintain real prices through
 * the catalog API.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(CatalogSeeder.class);
  private static final Instant SEED_EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");

  private final ModelPriceRepository repository;
  private final LedgerProperties properties;
  private final Clock clock;

  /** Creates the seeder. */
  public CatalogSeeder(ModelPriceRepository repository, LedgerProperties properties,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.seedCatalog() || repository.count() > 0) {
      return;
    }
    seed("openai", "gpt-4o", "2.50", "10.00", "1.25", 128_000, 4);
    seed("openai", "gpt-4o-mini", "0.15", "0.60", "0.075", 128_000, 2);
    seed("anthropic", "claude-sonnet-4", "3.00", "15.00", "0.30", 200_000, 4);
    seed("anthropic", "claude-haiku-3.5", "0.80", "4.00", "0.08", 200_000, 2);
    seed("google", "gemini-2.0-flash", "0.10", "0.40", "0.025", 1_000_000, 2);
    seed("google", "gemini-1.5-pro", "1.25", "5.00", "0.3125", 2_000_000, 4);
    LOG.info("Seeded sample model catalog with {} entries", repository.count());
  }

  private void seed(String provider, String model, String in, String out, String cached,
      int contextWindow, int tier) {
    repository.save(
        new ModelPrice(
            provider,
            model,
            1,
            new BigDecimal(in),
            new BigDecimal(out),
            new BigDecimal(cached),
            contextWindow,
            tier,
            SEED_EFFECTIVE_FROM,
            clock.instant()));
  }
}
