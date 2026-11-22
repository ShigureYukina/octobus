# Java shared response and exception

## Goal

Define the shared success/error response envelope and global exception handling
baseline for the Java service.

## Dependency Notes

- Depends on `05-31-java-bootstrap-project`.
- Must complete before `05-31-java-security-baseline` and `05-31-java-health-and-smoke-test`.

## Requirements

- Define the response envelope shape.
- Add global exception translation.
- Add validation failure mapping.

## Acceptance Criteria

- [ ] Shared error responses are consistent.
- [ ] Validation failures are mapped predictably.
- [ ] Downstream security and health tasks can reuse the shared web foundation.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
