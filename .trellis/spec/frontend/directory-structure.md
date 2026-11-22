# Directory Structure

> How frontend code is organized in this project.

---

## Overview

This project keeps frontend code under `web/src/` and groups files by concern,
not by layer alone.

---

## Directory Layout

```text
web/
├── src/
│   ├── app/          # Next.js App Router pages and layouts
│   ├── api/          # API client, endpoint wrappers, and shared types
│   ├── components/   # UI primitives and feature modules
│   ├── hooks/        # Custom React hooks
│   ├── lib/          # Shared libraries and helpers
│   ├── provider/     # React providers
│   ├── route/        # Client route config and lazy loading helpers
│   ├── stores/       # Zustand stores
│   └── types/        # TypeScript types
```

---

## Module Organization

- Keep route-level code inside `app/`.
- Keep feature widgets under `components/modules/`.
- Keep reusable UI primitives under `components/ui/`.
- Keep server communication in `api/` and avoid direct fetch calls in components.
- Keep route loading logic in `route/` rather than inside page components.

---

## Naming Conventions

- Use `*.tsx` for React components.
- Use `*.ts` for helpers, stores, and API wrappers.
- Prefer feature-oriented folder names that match the screen or domain.

---

## Examples

- `web/src/components/modules/log/`
- `web/src/api/endpoints/`
- `web/src/route/config.tsx`
