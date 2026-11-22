# Java user auth

## Goal

Implement user login/logout with bcrypt password verification and wire the
real admin JWT auth flow into the existing security skeleton.

## Dependency Notes

- Depends on Milestone 1 foundation: bootstrap, config, security skeleton,
  shared response/exception, and persistence baseline.
- Must complete before any other Milestone 2 child task can meaningfully test
  its endpoints.

## Requirements

- Create user entity and Flyway migration.
- Implement bcrypt password hashing.
- Implement login endpoint.
- Implement logout endpoint.
- Wire admin JWT auth into the existing RequestAuthenticationFilter.

## Acceptance Criteria

- [ ] User login returns a valid JWT token.
- [ ] User login with wrong credentials returns 401.
- [ ] JWT secret is read from config only, not derived.
- [ ] Protected endpoints reject unauthenticated requests with 401.
- [ ] Logout invalidates the current session/token.
- [ ] Maven tests pass.
