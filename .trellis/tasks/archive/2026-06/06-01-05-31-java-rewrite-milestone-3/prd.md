# Plan Java rewrite milestone 3

## Goal

Implement the non-streaming relay main path for the Java rewrite, so the
service can accept LLM client requests, resolve a group/channel candidate, and
return a successful provider response with usage and audit logging recorded.

## Confirmed Facts

- Milestone 1 is complete: Java service skeleton, configuration, persistence,
  security skeleton, shared response/exception, and health check are in place.
- Milestone 2 is complete: admin CRUD and auth are functional, with all entity
  tables, repositories, and controllers shipped.
- The Java package layout follows `.trellis/spec/java/architecture.md`:
  `config`, `security`, `persistence`, `domain/*`, `relay`, `task`, `common`.
- The Java web stack is Spring WebFlux on Java 21, built with Maven.
- The shared `ApiResponse` envelope and `GlobalExceptionHandler` are already in
  place and must be reused by relay controllers.
- The Relay configuration shape already exists in
  `config/RelayProperties.java` (`connectTimeout`, `readTimeout`, `retry`,
  `circuitBreaker`).
- The current Go relay implementation in `internal/relay/` is the behavioral
  reference: `relay.go` (main loop), `balancer/`, `transformers.go`,
  `metrics.go`.
- `.trellis/spec/java/relay-protocol-adapter.md` defines the required inbound
  and outbound formats (OpenAI Chat, OpenAI Responses, OpenAI Embeddings,
  OpenAI Images, Anthropic Messages, Gemini, Doubao).
- Stream behavior (`SSE`, `tool-call`, multimodal streaming) is explicitly
  scoped to Milestone 4. Milestone 3 only handles non-streaming responses.
- The current production endpoint shape is OpenAI-style. The Java rewrite
  must keep `/v1/chat/completions`, `/v1/messages`, `/v1/responses`,
  `/v1/embeddings`, `/v1/images/generations`, `/v1/images/edits`,
  `/v1/images/variations` reachable and behaviorally compatible.

## Requirements

- Implement a relay handler factory that binds an inbound API format to a
  non-streaming relay pipeline.
- Implement the three-stage relay lifecycle: `RelayHandler` (HTTP entry),
  `RelayRun` (request-level state), `RelayAttempt` (single candidate attempt).
- Resolve the matching `Group` for a request by model name, then build a
  candidate list from `GroupItem` rows and the configured balancer mode.
- Implement the four balancer modes from the Go version: RoundRobin, Random,
  Failover, Weighted.
- Implement the candidate iterator (balancer output + sticky session priority +
  decision tracking) so retries can walk the candidates deterministically.
- Implement per-channel/per-key/per-model circuit breaker with the same state
  machine semantics as the Go version (Closed / Open / HalfOpen, exponential
  cooldown, half-open probe).
- Implement session sticky: when `group.sessionKeepTime > 0`, pin a successful
  `(apiKeyId, requestModel) -> (channelId, keyId)` mapping for the configured
  TTL, and prefer it on the next request.
- Implement adapter factories: an inbound transformer for each supported
  client API and an outbound transformer for each provider / request type.
  At least the OpenAI Chat and OpenAI-compatible Chat path must be fully
  working in Milestone 3; the other formats are scaffolded per spec.
- Implement non-streaming usage recording: parse the upstream `usage` block,
  compute the three-tier input cost (cached read / cached write / fresh) plus
  the output cost, and persist it.
- Persist the seven statistics updates and the `RelayLog` row (request /
  response / error / attempts chain).
- Register the relay routes on `/v1/*` behind the existing
  `RequestAuthenticationFilter` (API key auth) from Milestone 1.
- Reuse the shared `ApiResponse` envelope and `GlobalExceptionHandler` for
  relay failure responses; on full candidate exhaustion return a predictable
  502-style error payload.

## Constraints

- Milestone 3 only ships the **non-streaming** path. SSE, chunked tool calls,
  and partial usage aggregation are deferred to Milestone 4.
- Stream endpoints must still be routed and rejected with a clear "streaming
  not yet supported" error, not crash the application.
- All adapter implementations must normalize to one unified internal request
  model before provider dispatch, per
  `.trellis/spec/java/relay-protocol-adapter.md`.
- Candidate selection must not call provider networks. Only the actual
  `RelayAttempt.forward()` may open a network connection.
- Channel keys must be selected using the same priority rule as Go:
  enabled, non-empty, not in 5-minute 429 cooldown, lowest accumulated cost.
- Circuit breaker state must live in-process (no external store in Milestone 3).
  State is best-effort and is not required to survive restart.
- All new entity tables (if any) must be tracked via Flyway migrations.
- No new external dependencies beyond what is already in `pom.xml` unless a
  child task explicitly justifies them and gets sign-off.

## Acceptance Criteria

- [ ] Non-streaming `/v1/chat/completions` returns a successful upstream
      response end-to-end against a configured OpenAI-compatible provider.
- [ ] All four balancer modes are unit-tested and produce the expected
      candidate ordering for representative group shapes.
- [ ] Circuit breaker opens after the configured failure threshold, refuses
      further traffic until cooldown elapses, then half-opens and recovers on
      probe success.
- [ ] Sticky session pinning is honored when enabled, ignored when disabled.
- [ ] At least one full inbound / outbound adapter pair is wired (OpenAI Chat
      in -> OpenAI Chat out). Other formats in
      `.trellis/spec/java/relay-protocol-adapter.md` are scaffolded with a
      "not yet implemented" guard or the minimum viable path.
- [ ] Successful and failed relay requests each persist a `RelayLog` row
      including the attempts chain.
- [ ] The seven statistics updates (global total / hourly / daily / apikey /
      channel usage / cost / log) are exercised by an integration test using
      a stubbed upstream.
- [ ] An authenticated, properly keyed relay request returns 401/403 when
      the API key is missing or invalid.
- [ ] When all candidates fail, the relay returns a predictable error
      payload and writes a failure log row.
- [ ] All relevant Maven tests pass and the application still starts up
      against the existing M1 / M2 wiring.

## Proposed Child Tasks

- `java-relay-core` — handler factory, three-stage lifecycle, non-streaming
  pipeline skeleton, route registration.
- `java-relay-adapter-spi` — unified internal request/response model,
  inbound/outbound transformer interfaces, adapter factory wiring.
- `java-relay-openai-chat` — OpenAI Chat inbound + OpenAI-compatible outbound
  (the first fully working path).
- `java-relay-balancer` — four balancer modes and the candidate iterator
  with sticky priority and decision tracking.
- `java-relay-circuit-breaker` — in-process circuit breaker with the same
  state machine and exponential cooldown as the Go version.
- `java-relay-session` — sticky session store, TTL handling, fallback when
  disabled.
- `java-relay-metrics` — usage parsing, three-tier cost calculation, seven
  statistics updates, `RelayLog` persistence, attempts chain capture.
- `java-relay-integration` — stubbed upstream, end-to-end tests covering
  success / failure / partial failure / no candidate, and route-level
  authentication checks.

## Out of Scope

- SSE streaming responses (Milestone 4).
- Tool-call streaming, partial usage aggregation, multimodal streaming
  (Milestone 4).
- Gemini / Anthropic / Doubao provider adapters beyond what the spec lists
  as required and what is needed for a single working path in Milestone 3.
- Self-update logic and external rate-limit windows beyond the in-process
  circuit breaker.
- Performance hardening, GC tuning, and final verification matrices
  (Milestone 5).
- Frontend changes.

## Open Questions

- None at the planning level. Provider-adapter scope will be confirmed inside
  `java-relay-openai-chat` and `java-relay-adapter-spi` based on the
  minimum viable path needed to satisfy the M3 acceptance criteria.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance
  criteria. Technical design and execution planning live in `design.md` and
  `implement.md`.
- The Go relay code in `internal/relay/` is the behavioral reference for
  semantics, not a line-by-line translation target.
- Milestone 3 deliberately stops short of streaming. Do not extend any child
  task to add SSE handling; defer that to Milestone 4.
