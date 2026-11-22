# Roadmap

> Delivery phases for rebuilding Octobus as a Java application.

---

## Overall Goal

Build a Java version that preserves the current product behavior, public admin
API, relay endpoints, and operational constraints of the Go version.

---

## Five-Step Plan

### Milestone 1 — Platform Skeleton

Build the application skeleton, configuration model, persistence baseline,
security baseline, and shared response/error model.

### Milestone 2 — Admin CRUD + Auth

Implement the management-side APIs and data flows for user login, channel,
group, apikey, setting, model, log, and stats management.

### Milestone 3 — Relay Main Path

Implement the non-streaming relay path first, including request intake,
provider dispatch, balancing, and basic usage recording.

### Milestone 4 — Stream + Adapter Compatibility

Implement SSE streaming, protocol adapters, tool-call conversion, multimodal
mapping, and provider-specific response normalization.

### Milestone 5 — Hardening + Verification

Implement observability, cleanup jobs, performance safeguards, and the final
test matrix needed for replacement-level confidence.

---

## Milestone 1 Detail

### Objective

Create a runnable Java service skeleton that future CRUD and relay work can
 safely build on without structural rework.

### Deliverables

- Spring Boot application entrypoint.
- Package skeleton matching the architecture spec.
- `application.yml` baseline with environment override rules.
- Flyway migration baseline.
- Shared response envelope and exception translation.
- JWT secret configuration and API key filter skeleton.
- Basic health endpoint.

### Work Breakdown

1. **Project bootstrap**
   - Create Maven or Gradle project.
   - Add Spring Boot, validation, persistence, Flyway, JWT, and test dependencies.
   - Establish base package namespace.

2. **Package skeleton**
   - Create `config`, `common`, `security`, `persistence`, `domain`, `relay`, and `task` packages.
   - Add placeholder package-level README or comments only if needed.

3. **Configuration model**
   - Define typed config classes for server, database, relay, and auth.
   - Define startup validation for required fields.
   - Map environment variables consistently.

4. **Persistence baseline**
   - Configure datasource and JPA.
   - Create first Flyway migration structure.
   - Validate migration wiring and boot-time database startup only.
   - Do not port full business entities or login-query persistence in this milestone.

5. **Shared web foundation**
   - Define success/error response shape.
   - Add global exception handler.
   - Add request validation failure mapping.

6. **Security baseline**
   - Add JWT utility/service.
   - Add API key extraction filter skeleton.
   - Add route-level auth boundary for admin API vs relay API.

7. **Operational baseline**
   - Add health endpoint.
   - Add startup logging for critical config summary without secrets.
   - Add one smoke integration test for application boot.

### Dependencies

- Java version: 21.
- Build tool: Maven.
- Web stack: Spring WebFlux.
- Initial database target: SQLite.

### Acceptance Criteria

- Service boots locally with documented startup steps.
- Config validation fails fast on invalid required values.
- Health endpoint responds successfully.
- Shared error handler returns consistent JSON.
- JWT secret comes only from config, not derived credentials.
- Flyway migration runs successfully on startup.
- A smoke test verifies application boot.

### Risks

- Choosing MVC and later forcing streaming-heavy WebFlux retrofits.
- Letting persistence entities leak into HTTP DTOs too early.
- Defining an error envelope that does not match the current frontend expectation.
- Over-designing package boundaries before relay constraints are clear.

### Exit Rule

Do not start Milestone 2 until the project can boot, validate config, run the
 first migration, and return a stable health response with the shared error model in place.

---

## Task Mapping Suggestion

Milestone 1 can be split into the following Trellis tasks:

- `java-bootstrap-project`
- `java-config-and-validation`
- `java-persistence-baseline`
- `java-shared-response-and-exception`
- `java-security-baseline`
- `java-health-and-smoke-test`
