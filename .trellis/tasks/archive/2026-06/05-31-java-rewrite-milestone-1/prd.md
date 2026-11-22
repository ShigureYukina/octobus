# Plan Java rewrite milestone 1

## Goal

Define the delivery plan and executable task split for Milestone 1 of the Java
rewrite, so implementation can begin from a stable application skeleton instead
of ad-hoc decisions.

## Confirmed Facts

- The current repository is a Go backend plus Next.js frontend project.
- A Java rewrite is intended to preserve current product behavior and public API
  shape rather than redesign the product.
- The agreed overall roadmap has five milestones:
  1. Platform Skeleton
  2. Admin CRUD + Auth
  3. Relay Main Path
  4. Stream + Adapter Compatibility
  5. Hardening + Verification
- Milestone 1 covers application skeleton, configuration, persistence baseline,
  security baseline, and shared response/error handling.
- A Java rewrite spec tree already exists under `.trellis/spec/java/`.
- `.trellis/spec/java/roadmap.md` already defines Milestone 1 deliverables,
  work breakdown, dependencies, acceptance criteria, and risks.
- The initial Java runtime web stack for the rewrite scaffold is `Spring WebFlux`.
- The build tool for the rewrite scaffold is `Maven`.
- The Java version baseline for the rewrite scaffold is `Java 21`.
- The initial database target for the rewrite scaffold is `SQLite`.

## Requirements

- Persist Milestone 1 planning in Trellis artifacts, not only in conversation.
- Break Milestone 1 into executable child tasks that can be implemented and
  verified independently.
- Capture dependencies and ordering between child tasks.
- Define acceptance criteria clear enough to gate the start of Milestone 2.
- Resolve the remaining foundational technology choices that affect the
  application skeleton.

## Constraints

- The Java version should stay aligned with current Go behavior unless a later
  design decision explicitly changes that behavior.
- The planning output should be suitable for Trellis execution, including
  PRD/design/implement artifacts for complex work.
- Milestone 1 should optimize for a safe skeleton, not early over-abstraction.

## Acceptance Criteria

- [ ] Milestone 1 planning is captured in task artifacts, not only chat.
- [ ] Milestone 1 is split into concrete child tasks with stable names.
- [ ] Each child task has a clear responsibility and dependency order.
- [ ] The blocking technology decisions for Milestone 1 are explicitly resolved
      or called out as open questions.
- [ ] The plan is detailed enough to start implementation after planning
      approval.

## Proposed Child Tasks

- `java-bootstrap-project`
- `java-config-and-validation`
- `java-persistence-baseline`
- `java-shared-response-and-exception`
- `java-security-baseline`
- `java-health-and-smoke-test`

## Out of Scope

- Milestone 2 CRUD implementation
- Relay implementation beyond Milestone 1 architectural decisions
- Provider protocol adapter implementation
- Frontend migration or frontend Java rewrite decisions

## Open Questions

- None.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
