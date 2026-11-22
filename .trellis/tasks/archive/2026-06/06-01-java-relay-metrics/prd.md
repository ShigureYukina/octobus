# Java relay metrics and logging

## Goal

Implement relay-side usage parsing, cost calculation, statistics updates, and
audit logging for Milestone 3.

After this task successful and failed relay requests can be recorded with usage
data, attempts chain, cost breakdown, and the seven required stats updates.

## Dependency Notes

- Depends on `06-01-java-relay-adapter-spi` for unified response/usage shapes.
- Depends on `06-01-java-relay-openai-chat` or an equivalent working provider
  path to supply real responses.
- Reuses the persistence layer and existing entities from Milestone 1 / 2; no
  new table is expected.
- Implements T7 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.

## Requirements

- Add relay metrics components for parsing upstream `usage` payloads.
- Compute cost using prompt/output/cache-read/cache-write token counts and the
  existing model price source of truth.
- Preserve the Go-side three-tier input-cost formula and clamp invalid negative
  intermediate values safely.
- Update the required statistics buckets: global total, hourly, daily,
  per-API-key, per-channel usage/cost, plus `RelayLog` persistence.
- Persist `RelayLog` with request model, actual model, request/response body,
  error summary, token counts, total cost, and attempts chain.
- Support both successful and failed relay outcomes.
- Keep metrics/persistence separate from provider client code.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes for the metrics package.
- [ ] Cost-calculation tests cover prompt/output/cache combinations and match
      expected totals.
- [ ] Tests verify all required statistics updates are invoked.
- [ ] Tests verify successful and failed relay requests each write a `RelayLog`
      row with the expected fields.
- [ ] No schema migration is required; if one becomes necessary, the task must
      stop and re-scope before implementation continues.
