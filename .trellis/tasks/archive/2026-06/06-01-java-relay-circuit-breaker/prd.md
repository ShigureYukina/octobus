# Java relay circuit breaker

## Goal

Implement the in-process relay circuit breaker used to suppress unhealthy
`channelId:keyId:modelName` combinations during Milestone 3.

After this task relay code can query breaker state and record success/failure
transitions with the same Closed/Open/HalfOpen semantics as the Go service.

## Dependency Notes

- Depends on `06-01-java-relay-core` only for overall milestone context.
- Is consumed by `06-01-java-relay-balancer` / relay iterator wiring, but can
  be implemented independently as long as the key shape stays stable.
- May require a minimal extension of `RelayProperties` only if Go-compatible
  cooldown controls cannot be represented with the existing config.
- Implements T4 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.

## Requirements

- Add `relay/breaker/` package with a `CircuitBreaker` interface and an
  in-memory implementation.
- Key breaker state by `channelId:channelKeyId:modelName`.
- Implement Closed -> Open when failures reach threshold.
- Implement Open -> HalfOpen after cooldown elapses.
- Implement HalfOpen -> Closed on probe success.
- Implement HalfOpen -> Open on probe failure with increased cooldown,
  preserving exponential-backoff semantics close to the Go implementation.
- Expose methods to check whether an attempt is allowed and to record success
  or failure.
- Keep state in-process only; no DB, Redis, or distributed coordination in
  this task.
- Avoid blocking behavior in the event-loop path.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes for the breaker package.
- [ ] Tests cover threshold trip, cooldown expiry to half-open, probe success
      recovery, probe failure reopening, and state isolation between distinct
      keys.
- [ ] Breaker state is keyed by `channelId:keyId:modelName` and not shared too
      broadly.
- [ ] Any config extension is minimal and documented; otherwise the existing
      `RelayProperties` shape is reused unchanged.
- [ ] No provider client, route, or persistence work is added in this task.
