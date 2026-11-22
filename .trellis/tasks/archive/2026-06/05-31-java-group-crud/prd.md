# Java group CRUD

## Goal

Implement group CRUD endpoints for the admin API.

## Dependency Notes

- Depends on `05-31-java-user-auth` for the auth flow needed to protect and
  test endpoints.

## Requirements

- Create group entity and Flyway migration.
- Implement list, get, create, update, delete endpoints for groups.
- Support group-item (channel membership) management.
- Use shared ApiResponse envelope.

## Acceptance Criteria

- [ ] Group CRUD endpoints work with valid admin JWT.
- [ ] Group endpoints return 401 without valid auth.
- [ ] Group-item membership can be managed.
- [ ] Maven tests pass.
