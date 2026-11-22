# Testing Strategy

> Testing scope for the Java rewrite.

---

## Rules

- Cover pure logic first with unit tests.
- Add integration tests for API contracts and relay flows.
- Add stream fixtures for adapter behavior.
- Keep migration and persistence tests isolated from external services.

---

## Minimum Coverage

- Config validation
- JWT and API key flows
- Balancer and circuit breaker behavior
- Protocol adapter transformations
- Relay stream aggregation
- CRUD endpoints
- End-to-end relay flows

---

## Integration Test Patterns

### Stub Upstream

Use `com.sun.net.httpserver.HttpServer` (JDK built-in) for stub upstreams in end-to-end relay tests. It avoids adding test-only dependencies and integrates cleanly with `@DynamicPropertySource` for random port wiring.

Pattern:
```java
// StubUpstream starts on random port, enqueues responses per test scenario
// Test seeds DB via JdbcTemplate, drives routes via WebTestClient
// @DynamicPropertySource points the DB to a temp SQLite file
```

### Observability Integration Test

After relay operations, verify database side-effects include:

- **5 stats tables**: `stats_total` (id=1), `stats_daily` (date=today),
  `stats_hourly` (hour, date), `stats_apikey` (api_key_id),
  `stats_channel` (channel_id) — each with matching token/cost/metrics
- **relay_log**: request/response metadata, tokens, cost, `usage_recorded`,
  error, `attempts` (JSON → `List<ChannelAttempt>`), `total_attempts`
- **attempts chain**: `ChannelAttempt.status` is `"failed"` (not `"failure"`)

Key infrastructure details:

```java
@BeforeEach
void setUp() {
    // 1. Seed model prices under group_items.model_name key
    jdbc.update("INSERT OR IGNORE INTO model ...", "gpt-4o", ...);
    // 2. PriceLookup caches on construction; refresh after seeding
    priceLookup.refresh();
}

// Capture date/hour before relay call (avoids midnight-boundary flakiness)
String today = LocalDate.now().toString();
int hour = LocalDateTime.now().getHour();

// Query helpers return domain records via RowMapper
private StatsTotal queryTotal() {
    return jdbc.queryForObject("SELECT ... FROM stats_total WHERE id = 1",
        (rs, rowNum) -> new StatsTotal(rs.getInt("id"), ...));
}

// relay_log.attempts is TEXT JSON in SQLite, deserialize with ObjectMapper
private RelayLog queryLatestLog() {
    return jdbc.queryForObject("SELECT ... FROM relay_log ORDER BY id DESC LIMIT 1",
        (rs, rowNum) -> {
            String json = rs.getString("attempts");
            List<ChannelAttempt> attempts = mapper.readValue(json, ATTEMPTS_TYPE);
            return new RelayLog(..., attempts, ...);
        });
}

// usage_recorded is INTEGER in SQLite (0/1)
rs.getInt("usage_recorded") != 0  // convert to boolean
```

When observability writes are buffered, tests must **flush explicitly before
asserting DB state**:

```java
@Autowired private StatsFlushTask statsFlushTask;
@Autowired private LogFlushTask logFlushTask;

private void flushBuffers() {
    statsFlushTask.flushOnce();
    logFlushTask.flushOnce();
}
```

Rules:

- Call `flushBuffers()` after the relay request and before any stats/log query.
- Do **not** sleep or wait for scheduler ticks in tests.
- Keep date/hour capture near the relay request to avoid midnight-boundary
  flakiness.

### In-Memory State Isolation

`@Nested` test classes in `@SpringBootTest` share a single Spring context.
In-memory state (`CircuitBreaker`, `StickyStore`) **persists across nested classes** if not cleared.

Always inject and reset in-memory state in `@BeforeEach`:
```java
@Autowired private io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker circuitBreaker;
@Autowired private StickyStore stickyStore;

@BeforeEach
void cleanDb() {
    circuitBreaker.resetAll();
    stickyStore.clear();
    jdbc.update("DELETE FROM ...");
}
```

Both `CircuitBreaker` and `StickyStore` expose `resetAll()` / `clear()` default methods
for test isolation. Implementations that hold concurrent maps must override these to clear their internal state.

---

## Scenario: Dual-database and backup compatibility coverage

### 1. Scope / Trigger
- Trigger: Java MVP now supports SQLite + MySQL config paths, full backup import/export, and Gemini relay adaptation, which introduced new compatibility-test lanes beyond the original SQLite-only suite.

### 2. Signatures
- `MysqlCompatibilityIntegrationTests`
- `StaticFrontendIntegrationTests`
- `BackupIntegrationTests`
- `GeminiContentsOutboundTransformerTests`
- `RelayGeminiContentsIT`

### 3. Contracts
- Keep existing SQLite integration tests intact; do not rewrite the whole suite into MySQL mode
- Add a separate MySQL-compatibility lane using H2 `MODE=MySQL` for migration/config/upsert coverage
- Use temp SQLite database files per test class when Flyway checksum drift or local `./data/octobus.db` pollution could mask the intended assertion
- Backup tests must cover both multipart upload and JSON-body compatibility paths
- Gemini relay tests must assert both provider-request shape and client-facing response adaptation

### 4. Validation & Error Matrix
- MySQL compatibility test loading both SQLite and MySQL Flyway trees -> fail the test; fix location switching first
- reserved identifiers (`groups`, `key`, `value`, `date`, `hour`) unquoted in MySQL/H2 mode -> startup or query failure
- backup rollback test leaves changed rows behind -> test failure
- Gemini streaming test emits chunks but misses `[DONE]` -> test failure

### 5. Good/Base/Bad Cases
- Good: SQLite suite stays green, MySQL compatibility suite passes separately, and backup/Gemini tests cover new cross-layer behavior
- Base: targeted transformer tests validate serialization/parsing without booting full app context
- Bad: replace all SQLite tests with H2 MySQL mode and lose coverage of the real default deployment path

### 6. Tests Required
- `MysqlCompatibilityIntegrationTests`
  - datasource type selection
  - MySQL Flyway location selection
  - settings upsert
  - stats accumulation
  - reserved-word-safe group CRUD
- `BackupIntegrationTests`
  - export/import/rollback assertions
- `StaticFrontendIntegrationTests`
  - root, SPA fallback, excluded API/relay/health paths
- `RelayGeminiContentsIT` + `RelayStreamingIT`
  - Gemini SSE conversion and OpenAI streaming guard regression

### 7. Wrong vs Correct
#### Wrong
- use one reused local DB file in config validation tests and let checksum drift hide the actual failure under test
- treat H2 MySQL mode as a drop-in replacement for every persistence test

#### Correct
- isolate config-validation databases and keep SQLite as the default regression lane
- add focused MySQL compatibility tests for the new dialect-specific paths only
