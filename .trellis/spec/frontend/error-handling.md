# Error Handling

> How frontend errors are handled in this project.

---

## Overview

API wrappers should convert failures into consistent client-facing errors.
Components should render graceful empty/error states instead of crashing.

---

## Guidelines

- Normalize fetch failures in `web/src/api/client.ts`.
- Prefer user-facing messages from endpoint wrappers instead of raw exceptions.
- Keep retry and reconnect behavior close to the API layer.
- Handle loading and error states explicitly in list and detail views.

---

## Common Mistakes

- Swallowing request failures in UI code.
- Rendering empty lists without checking loading or error state.
- Letting a stream failure permanently disable live updates without retry.
