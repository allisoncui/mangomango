package com.mangomango.ledger.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence and aggregate queries for usage events. */
public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

  /** Returns the client's event with this idempotency key, if any. */
  Optional<UsageEvent> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

  /** Returns a client's events with request timestamps in {@code [start, end)}. */
  @Query("select e from UsageEvent e where e.clientId = :clientId"
      + " and e.requestTimestamp >= :start and e.requestTimestamp < :end"
      + " order by e.requestTimestamp")
  List<UsageEvent> findInRange(
      @Param("clientId") String clientId,
      @Param("start") Instant start,
      @Param("end") Instant end);

  /** Sums a client's spend in {@code [start, end)}. */
  @Query("select coalesce(sum(e.cost), 0) from UsageEvent e where e.clientId = :clientId"
      + " and e.requestTimestamp >= :start and e.requestTimestamp < :end")
  BigDecimal sumCost(
      @Param("clientId") String clientId,
      @Param("start") Instant start,
      @Param("end") Instant end);

  /** Sums a client's spend for one project tag in {@code [start, end)}. */
  @Query("select coalesce(sum(e.cost), 0) from UsageEvent e where e.clientId = :clientId"
      + " and e.projectTag = :project"
      + " and e.requestTimestamp >= :start and e.requestTimestamp < :end")
  BigDecimal sumCostForProject(
      @Param("clientId") String clientId,
      @Param("project") String project,
      @Param("start") Instant start,
      @Param("end") Instant end);

  /** Sums a client's spend with one provider in {@code [start, end)}. */
  @Query("select coalesce(sum(e.cost), 0) from UsageEvent e where e.clientId = :clientId"
      + " and e.provider = :provider"
      + " and e.requestTimestamp >= :start and e.requestTimestamp < :end")
  BigDecimal sumCostForProvider(
      @Param("clientId") String clientId,
      @Param("provider") String provider,
      @Param("start") Instant start,
      @Param("end") Instant end);

  /** Returns a client's events for one model in {@code [start, end)}. */
  @Query("select e from UsageEvent e where e.clientId = :clientId"
      + " and e.provider = :provider and e.modelId = :modelId"
      + " and e.requestTimestamp >= :start and e.requestTimestamp < :end")
  List<UsageEvent> findForModelInRange(
      @Param("clientId") String clientId,
      @Param("provider") String provider,
      @Param("modelId") String modelId,
      @Param("start") Instant start,
      @Param("end") Instant end);
}
