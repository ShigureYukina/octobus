# Implementation Plan

## Purpose

Convert the Milestone 1 roadmap into a sequence of executable Trellis child
tasks and planning gates.

## Ordered Checklist

1. Create child task `java-bootstrap-project`
2. Create child task `java-config-and-validation`
3. Create child task `java-persistence-baseline`
4. Create child task `java-shared-response-and-exception`
5. Create child task `java-security-baseline`
6. Create child task `java-health-and-smoke-test`
7. Write PRDs for all child tasks with explicit dependency notes
8. Review the child-task dependency order
9. Start only the first child that owns immediate implementation work

## Per-Task Intent

### java-bootstrap-project

- Create Maven Spring Boot scaffold
- Add baseline dependencies
- Create base package namespace and package skeleton

### java-config-and-validation

- Define typed config classes
- Define startup validation rules
- Document environment variable mapping

### java-persistence-baseline

- Configure SQLite datasource
- Add Flyway baseline structure
- Validate boot-time migration execution

### java-shared-response-and-exception

- Define success/error envelope
- Add global exception handling
- Add validation failure mapping

### java-security-baseline

- Add JWT utility/service
- Add API key extraction and auth boundary skeleton
- Keep secrets config-driven only

### java-health-and-smoke-test

- Add health endpoint
- Add startup smoke test
- Verify application boot with config + migration in place

## Validation Gates

- Planning review before `task.py start`
- Each child task must declare dependencies in its own PRD
- Do not start a downstream child before its upstream dependency is complete

## Rollback Points

- If WebFlux causes early project friction, reassess before any relay child task starts, not during relay implementation
- If the response envelope mismatches the current frontend expectation, stop and correct it before CRUD work begins
