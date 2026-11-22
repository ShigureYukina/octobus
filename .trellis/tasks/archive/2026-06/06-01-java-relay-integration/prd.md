# Java relay integration verification

## Goal

Prove Milestone 3 end-to-end with a controlled upstream and full relay-path
verification across success, failure, retry, auth, breaker, sticky, and stream
guard scenarios.

After this task Milestone 3 has an executable verification layer and can be
marked complete once the parent acceptance criteria are satisfied.

## Dependency Notes

- Depends on all prior relay sub-tasks T2-T7 being implemented and wired.
- Is the final validation gate defined in parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.
- Is the only child task allowed to introduce the embedded stub upstream test
  infrastructure.

## Requirements

- Add an embedded/stub upstream for deterministic OpenAI-compatible responses.
- Add end-to-end WebFlux integration tests for:
  success path, candidate failover, full candidate exhaustion, sticky reuse,
  circuit breaker open/half-open/closed transitions, missing or invalid API
  key, and stream requests returning 501.
- Use a relay-specific test profile or equivalent test wiring to isolate data
  and upstream configuration.
- Drive the tests through the real `/v1/*` routes rather than bypassing relay
  entrypoints.
- Finish with the final Maven verification command required by the parent task.

## Acceptance Criteria

- [ ] `mvn -pl java -am verify` passes.
- [ ] Integration tests cover success, retry/failover, total failure, sticky
      session reuse, circuit breaker transitions, auth failure, and stream 501.
- [ ] Tests exercise the real relay wiring from route entry to response body.
- [ ] The parent Milestone 3 acceptance criteria can be checked from the test
      evidence produced here.
- [ ] Test-only upstream infrastructure does not leak into production runtime
      wiring.
