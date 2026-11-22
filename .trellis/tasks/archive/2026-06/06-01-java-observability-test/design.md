# Design: Full-flow observability consistency test

## Technical Decisions

### 1. New test class, not modification of existing tests

Create `RelayObservabilityIT.java` as a standalone test class rather than
adding assertions to `RelayEndToEndIT`. This keeps test responsibility
clear (`RelayEndToEndIT` = relay logic, `RelayObservabilityIT` = DB
side-effects) and avoids bloating the existing file.

### 2. Infrastructure reuse

The new test class reuses all existing infrastructure:

| Component | Source | Reason |
|-----------|--------|--------|
| `StubUpstream` | `relay/it/StubUpstream.java` | Embedded HTTP stub with deterministic response queue |
| `@SpringBootTest(RANDOM_PORT)` | Same pattern as `RelayEndToEndIT` | Spins up real WebFlux with routes |
| Temp SQLite DB | Same `DynamicPropertySource` pattern | Isolated database per test run |
| `JdbcTemplate` | Injected via `@Autowired` | Direct DB queries for verification |
| `ObjectMapper` | Injected via `@Autowired` | Deserialize attempts JSON in RowMapper |
| Seed helpers | Inline `jdbc.update(...)` calls | Same helpers used in `RelayEndToEndIT` |

### 3. Model price seeding for cost verification

`PriceLookup` caches model prices from the `model` table on construction
(before any `@BeforeEach` runs). To verify cost calculations, seed a
model row **and** call `PriceLookup.refresh()`:

```java
@BeforeEach
void setUp() {
    jdbc.update("""
        INSERT OR IGNORE INTO model (name, input, output, cache_read, cache_write)
        VALUES (?, ?, ?, ?, ?)
        """, "gpt-4o", 1.0, 2.0, 0.5, 1.0);
    priceLookup.refresh();  // reload DB-visible prices into cache
}
```

The price key **must** match `group_items.model_name` because
`RelayRun` passes `candidate.modelName()` to `PriceLookup.priceFor()`,
not the upstream response model name.

The test verifies:
- `input_cost` = (2 × 0.5 + 1 × 1.0 + 7 × 1.0) × 1e-6 = 9.0e-6
- `output_cost` = 5 × 2.0 × 1e-6 = 10.0e-6
- `total_cost` = 19.0e-6

### 4. DB query pattern

Use direct `JdbcTemplate.query()` rather than repository classes. This
keeps the test self-contained and avoids depending on repository
abstractions. Example:

```java
private StatsTotal queryTotal() {
    return jdbc.queryForObject(
        "SELECT id, input_token, output_token, input_cost, output_cost, "
        + "wait_time, request_success, request_failed, cache_hit_token "
        + "FROM stats_total WHERE id = 1",
        (rs, rowNum) -> new StatsTotal(...));
}
```

### 5. Test data constants

Reuse the `SUCCESS_CH1` JSON constant from `RelayEndToEndIT` which has
known token counts (prompt=10, completion=5, cached=2, write_cached=1).

### 6. Attempts chain verification

The `relay_log.attempts` column stores a JSON array of `ChannelAttempt`
objects. The `RelayLog` domain record deserializes it into
`List<ChannelAttempt}` directly via Jackson. The test asserts:

- List length matches expected attempt count
- Each entry has correct `status`, `channelId` fields

The relay code (`RelayRun.java:234`) records attempt status as
`"failed"` (not `"failure"`).

## Test Structure

```
RelayObservabilityIT
├── @BeforeEach: seed model prices, seed API key/channel/group
│
├── TC1: successfulRelayPersistsAllStatsAndLog
│   ├── Stub returns 200 + SUCCESS_CH1 (prompt=10, completion=5, cache=2)
│   ├── POST /v1/chat/completions
│   ├── Assert stats_total: input_token=10, output_token=5, request_success=1
│   ├── Assert stats_hourly: same increments
│   ├── Assert stats_daily: same increments
│   ├── Assert stats_apikey: same increments
│   ├── Assert stats_channel: same increments
│   ├── Assert relay_log: model, tokens, cost, usage_recorded=true, error=""
│   └── Assert attempts JSON: length=1, status=success
│
├── TC2: failedRelayPersistsFailureStatsAndLog
│   ├── Stub returns 500
│   ├── Assert stats_total: request_failed=1, request_success=0
│   ├── Assert relay_log: usage_recorded=false, error non-empty
│   └── Assert attempts: length=1, status="failed"
│
├── TC3: fullExhaustionPersistsMultiAttemptLog
│   ├── 2 channels, both return 500
│   ├── Assert 502 response
│   ├── Assert relay_log: total_attempts=2, attempts JSON length=2
│   └── Assert stats: request_failed=1 (one request, not per-attempt)
│
└── TC4: costCalculationMatchesExpectedValues
    ├── Stub returns 200 + usage with specific token counts
    ├── Assert input_cost = expected formula result
    ├── Assert output_cost = expected formula result
    └── Assert relay_log.cost = total_cost
```

## Key Assertions

### Stats table columns

| Table | Key columns |
|-------|-------------|
| `stats_total` | id=1, input_token, output_token, input_cost, output_cost, wait_time, request_success, request_failed, cache_hit_token |
| `stats_daily` | date=today, same metric columns |
| `stats_hourly` | hour=current_hour, date=today, same metric columns |
| `stats_apikey` | api_key_id=N, same metric columns |
| `stats_channel` | channel_id=N, same metric columns |

### RelayLog fields (subset)

| Field | Success expectation | Failure expectation |
|-------|-------------------|-------------------|
| `request_model_name` | "gpt-4o" | "gpt-4o" |
| `actual_model_name` | "gpt-4o" (candidate modelName) | "gpt-4o" (candidate modelName) |
| `input_tokens` | 10 | 0 |
| `output_tokens` | 5 | 0 |
| `cost` | ~19.0e-6 | 0.0 |
| `usage_recorded` | 1 (true) | 0 (false) |
| `error` | "" | non-empty |
| `total_attempts` | 1 | 1 (or 2 for exhaustion) |
| `attempts` (list) | `[{status:"success",...}]` | `[{status:"failed",...}]` |
| `cache_hit_tokens` | 2 | 0 |

Note: `actual_model_name` is set from `candidate.modelName()` (i.e.
`group_items.model_name`), not from the upstream response body's
`model` field.

## Scope Boundaries

- **In scope**: stats table reads, relay_log reads, attempts JSON parsing
- **Not in scope**: model list, log list filtering, stats HTTP endpoints
  (already tested by `ModelLogStatsIntegrationTests`)
- **Not in scope**: per-key circuit breaker state (already tested by
  `InMemoryCircuitBreakerTests`)
- **Not in scope**: streaming, tool calls, multimodal (Milestone 4)
