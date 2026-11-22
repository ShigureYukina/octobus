# Design Java rewrite milestone 3

## Objective

Design the non-streaming relay main path for the Java rewrite. The path
covers inbound HTTP handling, group resolution, candidate selection, single
attempt execution, retry orchestration, and usage / audit persistence.

The output of this milestone is a working non-streaming relay that mirrors
the Go service's behavior on the same routes, and that leaves clean
extension points for Milestone 4 streaming and adapter additions.

## Child Task Design

The work is split into 8 child tasks. Each is independently verifiable
and produces a testable artifact before the next one starts.

### 1. `java-relay-core`

- Build the `RelayHandler` factory and the three-stage lifecycle types:
  `RelayHandler` (WebFlux handler bound to an inbound format),
  `RelayRun` (request-level state), `RelayAttempt` (single candidate
  attempt).
- Register all `/v1/*` routes on top of the existing
  `RequestAuthenticationFilter` from `security/`.
- Stream endpoints must be wired but must respond with a 501 "streaming not
  yet supported" until Milestone 4 lands.
- Return failures through the shared `ApiResponse` / `GlobalExceptionHandler`
  pipeline.
- Provides the skeleton that every other child task plugs into.

### 2. `java-relay-adapter-spi`

- Define the unified internal request / response model that every
  inbound format normalizes into and every outbound format dispatches from.
- Define `InboundTransformer` and `OutboundTransformer` interfaces plus the
  registry that the factory consults.
- Wire the inbound / outbound matrix from
  `.trellis/spec/java/relay-protocol-adapter.md`. For Milestone 3 the
  only fully implemented cell is OpenAI Chat -> OpenAI Chat. Other
  inbound formats get a thin "not yet supported" guard that returns
  a predictable error.
- No HTTP I/O happens inside this task; the SPI is pure transformation
  plus metadata.

### 3. `java-relay-openai-chat`

- Implement the OpenAI Chat inbound transformer (parse request body, build
  the unified request, including tool definitions / system message / model
  mapping).
- Implement the OpenAI-compatible outbound transformer (serialize the
  unified request back to OpenAI Chat JSON, build provider URL and headers,
  and convert the upstream response back to the unified response).
- Use a small reactive `WebClient` wrapper kept inside `relay/`. The
  provider client must not leak into `domain/` per the architecture spec.
- This is the single end-to-end path that proves the framework.

### 4. `java-relay-balancer`

- Implement the four balancer modes exactly as in the Go version:
  RoundRobin (atomic counter), Random (shuffle), Failover (priority
  ascending), Weighted (score by `weight / totalWeight`).
- Implement the `RelayCandidateIterator`: holds the candidate list,
  resolves sticky priority, exposes `next()` / `current()` /
  `skip()` / `recordAttempt()`, and produces the attempts chain.
- Stateless between requests, except for the sticky store and the
  circuit breaker state handled by their own tasks.

### 5. `java-relay-circuit-breaker`

- Implement the in-process breaker keyed by
  `channelId:channelKeyId:modelName` with the same state machine:
  Closed -> Open (after `failureThreshold` consecutive failures),
  Open -> HalfOpen (after exponential cooldown), HalfOpen -> Closed on
  probe success or -> Open with doubled cooldown on probe failure.
- The breaker exposes `allow(channelId, keyId, model)` and
  `recordSuccess(channelId, keyId, model)` /
  `recordFailure(channelId, keyId, model)`.
- Configuration is read from the existing `RelayProperties.circuitBreaker`
  block; extend that record with a max cooldown and a probe timeout if
  the Go defaults need to be preserved.
- State is best-effort: lost on restart, acceptable for Milestone 3.

### 6. `java-relay-session`

- Implement the sticky store: key `(apiKeyId, requestModel)`, value
  `(channelId, keyId, timestamp)`.
- TTL is taken from `group.sessionKeepTime`; 0 means sticky is disabled
  and the iterator falls back to pure balancer output.
- The store is in-process for Milestone 3. The interface must allow a
  later implementation backed by Redis or the database without changes
  to the iterator.

### 7. `java-relay-metrics`

- Parse the upstream `usage` block (input / output / cached read /
  cached write) from the unified response.
- Compute the three-tier input cost plus output cost, using the
  same `price.GetLLMPrice` style lookup. Pricing source of truth
  remains the JSON price map; the Java rewrite reads it on startup.
- Persist the seven statistics updates: global total, hourly, daily,
  per-API-key, per-channel usage and cost, and the `RelayLog` row.
- The `RelayLog` row carries the attempts chain, request / response
  bodies, error summary, request model, actual upstream model,
  input / output / cache-hit token counts, and total cost.
- This task depends on the persistence layer in Milestone 1; no new
  schema changes are expected.

### 8. `java-relay-integration`

- Add an embedded stub upstream that records requests and returns
  controllable success / failure / 429 / 500 responses.
- End-to-end WebFlux tests for: success path, single-key 429 retry,
  full candidate exhaustion, sticky session reuse, circuit breaker
  open -> half-open -> closed, and missing / invalid API key.
- A Maven verify run must pass before this task is marked complete.

### Ordering

The dependency order is fixed:

```
1. java-relay-core            (skeleton + routes)
2. java-relay-adapter-spi     (transformer interfaces + registry)
3. java-relay-balancer        (depends on spi for the unified request)
4. java-relay-circuit-breaker (independent, used by iterator)
5. java-relay-session         (independent, used by iterator)
6. java-relay-openai-chat     (depends on spi; first working path)
7. java-relay-metrics         (depends on core + spi; integrates with cost map)
8. java-relay-integration     (depends on all of the above)
```

`java-relay-circuit-breaker` and `java-relay-session` can be developed
in parallel with `java-relay-balancer`; they only need to agree on the
key shape.

## Technical Decisions

### 1. Three-stage lifecycle mirrors Go's structure

- `RelayHandler` is a WebFlux handler factory. It knows the inbound
  format, parses the body once, builds a `RelayRun`, and calls `run()`.
- `RelayRun` owns the inbound transformer, the unified request, the
  candidate iterator, the metrics recorder, and the resolved group.
  Its `run()` method loops through candidates and delegates to a
  `RelayAttempt` per candidate.
- `RelayAttempt` owns the outbound transformer, the channel and key
  selection, the circuit breaker check, and the actual HTTP call. Its
  result drives success / failure in the iterator.
- This split keeps the candidate loop testable in isolation, which
  the Go version proved necessary.

### 2. Candidate selection never opens a network connection

- The iterator and balancers are pure functions over `GroupItem` rows.
- The breaker and session stores are queried synchronously before any
  attempt starts. A breaker rejection counts as a "skip" with
  reason `circuit_break`, not as a real failure.
- Only `RelayAttempt.forward()` may open a network connection.

### 3. Single working path in Milestone 3

- The spec lists seven inbound and four outbound formats. M3 only
  requires one fully working cell. We pick OpenAI Chat -> OpenAI Chat
  because it is the highest traffic path and exercises the full SPI
  without dragging in provider-specific quirks.
- Other inbound formats are registered but guarded. Adding Gemini /
  Anthropic / Doubao as outbound targets is tracked in Milestone 4.

### 4. Unified internal request model

- The SPI defines a single `UnifiedRequest` type that carries messages,
  tools, model hint, sampling parameters, and stream flag. Inbound
  transformers produce it; outbound transformers consume it.
- The unified response mirrors it for `usage` and the first / final
  message. Streaming fields are present but unused in M3.
- This matches the "normalize then dispatch" rule from
  `.trellis/spec/java/relay-protocol-adapter.md`.

### 5. Circuit breaker scope

- Key shape: `channelId + ":" + channelKeyId + ":" + modelName`. This
  matches the Go implementation and avoids penalizing a healthy key on
  a channel for another key's failures.
- State lives in a `ConcurrentHashMap` keyed by that string. Per-entry
  state is guarded by a small `ReentrantLock` or by careful use of
  `AtomicReference` for the state field plus `AtomicLong` for counters.
- A small scheduled task is NOT required. Cooldown is checked lazily on
  `allow()`. This keeps the implementation simple and matches Go's
  on-demand semantics.

### 6. Sticky session scope

- In-process `ConcurrentHashMap` keyed by `apiKeyId + ":" + modelName`,
  value `(channelId, keyId, expiresAt)`. Expired entries are removed
  lazily when read.
- Interface is `StickyStore`. The implementation for M3 is
  `InMemoryStickyStore`. Later milestones may add a Redis-backed
  implementation without changing the iterator.

### 7. Cost calculation reuses price source of truth

- The Java rewrite already loads model pricing on startup. The relay
  metrics task calls the same loader to resolve `Input`, `Output`,
  `CacheRead`, `CacheWrite` rates per million tokens.
- Three-tier input cost: `cached * CacheRead + writeCached * CacheWrite +
  (prompt - cached - writeCached) * Input`. Negative fallbacks are
  clamped to zero, matching the Go behavior.

### 8. Failure surface uses shared envelope

- `RelayHandler` returns a Mono of `ResponseEntity<ApiResponse<?>>`.
- The exception types already defined in `common/` are reused:
  `NotFoundException`, `UnauthorizedException`, `RateLimitException`,
  and a new `UpstreamException` for non-2xx upstream responses.
- On full candidate exhaustion, the handler returns
  `502 BAD_GATEWAY` with a payload that lists the number of attempts
  and the last error message, then the `RelayLog` row is written.

### 9. Persistence changes

- No new tables are required for M3. `RelayLog` and `ChannelAttempt`
  already exist from Milestone 1.
- The seven statistics updates go through the existing `Stats` /
  `StatsRepository` surface. If the Go version's update granularity is
  not yet represented, extend the repository, not the controller.

### 10. Provider HTTP client lives in `relay/`

- Per `.trellis/spec/java/architecture.md`, provider-specific HTTP code
  must not live in `domain/`. A `relay/provider/` subpackage owns the
  WebClient configuration and per-provider URL / header conventions.
- A single `OpenAICompatibleProviderClient` covers OpenAI Chat in M3.

## Compatibility Notes

- Endpoint paths and HTTP shapes are unchanged from the Go service. The
  rewrite does not introduce new routes in M3.
- API key auth continues to flow through `RequestAuthenticationFilter`
  from Milestone 1. No new auth surface is added.
- Error envelope shape (`ApiResponse`) is the same as M2.
- Pricing is loaded from the same JSON source; the rate keys are
  unchanged. If the Go service has pricing keys that the Java rewrite
  does not yet load, the loader is extended; no fallback math.
- Stream requests receive a 501 instead of crashing. This is the only
  intentional behavior difference and is documented in the OpenAPI
  spec when one is added in M5.

## Risks

- **Reactive + blocking parts of the Go port.** The Go version mixes
  blocking channels and goroutines freely. The Java version must keep
  the WebFlux call chain non-blocking. The provider client and price
  loader must be reactive or wrapped safely; the breaker and sticky
  store must not block the event loop.
- **Testability of the iterator.** The Go version's iterator is easy to
  test because it is a struct with no globals. The Java version must
  keep the same property: pass in the sticky store and the breaker as
  constructor parameters, not as static singletons.
- **Circuit breaker state cardinality.** The `channelId:keyId:model`
  key shape can produce a large map for busy installations. M3 is
  best-effort; M5 may introduce a bounded LRU. The M3 implementation
  must keep memory bounded by removing entries on a periodic basis.
- **Sticky store on restart.** Restart drops the in-memory sticky map.
  This is acceptable for M3 but must be called out for operators.
- **First-token semantics.** The Go version's first-token timeout is a
  streaming concept. M3 does not implement it. M4 will add it together
  with SSE. Do not backport partial streaming logic into M3.
- **Adapter scope creep.** It is tempting to wire all seven formats in
  M3. Doing so expands the risk surface and dilutes the M3 acceptance
  bar. M3 sticks to one fully working path; the rest are guarded.
