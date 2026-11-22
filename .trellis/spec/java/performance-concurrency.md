# Performance & Concurrency

> Performance and concurrency rules for the Java rewrite.

---

## Rules

- Keep relay streaming non-blocking where possible.
- Use bounded caches and bounded subscriber sets.
- Clean up expired circuit breaker and session entries.
- Avoid thread-per-request patterns in hot relay paths.

---

## Focus Areas

- SSE streaming
- Provider HTTP calls
- Cache and session cleanup
- Balancer and circuit breaker hot paths
