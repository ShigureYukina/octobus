# Java security baseline

## Goal

Add the JWT and API key security skeleton required for later admin and relay
endpoint protection.

## Dependency Notes

- Depends on `05-31-java-bootstrap-project`, `05-31-java-config-and-validation`, and `05-31-java-shared-response-and-exception`.
- Must remain a security skeleton and not expand into full login persistence.

## Requirements

- Add JWT utility or service.
- Add API key extraction and auth-boundary skeleton.
- Keep all secrets config-driven only.

## Acceptance Criteria

- [ ] JWT secret is read from config only.
- [ ] API key auth boundary exists in skeleton form.
- [ ] No credential-derived signing behavior exists.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
