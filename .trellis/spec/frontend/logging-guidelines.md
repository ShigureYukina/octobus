# Logging Guidelines

> How frontend logging and debugging should work in this project.

---

## Overview

Keep client logs minimal and actionable. Prefer structured error reporting in
the API layer and visible UI feedback in components.

---

## Guidelines

- Log only when a user-visible action or API call fails.
- Include enough context to diagnose the failing endpoint or route.
- Avoid high-frequency console noise inside render paths.
- Use existing debug helpers instead of ad-hoc `console.log` statements.

---

## Common Mistakes

- Logging the same error in both the API layer and the component.
- Leaving temporary debug output in shared UI components.
