# Full-flow observability consistency test

## Goal

Verify that every relay request produces correct database side-effects. The
existing M3 tests validate HTTP responses and relay logic in isolation, but
never query the database after a relay operation to confirm the 7 statistics
updates and the `RelayLog` row were written correctly.

This task fills that gap by adding an end-to-end test that asserts the full
causal chain:

```
relay request → RelayMetricsRecorder.record() →
  5 stats tables (total / hourly / daily / apikey / channel) +
  1 relay_log row +
  attempts chain JSON →
  query database → assert values match expectations
```

## Dependency Notes

- Depends on M3 `java-relay-metrics` and `java-relay-integration` — the
  metrics recorder and stub upstream must exist.
- Must be implemented on the current branch `feat/java-m1-bootstrap` where
  M1/M2/M3 code already lives.

## Requirements

- Extend the existing e2e test pattern (`RelayEndToEndIT`) with a new
  test class that queries the database after each relay operation.
- Use the existing `StubUpstream`, `JdbcTemplate`, `@SpringBootTest` config,
  and seed helpers — do not introduce new infrastructure.
- The price lookup (`PriceLookup`) reads from the `model` table at startup;
  seed a known model price to enable cost assertion.

## Acceptance Criteria

- [ ] A successful relay request produces the correct 5 stats table rows
      with matched token counts and calculated costs.
- [ ] A successful relay request persists a `RelayLog` row with correct
      request/response/attempts data and `usage_recorded=true`.
- [ ] A failed relay request (500 upstream) persists `request_failed=1`,
      `usage_recorded=false`, and a non-empty `error` field.
- [ ] A full candidate exhaustion (2 channels, both 500) persists a
      `RelayLog` row with `attempts` JSON array of length 2.
- [ ] Cost calculation in the database matches the expected
      three-tier formula (cached read × cache_read_price + cached write ×
      cache_write_price + fresh × input_price) × 1e-6.
- [ ] `mvn -pl java -am test` passes with all new tests green.

## Notes

- Keep the test class focused on observability (stats + log). Do not
  re-test relay logic (balancer, breaker, session) that `RelayEndToEndIT`
  already covers.
- Use approximate equality (`assertThat(x).isCloseTo(expected, within(tol))`)
  for floating-point cost assertions.
- Follow the same `JdbcTemplate` query style already used in
  `ModelLogStatsIntegrationTests` and `RelayEndToEndIT`.
