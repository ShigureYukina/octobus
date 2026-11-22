# Design

## Objective

Plan Milestone 1 as the stable technical foundation for the Java rewrite.
Milestone 1 is not allowed to spill into CRUD or relay feature delivery beyond
the minimum architectural decisions needed to unblock later milestones.

## Technical Decisions

### Runtime stack

- Java version: 21
- Framework: Spring Boot
- Web stack: Spring WebFlux
- Build tool: Maven
- Initial database target: SQLite

These choices prioritize behavior parity with the current Go relay shape,
low-friction local startup, and minimal delay before Milestone 2 and 3.

### Boundary model

Milestone 1 should establish these package boundaries only:

- `config`
- `common`
- `security`
- `persistence`
- `domain`
- `relay`
- `task`

The design goal is to prevent early coupling between CRUD services and relay
protocol logic.

### Persistence scope

Milestone 1 should only add the persistence baseline needed to validate boot,
migration wiring, datasource setup, and startup verification. It should not
attempt to port the full Go schema, implement login-query persistence, or add
full business entity coverage in one pass.

### Web foundation scope

Milestone 1 should define:

- response envelope
- exception translation boundary
- validation failure mapping
- health endpoint

It should not implement resource-specific controllers yet.

## Child Task Design

Milestone 1 should be split into independently verifiable child tasks:

1. `java-bootstrap-project`
2. `java-config-and-validation`
3. `java-persistence-baseline`
4. `java-shared-response-and-exception`
5. `java-security-baseline`
6. `java-health-and-smoke-test`

### Ordering

- `java-bootstrap-project` must finish first.
- `java-config-and-validation` depends on bootstrap.
- `java-persistence-baseline` depends on bootstrap and config.
- `java-shared-response-and-exception` depends on bootstrap.
- `java-security-baseline` depends on bootstrap, config, and shared response/error.
- `java-health-and-smoke-test` depends on bootstrap, config, persistence, and shared response/error.

## Compatibility Notes

- JWT secret must come from configuration, matching the current hardened Go behavior.
- Error envelopes must stay compatible with the current frontend expectations.
- SQLite keeps local behavior closer to the current Go deployment default.

## Risks

- WebFlux may be underused in Milestone 1 and tempt MVC-style coding that hurts later relay work.
- SQLite may hide future database differences, but it minimizes bootstrap risk.
- If shared error contracts drift from current frontend expectations, Milestone 2 will pay the cost.
