# Architecture

> Package boundaries for the Java rewrite.

---

## Scope

The Java version should stay as a single deployable application with clear
internal package boundaries.

---

## Required Boundaries

- `config` for application configuration and bean wiring.
- `security` for JWT, API keys, and request filters.
- `persistence` for entities, repositories, and migrations.
- `domain/*` for user, channel, group, apikey, setting, log, stats, and update
  business logic.
- `relay` for protocol adapters, balancing, provider clients, and streaming.
- `task` for scheduled jobs and maintenance tasks.
- `common` for shared constants, exceptions, response wrappers, and helpers.

---

## Structural Rules

- Keep relay adapters separate from CRUD services.
- Keep persistence entities out of controller packages.
- Keep stream parsing and provider HTTP clients out of domain services.
- Keep shared response/error types centralized.
