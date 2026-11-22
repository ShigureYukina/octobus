# Java setting CRUD

## Goal

Implement system setting CRUD endpoints for the admin API.

## Dependency Notes

- Depends on `05-31-java-user-auth` for the auth flow needed to protect and
  test endpoints.

## Requirements

- Create setting entity and Flyway migration.
- Implement list, get, update endpoints for system settings.
- Use shared ApiResponse envelope.

## Acceptance Criteria

- [ ] Setting CRUD endpoints work with valid admin JWT.
- [ ] Setting endpoints return 401 without valid auth.
- [ ] Maven tests pass.
