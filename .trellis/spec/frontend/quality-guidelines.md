# Quality Guidelines

> Code quality rules for the frontend in this project.

---

## Overview

Use React 19 patterns, keep state local when possible, and avoid unnecessary
memoization or wrapper abstractions.

---

## Guidelines

- Keep components small and focused.
- Prefer derived values over duplicated state.
- Do not introduce new hooks or stores unless the state is shared.
- Use existing route, store, and API patterns before adding new abstractions.
- Keep client-only logic out of `app/` pages when it belongs in `components/` or `api/`.

---

## Common Mistakes

- Putting network calls directly in page components.
- Duplicating request/response parsing logic across components.
- Creating a second source of truth for values already stored in Zustand or query state.
- Adding broad abstractions before the feature shape is stable.
