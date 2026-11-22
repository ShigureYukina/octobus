# Java relay balancer

## Goal

Implement relay candidate ordering for Milestone 3: the four balancer modes and
the iterator that exposes deterministic retry order, sticky priority handling,
and attempt/skip tracking.

After this task `RelayRun` has a testable candidate traversal mechanism, but it
still does not perform provider I/O.

## Dependency Notes

- Depends on `06-01-java-relay-core` for the relay lifecycle shell.
- Depends on `06-01-java-relay-adapter-spi` for unified request context and
  naming alignment.
- Must integrate later with `06-01-java-relay-circuit-breaker` and
  `06-01-java-relay-session`, but should depend on their interfaces rather than
  concrete implementations.
- Implements T3 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.

## Requirements

- Add `relay/balancer/` package with a balancer interface and four strategies:
  RoundRobin, Random, Failover, Weighted.
- Match the Go relay semantics for each strategy:
  round-robin by rotating starting point, random shuffle, failover by priority,
  weighted ordering based on configured weight.
- Add `RelayCandidateIterator` that accepts ordered candidates plus injected
  `StickyStore` and `CircuitBreaker` interfaces.
- Iterator must expose the current candidate, move to the next candidate,
  record skip reasons, record actual attempts, and return the accumulated
  attempts chain.
- Sticky-hit candidates must be preferred ahead of balancer output when sticky
  is enabled.
- Circuit-breaker rejections must be recorded as skips rather than real
  provider attempts.
- Keep candidate selection pure: no network I/O, no persistence writes, no
  provider client access in this task.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes for the balancer package.
- [ ] Unit tests cover all four balancer modes with representative candidate
      sets.
- [ ] Iterator tests cover normal ordering, sticky priority, circuit-break
      skips, and attempts-chain recording.
- [ ] Iterator depends on abstractions for sticky and breaker behavior, not
      hard-coded singletons.
- [ ] No provider HTTP code or persistence code is introduced in this task.
