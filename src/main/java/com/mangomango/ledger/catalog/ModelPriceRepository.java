package com.mangomango.ledger.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for model pricing versions. */
public interface ModelPriceRepository extends JpaRepository<ModelPrice, Long> {

  /** Returns every version of a model, oldest first. */
  List<ModelPrice> findByProviderAndModelIdOrderByVersionAsc(String provider, String modelId);

  /** Returns true if any version of the model is stored. */
  boolean existsByProviderAndModelId(String provider, String modelId);

  /** Returns the most recently added version of a model. */
  Optional<ModelPrice> findTopByProviderAndModelIdOrderByVersionDesc(
      String provider, String modelId);

  /** Returns the version in effect at {@code at}: latest effective date, then highest version. */
  @Query("select p from ModelPrice p where p.provider = :provider and p.modelId = :modelId"
      + " and p.effectiveFrom <= :at order by p.effectiveFrom desc, p.version desc limit 1")
  Optional<ModelPrice> findInEffectAt(
      @Param("provider") String provider, @Param("modelId") String modelId,
      @Param("at") Instant at);

  /** Returns every distinct model in the catalog. */
  @Query("select distinct new com.mangomango.ledger.catalog.ModelKey(p.provider, p.modelId)"
      + " from ModelPrice p")
  List<ModelKey> findAllModelKeys();
}
