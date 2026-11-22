# Java persistence baseline

## Goal

Add the SQLite datasource and Flyway migration baseline needed to verify boot
and migration wiring.

## Dependency Notes

- Depends on `05-31-java-bootstrap-project` and `05-31-java-config-and-validation`.
- Must not expand into full schema migration or login-query persistence.

## Requirements

- Configure the initial SQLite datasource.
- Add Flyway baseline structure.
- Verify boot-time migration execution.
- Keep scope limited to persistence wiring and startup validation.

## Acceptance Criteria

- [ ] Application can connect to SQLite using the typed config.
- [ ] Flyway runs successfully at startup.
- [ ] No full business-entity migration scope is pulled into this task.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
