# Java model log stats

## Goal

Implement model, log, and stats query endpoints for the admin API.

## Dependency Notes

- Depends on `05-31-java-user-auth` for the auth flow needed to protect and
  test endpoints.

## Requirements

- Implement model list endpoint.
- Implement log list endpoint with filtering and pagination.
- Implement stats aggregation endpoint.
- Use shared ApiResponse envelope.

## Acceptance Criteria

- [ ] Model/Log/Stats endpoints work with valid admin JWT.
- [ ] Endpoints return 401 without valid auth.
- [ ] Log list supports pagination and filtering.
- [ ] Maven tests pass.
