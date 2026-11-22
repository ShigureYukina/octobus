# Implement Java rewrite milestone 3

## Purpose

This document turns the M3 `prd.md` and `design.md` into an ordered
execution plan. It is the contract the implementing sub-agents will
follow. Each child task lists its intent, the artifacts it must
produce, and the validation gate that lets the next task begin.

## Ordered Checklist

- [ ] T1. `java-relay-core` — handler factory, three-stage lifecycle, route
  registration, 501 on stream paths.
- [ ] T2. `java-relay-adapter-spi` — unified request / response types,
  transformer interfaces, registry, guarded non-target formats.
- [ ] T3. `java-relay-balancer` — four balancer modes plus
  `RelayCandidateIterator` with skip / record-attempt support.
- [ ] T4. `java-relay-circuit-breaker` — in-process breaker with the
  Closed / Open / HalfOpen state machine and exponential cooldown.
- [ ] T5. `java-relay-session` — `StickyStore` interface plus the
  in-memory implementation, TTL-aware read / write.
- [ ] T6. `java-relay-openai-chat` — OpenAI Chat inbound + OpenAI Chat
  outbound wired through the SPI, with the OpenAI-compatible provider
  client.
- [ ] T7. `java-relay-metrics` — usage parsing, three-tier cost
  calculation, seven statistics updates, `RelayLog` persistence with
  attempts chain.
- [ ] T8. `java-relay-integration` — embedded stub upstream, end-to-end
  WebFlux tests, full Maven verify green.

## Per-Task Intent

### T1. `java-relay-core`

Intent: stand up the relay skeleton before any adapter exists. After
T1 the application still starts, the `/v1/*` routes are registered,
each one delegates to a `RelayHandler` that parses the body, builds a
`RelayRun`, and currently fails with a controlled "no adapter" error.

Artifacts:
- `relay/RelayHandler.java` — WebFlux handler factory.
- `relay/RelayRun.java` — request-level state holder.
- `relay/RelayAttempt.java` — single candidate attempt (scaffold).
- `relay/RelayRouter.java` (or equivalent) — registers the seven
  `/v1/*` routes behind `RequestAuthenticationFilter`.
- Unit test: a request to `/v1/chat/completions` with a stubbed
  `RelayRun` returns the expected `ApiResponse` envelope.

Validation gate: `mvn -pl java -am test` passes; the application
boots; a manual `curl` to `/v1/chat/completions` returns the expected
"adapter not wired" error body, not a stack trace.

### T2. `java-relay-adapter-spi`

Intent: lock down the internal contract that every adapter must obey.
After T2 the SPI compiles, the registry returns the OpenAI Chat
inbound / outbound pair, and the other formats return a guarded
"not supported" transformer that produces a predictable error.

Artifacts:
- `relay/spi/UnifiedRequest.java`, `UnifiedResponse.java`,
  `UnifiedUsage.java`, `UnifiedMessage.java`, `UnifiedTool.java`.
- `relay/spi/InboundTransformer.java`, `OutboundTransformer.java`.
- `relay/spi/TransformerRegistry.java` with the matrix from
  `.trellis/spec/java/relay-protocol-adapter.md`.
- A test that asserts the OpenAI Chat cell resolves and that the
  OpenAI Responses cell returns the guard.

Validation gate: `mvn -pl java -am test` passes; the registry
test covers every cell of the inbound-by-format matrix.

### T3. `java-relay-balancer`

Intent: produce the four balancer modes and the candidate iterator
that `RelayRun` will drive. The iterator must accept a
`StickyStore` and a `CircuitBreaker` as constructor parameters so
tests can substitute fakes.

Artifacts:
- `relay/balancer/Balancer.java` interface.
- `relay/balancer/RoundRobinBalancer.java`,
  `RandomBalancer.java`, `FailoverBalancer.java`,
  `WeightedBalancer.java`.
- `relay/balancer/RelayCandidateIterator.java` with `next()`,
  `current()`, `skip(reason)`, `recordAttempt(...)`,
  `attempts()`.
- Tests for each balancer mode and for the iterator's
  ordering, sticky priority, and attempts chain shape.

Validation gate: `mvn -pl java -am test` passes for the
`relay.balancer` package.

### T4. `java-relay-circuit-breaker`

Intent: ship the breaker that decides which attempts are allowed.
No relay code outside this task should touch the breaker state.

Artifacts:
- `relay/breaker/CircuitBreaker.java` interface.
- `relay/breaker/InMemoryCircuitBreaker.java` keyed by
  `channelId:channelKeyId:modelName`.
- `relay/breaker/CircuitState.java` enum.
- `relay/breaker/CircuitDecision.java` (allow / open / halfOpen).
- Tests covering: threshold trip, cooldown elapsed -> half-open,
  probe success -> closed, probe failure -> open with doubled
  cooldown, no state leak between keys.
- Extend `RelayProperties` with `maxCooldown` and `probeTimeout` if
  the Go defaults are needed; otherwise document the deviation.

Validation gate: `mvn -pl java -am test` passes for the
`relay.breaker` package.

### T5. `java-relay-session`

Intent: ship the sticky store. This is small but it must be
complete before the iterator can be wired end-to-end.

Artifacts:
- `relay/session/StickyStore.java` interface.
- `relay/session/InMemoryStickyStore.java`.
- `relay/session/StickyEntry.java` value type
  (`channelId`, `keyId`, `expiresAt`).
- Tests: write then read within TTL, read after expiry returns
  empty, disabled when TTL is zero.

Validation gate: `mvn -pl java -am test` passes for the
`relay.session` package.

### T6. `java-relay-openai-chat`

Intent: produce the first fully working inbound / outbound pair.
After T6 a real OpenAI Chat request against a configured OpenAI
provider must round-trip successfully, with usage recorded.

Artifacts:
- `relay/adapter/openai/OpenAIChatInboundTransformer.java`.
- `relay/adapter/openai/OpenAIChatOutboundTransformer.java`.
- `relay/provider/openai/OpenAICompatibleProviderClient.java`.
- `relay/provider/ProviderHttpClient.java` (shared interface) if
  multiple providers will reuse it later.
- Unit tests that parse a representative Chat Completions body and
  produce a valid `UnifiedRequest`, and that serialize a
  `UnifiedRequest` back to the OpenAI Chat shape.

Validation gate: an integration test using a stubbed upstream
passes the full candidate / response flow described in T8.

### T7. `java-relay-metrics`

Intent: turn a successful or failed attempt into the seven
statistics updates and a `RelayLog` row. This task is the
integration point between relay and persistence.

Artifacts:
- `relay/metrics/UsageParser.java` for upstream `usage` blocks.
- `relay/metrics/CostCalculator.java` with the three-tier input
  cost + output cost formula.
- `relay/metrics/RelayMetricsRecorder.java` (or equivalent) that
  fans out to global / hourly / daily / apikey / channel updates
  and writes the `RelayLog` row.
- Tests using a fake `StatsRepository` and a fake `RelayLogRepository`
  to assert the seven updates and the `RelayLog` payload.

Validation gate: `mvn -pl java -am test` passes for the
`relay.metrics` package; a stubbed-upstream integration test
asserts that a successful and a failed attempt each produce the
expected statistics and log row.

### T8. `java-relay-integration`

Intent: prove the milestone end-to-end. This task is the only one
allowed to introduce the embedded stub upstream; it is the gate for
marking the M3 task complete.

Artifacts:
- `relay/it/StubUpstream.java` — a controllable mock provider
  exposing success / failure / 429 / 500 / slow responses.
- `relay/it/RelayEndToEndIT.java` — covers: success, single
  candidate failure -> next candidate succeeds, full candidate
  exhaustion, sticky reuse across two requests, breaker
  open -> half-open -> closed, missing / invalid API key, stream
  path returns 501.
- A `RelayProperties` test profile that points the relay at the
  stub upstream and at a temp data directory.

Validation gate: `mvn -pl java -am verify` is green; the M3
acceptance criteria in `prd.md` are all checked.

## Validation Gates

- Every child task ends with a green `mvn -pl java -am test`.
- T1 also requires the application to boot.
- T8 is the only task that must run `mvn -pl java -am verify` and
  pass.
- Before any child task starts, the implementing agent must run
  `python ./.trellis/scripts/task.py current` and confirm the
  active task path matches the child task it was dispatched to.

## Rollback Points

- T1 rollback: revert route registration; the application still
  boots. No other M3 task has been merged.
- T2 rollback: revert the SPI; nothing in the relay runtime
  references the registry yet.
- T3 / T4 / T5 rollback: each lives in its own subpackage; the
  `RelayRun` falls back to the T1 stub error.
- T6 rollback: revert the OpenAI Chat pair; the SPI still resolves
  the cell but the handler reports "adapter not wired" exactly as
  in T1.
- T7 rollback: revert the metrics recorder; usage and logs are
  skipped, but successful responses still return to the client.
  This is acceptable as a temporary degradation only if the
  task is reverted before T8 begins.
- T8 rollback: revert the test module. The runtime is unaffected
  because the stub upstream is test-scope only.

If two consecutive child tasks fail their validation gates, stop
adding patches and re-open `design.md` to update the affected
sections before continuing.
