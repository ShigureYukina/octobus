# Java relay core skeleton

## Goal

Stand up the non-streaming relay skeleton in the Java rewrite: a
`RelayHandler` factory, the three-stage lifecycle types
(`RelayHandler` / `RelayRun` / `RelayAttempt`), and the registration
of all `/v1/*` routes behind the existing API key auth filter.

After this task the application still starts, every relay route is
reachable, and each one produces a controlled "adapter not wired"
error rather than a stack trace. The behavior is exercised by a
WebFlux unit test that drives a stubbed `RelayRun`.

## Dependency Notes

- Depends on M1 platform skeleton: `ApiResponse`, `GlobalExceptionHandler`,
  `RequestAuthenticationFilter`, Flyway-managed schema.
- Depends on M2 admin CRUD: `Channel`, `Group`, `GroupItem`, `APIKey`
  entities and their repositories must exist.
- Blocks every other M3 sub-task. They all plug into the lifecycle
  defined here.
- The full design and ordering for this task lives in
  `06-01-05-31-java-rewrite-milestone-3/design.md` (section "T1.
  java-relay-core") and `.../implement.md` (section "T1.
  java-relay-core"). This PRD is the scope summary.

## Requirements

- Add a `relay/` package with `RelayHandler`, `RelayRun`, and
  `RelayAttempt` types.
- `RelayHandler` is a WebFlux handler factory parameterized by the
  inbound API format. It parses the request body, builds a
  `RelayRun`, and returns the response as `ResponseEntity<ApiResponse<?>>`.
- `RelayRun` is the request-level state holder. It owns the unified
  request, the metrics recorder, the candidate iterator (wired in T3),
  and the resolved group.
- `RelayAttempt` is the single-candidate attempt scaffold. The full
  outbound dispatch is filled in by T6 (`java-relay-openai-chat`).
- Register the seven `/v1/*` routes behind
  `RequestAuthenticationFilter`. Stream-capable routes must return
  a predictable 501 "streaming not yet supported" until M4 lands.
- All non-stream errors are returned through the shared
  `ApiResponse` envelope and `GlobalExceptionHandler`.
- A controlled "adapter not wired" error must surface from
  `/v1/chat/completions` when no outbound transformer is registered,
  proving the route is alive end-to-end.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` is green for the new `relay/` package.
- [ ] The application boots with all M1 / M2 wiring intact.
- [ ] `curl -X POST http://localhost:8080/v1/chat/completions -H
      "Authorization: Bearer <valid-key>" -d '{...}'` returns the
      shared `ApiResponse` envelope with a controlled error, not a
      stack trace and not a 500 with no body.
- [ ] A WebFlux unit test drives a stubbed `RelayRun` through
      `RelayHandler` and asserts the response envelope and status
      code.
- [ ] No changes outside the `relay/` package and its test
      counterparts. No Flyway migration in this task.
