# Java relay OpenAI Chat path

## Goal

Implement the first fully working Milestone 3 relay path: OpenAI Chat inbound
to OpenAI-compatible outbound.

After this task a non-streaming `/v1/chat/completions` request can be parsed,
normalized, dispatched to a configured OpenAI-compatible upstream, and mapped
back into a client-compatible response through the relay skeleton.

## Dependency Notes

- Depends directly on `06-01-java-relay-adapter-spi`.
- Should integrate with outputs from `06-01-java-relay-balancer`,
  `06-01-java-relay-circuit-breaker`, and `06-01-java-relay-session` rather
  than redefining those concerns.
- `06-01-java-relay-metrics` will later consume the unified response and
  attempts emitted from this path.
- Implements T6 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.

## Requirements

- Implement OpenAI Chat inbound transformation from request JSON into the
  unified relay request model.
- Implement OpenAI-compatible outbound transformation from unified request into
  upstream JSON plus provider URL/header metadata.
- Add provider HTTP client code under `relay/provider/`, not `domain/`.
- Wire `RelayRun` / `RelayAttempt` so a non-streaming OpenAI Chat request can
  reach a real upstream and receive a mapped response.
- Preserve T1 behavior for `stream: true` by continuing to reject streaming in
  Milestone 3.
- Reuse balancer / breaker / sticky abstractions rather than embedding that
  logic directly into the provider client.
- Keep scope to the OpenAI Chat path only; other inbound formats stay guarded.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes with OpenAI Chat path coverage.
- [ ] A representative Chat Completions request is parsed into the unified
      model correctly.
- [ ] A unified request is serialized back to an OpenAI-compatible upstream
      payload correctly.
- [ ] Non-stream `/v1/chat/completions` succeeds end-to-end against a stubbed
      or controlled OpenAI-compatible upstream.
- [ ] Other non-target inbound formats still return controlled guarded errors.
- [ ] No streaming behavior is implemented in this task.
