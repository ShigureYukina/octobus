# Java health and smoke test

## Goal

Add a health endpoint and one startup smoke test that verifies the Milestone 1
foundation can boot correctly.

## Dependency Notes

- Depends on `05-31-java-bootstrap-project`, `05-31-java-config-and-validation`, `05-31-java-persistence-baseline`, and `05-31-java-shared-response-and-exception`.
- Should start after those upstream tasks are complete.

## Requirements

- Add a health endpoint.
- Add one smoke integration test for application boot.
- Verify the app boots with config + migration + shared error foundation in place.

## Acceptance Criteria

- [ ] Health endpoint responds successfully.
- [ ] Smoke test verifies application boot.
- [ ] Milestone 1 exit criteria can be checked from this task output.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
