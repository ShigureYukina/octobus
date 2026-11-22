# Java API key CRUD

## Goal

Implement API key CRUD endpoints for the admin API.

## Dependency Notes

- Depends on `05-31-java-user-auth` for the auth flow needed to protect and
  test endpoints.

## Requirements

- Create API key entity and Flyway migration.
- Implement list, get, create, update, delete endpoints for API keys.
- Generate API keys with stable prefix format.
- Use shared ApiResponse envelope.

## Acceptance Criteria

- [ ] API key CRUD endpoints work with valid admin JWT.
- [ ] API key endpoints return 401 without valid auth.
- [ ] Generated keys follow the expected prefix format.
- [ ] Maven tests pass.
