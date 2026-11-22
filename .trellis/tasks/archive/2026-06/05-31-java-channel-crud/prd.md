# Java channel CRUD

## Goal

Implement channel CRUD endpoints for the admin API.

## Dependency Notes

- Depends on `05-31-java-user-auth` for the auth flow needed to protect and
  test endpoints.

## Requirements

- Create channel entity and Flyway migration.
- Implement list, get, create, update, delete endpoints for channels.
- Support pagination and search.
- Use shared ApiResponse envelope.

## Acceptance Criteria

- [ ] Channel CRUD endpoints work with valid admin JWT.
- [ ] Channel endpoints return 401 without valid auth.
- [ ] List endpoint supports pagination.
- [ ] Maven tests pass.
