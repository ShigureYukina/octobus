# Logging & Observability

> Logging and observability rules for the Java rewrite.

---

## Rules

- Log failures at the boundary where context is still available.
- Do not log credentials, secrets, or raw user payloads.
- Include request IDs or trace IDs where available.
- Keep high-frequency relay logs lightweight.

---

## Required Signals

- Authentication failures
- Relay provider failures
- Circuit breaker state changes
- Task and maintenance job outcomes

---

## Stats Tables (Observability Side-Effects)

Every relay request writes to 5 stats tables. All tables share the same
metrics columns: `input_token`, `output_token`, `input_cost`, `output_cost`,
`wait_time`, `request_success`, `request_failed`, `cache_hit_token`.

| Table | Key | Scope |
|-------|-----|-------|
| `stats_total` | id=1 (single row) | Cumulative since start |
| `stats_daily` | date (TEXT, ISO) | Daily aggregation |
| `stats_hourly` | (hour, date) composite PK | Hourly aggregation |
| `stats_apikey` | api_key_id | Per-API-key |
| `stats_channel` | channel_id | Per-channel |

All writes are accumulative UPSERT (INSERT ... ON CONFLICT DO UPDATE SET
col = col + delta).

---

## Relay Log

`relay_log` records every relay operation. Key fields:

| Field | Type | Notes |
|-------|------|-------|
| `actual_model_name` | TEXT | `candidate.modelName()` (= `group_items.model_name`), **not** the upstream response model |
| `usage_recorded` | INTEGER (0/1) | True when tokens were counted for billing |
| `attempts` | TEXT (JSON) | Array of `ChannelAttempt` objects; status values: `"success"`, `"failed"` |
| `total_attempts` | INTEGER | Number of channel attempts for this request |

`ChannelAttempt.status` values:
- `"success"` — candidate returned 2xx
- `"failed"` — candidate returned non-2xx or connection error (not `"failure"`)

---

## PriceLookup Caching

`PriceLookup` loads `model` table at construction time. When tests seed
model prices in `@BeforeEach`, they must call `priceLookup.refresh()`
to reload the cache. The price key in the `model` table must match
`group_items.model_name` because `RelayRun.priceFor()` is called with
`candidate.modelName()`.

---

## Scenario: Buffered stats/log flush

### 1. Scope / Trigger

- Trigger: relay observability writes moved from per-request direct DB writes
  to in-memory buffering plus scheduled flush.
- Applies to `RelayMetricsRecorder`, `task/*FlushTask`, `StatsAccumulator`,
  `LogBuffer`, `StatsRepository`, and `LogRepository`.

### 2. Signatures

```java
@Configuration
@EnableScheduling
public class TaskConfiguration {}

public class StatsAccumulator {
    public void record(LocalDateTime recordedAt, StatsMetrics delta, int apiKeyId, int channelId)
    public Snapshot drain()
}

public class LogBuffer {
    public void append(RelayLog log)
    public List<RelayLog> drainAll()
}

public class StatsFlushTask {
    @Scheduled(fixedRate = 30_000L)
    public void scheduledFlush()
    public void flushOnce()
    @PreDestroy public void flushOnShutdown()
}

public class LogFlushTask {
    @Scheduled(fixedRate = 30_000L)
    public void scheduledFlush()
    public void flushOnce()
    @PreDestroy public void flushOnShutdown()
}

public class LogRepository {
    public void saveAll(List<RelayLog> logs)
}
```

### 3. Contracts

- Flush interval is MVP-hardcoded to `30_000ms`; do not read it from
  `setting` table in this phase.
- Time buckets are decided **when recording happens**, not when flushing
  happens. `StatsAccumulator.record(...)` must receive the captured
  `recordedAt` from `RelayMetricsRecorder`.
- `stats_total`, `stats_daily`, `stats_hourly`, `stats_apikey`, and
  `stats_channel` still persist through the existing accumulative
  `StatsRepository.update*()` UPSERT methods.
- `relay_log` batch persistence uses `LogRepository.saveAll(List<RelayLog>)`
  and must preserve the same column mapping as `save(RelayLog)`.
- Read-path behavior stays DB-backed and unchanged: `StatsService` reads via
  `StatsRepository`; `LogService` reads via `LogRepository`.
- Best-effort flush is required only for normal Spring shutdown. Crash,
  process kill, and `kill -9` are out of scope.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|-----------|-------------------|
| `recordedAt == null` or `delta == null` | Ignore record request; do not mutate accumulator |
| `apiKeyId <= 0` | Skip `stats_apikey` bucket update |
| `channelId <= 0` | Skip `stats_channel` bucket update |
| `StatsAccumulator.drain()` returns empty snapshot | `StatsFlushTask.flushOnce()` returns without repository writes |
| `LogBuffer.drainAll()` returns empty list | `LogFlushTask.flushOnce()` returns without repository writes |
| Spring context closes normally | `flushOnShutdown()` performs one best-effort `flushOnce()` |

### 5. Good / Base / Bad Cases

- Good: one relay request records usage at `23:59:59`, flush happens after
  midnight, and the stats still land in the previous day's/hour's bucket.
- Base: multiple requests in the same hour merge in-memory, then one flush
  writes one row update per bucket plus one batched `relay_log` insert.
- Bad: `LocalDate.now()` / `LocalDateTime.now()` is called inside flush logic
  to decide daily/hourly bucket, causing cross-day or cross-hour mis-bucketing.

### 6. Tests Required

- Unit: `StatsAccumulatorTests`
  - same bucket merges correctly
  - cross-day/cross-hour recording creates separate buckets
  - `drain()` clears in-memory state
- Unit: `LogBufferTests`
  - append order is preserved
  - second `drainAll()` after flush is empty
- Unit: `StatsFlushTaskTests`
  - `@Scheduled.fixedRate == 30_000`
  - `flushOnce()` calls existing `StatsRepository.update*()` methods
  - second flush does not re-write drained data
- Unit: `LogFlushTaskTests`
  - `@Scheduled.fixedRate == 30_000`
  - `flushOnce()` writes a single `saveAll(...)` batch
  - second flush does not re-write drained data
- Integration: `RelayObservabilityIT`
  - relay request completes
  - test explicitly calls `statsFlushTask.flushOnce()` and
    `logFlushTask.flushOnce()` before DB assertions
  - all 5 stats tables plus `relay_log` match expected values

### 7. Wrong vs Correct

#### Wrong

```java
public void flushOnce() {
    String today = LocalDate.now().toString();
    int hour = LocalDateTime.now().getHour();
    statsRepository.updateDaily(today, delta);
    statsRepository.updateHourly(hour, today, delta);
}
```

#### Correct

```java
LocalDateTime recordedAt = LocalDateTime.now();
statsAccumulator.record(recordedAt, statsDelta, apiKeyId, channelId);

public void flushOnce() {
    StatsAccumulator.Snapshot snapshot = statsAccumulator.drain();
    snapshot.daily().forEach(statsRepository::updateDaily);
    snapshot.hourly().forEach((bucket, metrics) ->
        statsRepository.updateHourly(bucket.hour(), bucket.date(), metrics));
}
```
