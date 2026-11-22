# Plan Java rewrite milestone 2

## Goal

Implement the admin-side CRUD APIs and real user authentication flow needed to
manage the Octobus system through the frontend.

## Confirmed Facts

- Milestone 1 is complete: the Java service skeleton, configuration, persistence,
  security skeleton, shared response/exception, and health check are in place.
- The Go admin API surface is known: user, channel, group, apikey, setting,
  model, log, and stats resources each have GET/POST/PUT/DELETE endpoints.
- The Java frontend is already built and only expects compatible API responses.

## Requirements

- Implement user login/logout with real credential verification (bcrypt).
- Implement admin JWT auth flow using the milestone 1 security skeleton.
- Implement channel CRUD endpoints.
- Implement group CRUD endpoints.
- Implement API key CRUD endpoints.
- Implement setting CRUD endpoints.
- Implement model, log, and stats query endpoints.
- All admin endpoints must use the shared response envelope from milestone 1.
- Use Flyway migrations for all new tables.

## Constraints

- java-user-auth must be completed first because all CRUD tasks depend on the
  auth flow being functional for testing and development.
- Channel, group, apikey, setting CRUD tasks are independent of each other and
  can be implemented in any order after user-auth.
- java-model-log-stats can start after user-auth but may need channel context.

## Acceptance Criteria

- [ ] User login/logout flow works with bcrypt password verification.
- [ ] Admin JWT auth is enforced on all protected endpoints.
- [ ] All CRUD endpoints return consistent ApiResponse envelopes.
- [ ] All new entity tables are tracked via Flyway migrations.
- [ ] Corresponding backend tests exist for each resource.
- [ ] All relevant Maven tests pass.

## Proposed Child Tasks

- `java-user-auth` — login, logout, password hashing, admin JWT flow
- `java-channel-crud` — channel management endpoints
- `java-group-crud` — group management endpoints
- `java-apikey-crud` — API key management endpoints
- `java-setting-crud` — system setting management endpoints
- `java-model-log-stats` — model, log, and stats query endpoints

## Out of Scope

- Relay implementation (Milestone 3)
- Provider protocol adapters (Milestone 4)
- SSE streaming (Milestone 4)
- Frontend changes (not part of this rewrite phase)

## Open Questions

- None.
