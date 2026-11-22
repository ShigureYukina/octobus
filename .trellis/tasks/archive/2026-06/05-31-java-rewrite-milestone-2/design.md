# Design

## Objective

Deliver the admin CRUD and auth layer required for the Java rewrite to become
operationally useful, while keeping the relay core for later milestones.

## Child Task Design

Milestone 2 is split into independently verifiable child tasks:

1. `java-user-auth`
2. `java-channel-crud`
3. `java-group-crud`
4. `java-apikey-crud`
5. `java-setting-crud`
6. `java-model-log-stats`

### Ordering

- `java-user-auth` must finish first. It implements the login/logout flow,
  bcrypt password verification, and the real admin JWT auth that all other
  endpoints depend on.
- `java-channel-crud`, `java-group-crud`, `java-apikey-crud`, `java-setting-crud`
  are independent of each other and can be parallelized.
- `java-model-log-stats` may depend on channel context for some queries.

## Technical Decisions

### Auth flow
- Use bcrypt for password hashing (same as Go version).
- JWT tokens use the existing config-driven secret from milestone 1.
- Login returns a JWT token; logout invalidates the session.

### CRUD patterns
- Each resource uses a standard controller → service → repository pattern.
- Create/update DTOs are separate from entity classes.
- All responses use the shared ApiResponse envelope.

### Persistence
- New Flyway migrations for each entity table.
- Use JPA/Hibernate for entity mapping.
- Keep queries in repository layer, not in services or controllers.

## Compatibility Notes

- API response shapes must match the existing Go admin API to keep the
  frontend working without changes.
- Pagination and list response format must be consistent across all resources.

## Risks

- If response contracts drift from what the frontend expects, Milestone 2
  will need coordination or frontend adjustments.
- Parallelizing CRUD tasks may cause merge conflicts if entities share
  relationships (e.g., channel-group mappings).
