// Generated using Claude Code.

package com.mangomango.ledger.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangomango.ledger.common.ForbiddenException;
import com.mangomango.ledger.common.LedgerProperties;
import com.mangomango.ledger.common.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");

  @Mock private ModelPriceRepository repository;

  private CatalogService service;

  @BeforeEach
  void setUp() {
    service =
        new CatalogService(
            repository,
            new LedgerProperties(Set.of("admin"), false, 30),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static CatalogEntryRequest request() {
    return new CatalogEntryRequest(" OpenAI ", "GPT-X", new BigDecimal("1.00"),
        new BigDecimal("2.00"), null, 8000, 3, null);
  }

  @Test
  void nonAdminCannotWrite() {
    assertThatThrownBy(() -> service.addVersion("someone", request()))
        .isInstanceOf(ForbiddenException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void newVersionIncrementsAndNormalizesNames() {
    ModelPrice existing = new ModelPrice("openai", "gpt-x", 2, BigDecimal.ONE, BigDecimal.ONE,
        null, 8000, 3, NOW.minusSeconds(100), NOW.minusSeconds(100));
    when(repository.findTopByProviderAndModelIdOrderByVersionDesc("openai", "gpt-x"))
        .thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CatalogWriteResponse response = service.addVersion("admin", request());

    assertThat(response.stored().version()).isEqualTo(3);
    assertThat(response.stored().provider()).isEqualTo("openai");
    assertThat(response.stored().modelId()).isEqualTo("gpt-x");
    assertThat(response.stored().effectiveDate()).isEqualTo(NOW);
  }

  @Test
  void unknownModelIsReportedAsNotInCatalog() {
    ModelKey key = ModelKey.of("openai", "nope");
    when(repository.findInEffectAt("openai", "nope", NOW)).thenReturn(Optional.empty());
    when(repository.existsByProviderAndModelId("openai", "nope")).thenReturn(false);

    assertThatThrownBy(() -> service.requirePriceAt(key, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not in the catalog");
  }

  @Test
  void knownModelWithoutEffectivePriceIsReportedDifferently() {
    ModelKey key = ModelKey.of("openai", "future");
    when(repository.findInEffectAt("openai", "future", NOW)).thenReturn(Optional.empty());
    when(repository.existsByProviderAndModelId("openai", "future")).thenReturn(true);

    assertThatThrownBy(() -> service.requirePriceAt(key, NOW))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("No price");
  }
}
