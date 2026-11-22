# Java relay session sticky store

## Goal

Implement session stickiness for relay candidate reuse: store and resolve the
last successful `(channelId, keyId)` for an `(apiKeyId, requestModel)` pair
within a configured TTL.

After this task the iterator can prefer a sticky candidate when group sticky is
enabled, while falling back cleanly when sticky is disabled or expired.

## Dependency Notes

- Depends on `06-01-java-relay-core` for milestone context.
- Is consumed by `06-01-java-relay-balancer`, but should be implemented behind
  an interface so both tasks can evolve independently.
- TTL semantics depend on `Group.sessionKeepTime` from the existing domain
  model; no schema change is expected.
- Implements T5 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.

## Requirements

- Add `relay/session/` package with `StickyStore`, `InMemoryStickyStore`, and a
  sticky entry value type.
- Key entries by `(apiKeyId, requestModel)`.
- Store `(channelId, keyId, expiresAt)` or equivalent TTL-aware state.
- Return empty / miss when sticky is disabled (`sessionKeepTime == 0`) or the
  entry has expired.
- Remove expired entries lazily on read.
- Keep this task in-process only; no database or Redis integration.
- Preserve a clean interface boundary so future storage backends can be swapped
  in without changing iterator call sites.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes for the session package.
- [ ] Tests cover write-then-read success within TTL.
- [ ] Tests cover expiry returning empty and cleaning stale entries.
- [ ] Tests cover sticky-disabled behavior when TTL is zero.
- [ ] The store API is independent from balancer implementation details and has
      no direct dependency on provider/network code.
