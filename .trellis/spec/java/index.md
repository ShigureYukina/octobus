# Java Rewrite Spec Draft

> Draft specification set for a Java version of Octobus.

---

## Purpose

This spec tree defines the minimum conventions needed to rebuild Octobus in
Java while preserving the current product behavior and public API surface.

---

## Spec Index

| Spec | Purpose | Status |
|------|---------|--------|
| [Architecture](./architecture.md) | Module boundaries and package layout | Draft |
| [Roadmap](./roadmap.md) | Delivery phases and milestone sequencing | Draft |
| [API Contract](./api-contract.md) | Admin API and relay endpoint contracts | Draft |
| [Relay Protocol Adapter](./relay-protocol-adapter.md) | Provider and stream transformation rules | Draft |
| [Error Handling](./error-handling.md) | Error mapping and response policy | Draft |
| [Security & Auth](./security-auth.md) | JWT, API key, and access control rules | Draft |
| [Database & Persistence](./database-persistence.md) | Entity, repository, and migration rules | Draft |
| [Logging & Observability](./logging-observability.md) | Logging, metrics, and traceability rules | Draft |
| [Testing Strategy](./testing-strategy.md) | Unit, integration, and contract test scope | Draft |
| [Configuration](./configuration.md) | YAML, environment, and defaults | Draft |
| [Performance & Concurrency](./performance-concurrency.md) | Stream, cache, and worker constraints | Draft |

---

## Drafting Rule

- Keep these specs aligned with the current Go implementation unless the Java
  rewrite intentionally changes behavior.
- Prefer concrete file/package names over abstract advice.
- Update this tree before implementation when a Java design decision becomes stable.
