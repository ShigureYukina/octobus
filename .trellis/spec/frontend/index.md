# Frontend Development Guidelines

> Best practices for the Next.js frontend in this project.

---

## Overview

The frontend lives under `web/` and uses Next.js App Router with TypeScript.
Keep pages, shared components, routing helpers, stores, and API clients in their
existing `web/src/` subdirectories.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Frontend module and file layout | Ready |
| [Quality Guidelines](./quality-guidelines.md) | React, state, and composition rules | Ready |
| [Error Handling](./error-handling.md) | API/client error handling patterns | Ready |
| [Logging Guidelines](./logging-guidelines.md) | Client-side logging and debugging | Ready |

---

## Scope

- `web/src/app/` for routes and pages
- `web/src/components/` for UI and feature modules
- `web/src/api/` for client and endpoint wrappers
- `web/src/route/` for lazy route loading and preloading
- `web/src/stores/` for client state

---

## Language

All documentation should be written in **English**.
