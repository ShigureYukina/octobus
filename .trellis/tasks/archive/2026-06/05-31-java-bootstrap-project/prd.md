# Java bootstrap project

## Goal

Create the Maven-based Spring Boot project scaffold and package skeleton used by
all later Java rewrite work.

## Dependency Notes

- This child task has no upstream child-task dependency.
- Downstream tasks depend on this scaffold existing before they start.

## Requirements

- Create the initial Maven project structure.
- Use `Java 21` as the project baseline.
- Add baseline Spring Boot and test dependencies needed by Milestone 1.
- Establish the base package namespace and package skeleton.

## Acceptance Criteria

- [ ] The Java project builds with Maven.
- [ ] The project is pinned to Java 21.
- [ ] The package skeleton for config/common/security/persistence/domain/relay/task exists.
- [ ] Downstream tasks can add config, persistence, and web foundation work without restructuring the project.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
