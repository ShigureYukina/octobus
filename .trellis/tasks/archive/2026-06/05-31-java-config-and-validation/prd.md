# Java config and validation

## Goal

Define typed configuration classes, startup validation rules, and environment
override mapping for the Java scaffold.

## Dependency Notes

- Depends on `05-31-java-bootstrap-project`.
- Must complete before persistence and security baseline tasks start.

## Requirements

- Define typed config classes for server, database, relay, and auth.
- Add fail-fast validation for required values.
- Document or encode environment override mapping.

## Acceptance Criteria

- [ ] Invalid required config fails at startup.
- [ ] Valid config loads through typed classes.
- [ ] Persistence and security tasks can depend on stable config contracts.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
