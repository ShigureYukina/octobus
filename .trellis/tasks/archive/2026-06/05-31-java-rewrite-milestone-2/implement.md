# Implementation Plan

## Purpose

Convert the Milestone 2 roadmap into a sequence of executable Trellis child
tasks and planning gates.

## Ordered Checklist

1. Write PRDs for all child tasks with explicit dependency notes
2. Start child task `java-user-auth` (the blocker for all others)
3. After user-auth completes, start CRUD tasks in parallel where possible
4. Start `java-model-log-stats` after user-auth

## Per-Task Intent

### java-user-auth
- Implement user entity and repository
- Add bcrypt password hashing
- Implement login endpoint
- Implement logout endpoint
- Wire admin JWT auth into the existing filter skeleton

### java-channel-crud
- Channel entity + Flyway migration
- CRUD endpoints for channels
- Search/list with pagination

### java-group-crud
- Group entity + Flyway migration
- CRUD endpoints for groups
- Group-item relationship management

### java-apikey-crud
- API key entity + Flyway migration
- CRUD endpoints for API keys
- API key format generation

### java-setting-crud
- Setting entity + Flyway migration
- CRUD endpoints for system settings

### java-model-log-stats
- Model query endpoint
- Log query endpoints with filtering/pagination
- Stats aggregation endpoints

## Validation Gates

- Planning review before task.py start for each child
- Each child task must declare dependencies in its own PRD
- Complete and verify user-auth before starting CRUD tasks

## Rollback Points

- If the auth design turns out incompatible with the frontend expectations,
  stop and correct user-auth before CRUD work begins
- If response envelope changes needed, fix them before multiple CRUD tasks
  duplicate the wrong pattern
