# Error Handling

> Error mapping and visibility rules for the Java rewrite.

---

## Rules

- Return generic client-safe messages for database and internal failures.
- Preserve validation detail only when the client can act on it safely.
- Map relay provider failures to stable API error shapes.
- Keep stream failures observable but do not leak upstream internals.

---

## Error Classes

- Validation errors
- Authentication / authorization errors
- Not found errors
- Provider relay errors
- Internal server errors

---

## Behavior

- Controllers should translate exceptions into response objects.
- Services should throw typed exceptions instead of returning null on failure.
- Relay adapters should normalize provider-specific error payloads.
