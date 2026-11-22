# Implement: Full-flow observability consistency test

## Checklist

- [ ] 1. Create `RelayObservabilityIT.java` with test infrastructure
- [ ] 2. Implement TC1: successfulRelayPersistsAllStatsAndLog
- [ ] 3. Implement TC2: failedRelayPersistsFailureStatsAndLog
- [ ] 4. Implement TC3: fullExhaustionPersistsMultiAttemptLog
- [ ] 5. Implement TC4: costCalculationMatchesExpectedValues
- [ ] 6. Run `mvn -pl java -am clean test` — all 109 existing + 4 new tests pass
- [ ] 7. Run `mvn -pl java -am verify` — full verification gate

## Implementation Order

The tasks run sequentially since each test builds on the same
infrastructure.

### Step 1: Test infrastructure

File: `java/src/test/java/.../relay/it/RelayObservabilityIT.java`

Infrastructure:
- `@SpringBootTest(RANDOM_PORT)` with `octobus.auth.jwt-secret=test-secret`
- `@AutoConfigureWebTestClient`
- Temp DB via `DynamicPropertySource` (same pattern as `RelayEndToEndIT`)
- `StubUpstream` as `@BeforeAll/@AfterAll` lifecycle
- `JdbcTemplate`, `WebTestClient`, `ObjectMapper` injection
- `PriceLookup` injection (need `refresh()` after seeding model table)
- `@BeforeEach`: clean DB tables, seed model prices with key matching
  `group_items.model_name`, call `priceLookup.refresh()`, seed relay test data
- Helper: `post(uri, apiKey, body)` returning `ResponseSpec`
- Helper: `bodyFrom(ResponseSpec)` returning body string

DB query helpers (all return domain objects via RowMapper):
- `queryTotal()` → `StatsTotal`
- `queryToday()` → `StatsDaily`
- `queryHourly()` → `StatsHourly`
- `queryAPIKey(int apiKeyId)` → `StatsAPIKey`
- `queryChannel(int channelId)` → `StatsChannel`
- `queryLatestLog()` → `RelayLog`

### Step 2: TC1 — Successful relay

```java
    // Assert relay_log
    RelayLog log = queryLatestLog();
    assertThat(log.requestModelName()).isEqualTo("gpt-4o");
    // actualModelName = candidate.modelName() (= group_items.model_name)
    assertThat(log.actualModelName()).isEqualTo("gpt-4o");
    assertThat(log.inputTokens()).isEqualTo(10);
    assertThat(log.outputTokens()).isEqualTo(5);
    assertThat(log.cacheHitTokens()).isEqualTo(2);
    assertThat(log.cost()).isCloseTo(19.0e-6, within(1e-10));
    assertThat(log.usageRecorded()).isTrue();
    assertThat(log.error()).isEmpty();
    assertThat(log.totalAttempts()).isEqualTo(1);

    // Assert attempts (already deserialized by RelayLog record)
    assertThat(log.attempts()).hasSize(1);
    assertThat(log.attempts().get(0).status()).isEqualTo("success");
```

### Step 3: TC2 — Failed relay

Same structure as TC1 but stub returns 500. Key assertions:
- `request_success=0`, `request_failed=1`
- `usage_recorded=false`
- `error` non-empty containing "500" or similar
- tokens and cost are zero

### Step 4: TC3 — Full exhaustion

2 channels, both return 500. Key assertions:
- HTTP response body has `code=502`
- `relay_log.total_attempts=2`
- attempts JSON array length = 2
- `request_failed=1` (one request, not per-channel)
- tokens and cost are zero

### Step 5: TC4 — Cost precision

Use SUCCESS_CH1 with known prompt_tokens_details (cached=2, write_cached=1)
and seeded model prices (input=1.0, output=2.0, cache_read=0.5, cache_write=1.0).

Formula:
- nonCached = 10 - 2 - 1 = 7
- inputCost = (2 * 0.5 + 1 * 1.0 + 7 * 1.0) * 1e-6 = 9.0e-6
- outputCost = 5 * 2.0 * 1e-6 = 10.0e-6
- totalCost = 19.0e-6

## Validation Commands

After each step:
```
mvn -pl java -am clean test -q
```

After all steps complete:
```
mvn -pl java -am verify
```

## Rollback

If any step fails:
1. Read the test failure message to identify the assertion
2. Fix the assertion or the query helper
3. Re-run `mvn -pl java -am test` with only the failing test:
   `mvn -pl java -am test -Dtest=RelayObservabilityIT`
4. If the fix requires changes to domain models or repositories,
   document the deviation in prd.md before continuing
