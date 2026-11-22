# Database & Persistence

> Entity, repository, and migration rules for the Java rewrite.

---

## Rules

- Keep entity mappings separate from HTTP DTOs.
- Wrap multi-row updates in transactions.
- Keep migrations explicit and ordered.
- Avoid hidden persistence side effects inside controller code.

---

## Required Areas

- User, channel, group, apikey, setting, log, stats, and model tables.
- Relay usage and log persistence.
- Migration strategy for schema changes.

---

## PriceLookup Cache Semantics

`PriceLookup` is a `@Component` that loads all rows from the `model`
table into an in-memory `Map<String, ModelPrice>` cache at construction
time. The cache is immutable once loaded (`Map.copyOf`).

**`refresh()`** replaces the cache by re-reading the database. This is
critical in integration tests where model rows are seeded in `@BeforeEach`
(after the Spring context and `PriceLookup` are initialized).

**Lookup key**: `PriceLookup.priceFor(modelName)` is called with
`candidate.modelName()`, which equals `group_items.model_name`.
Therefore the `model.name` primary key must match the model name in
`group_items`, not the upstream response body's `model` field.

```java
// CORRECT — seed under the group_item model name
jdbc.update("INSERT OR IGNORE INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)", "gpt-4o", 1.0, 2.0, 0.5, 1.0);

// WRONG — seeding under the upstream response model name won't match
jdbc.update("INSERT OR IGNORE INTO model ...", "gpt-4o-ch1", ...);
```

---

## Scenario: Full database backup/restore import ordering and rollback

### 1. Scope / Trigger
- Trigger: Java admin setting export/import now restores multiple tables instead of settings only, so persistence ordering and transactional behavior must be explicit.

### 2. Signatures
- `BackupService.exportAll(boolean includeLogs, boolean includeStats)`
- `BackupService.importDump(DBDump dump)`
- repository bulk helpers for backup restore (insert/replace/findAll/export helpers)

### 3. Contracts
- Export core tables by default: `model`, `setting`, `channel`, `channel_key`, `groups`, `group_items`, `api_key`
- Export `relay_log` only when `includeLogs=true`
- Export stats tables only when `includeStats=true`
- Import runs in one transaction and must restore tables in FK-safe order:
  1. `model`, `setting`, `channel`, `groups`, `api_key`
  2. `channel_key`, `group_items`
  3. optional logs/stats tables
- Delete/replace order must be the inverse of FK dependencies
- Legacy setting-only import compatibility is allowed, but full dump import is the default contract

### 4. Validation & Error Matrix
- dump missing required core arrays -> reject import or treat as compatibility mode explicitly
- row conflict / malformed referenced row during import -> transaction rollback, no partial persistence
- logs/stats omitted from dump -> keep those tables untouched or replace only when explicitly importing them, according to service contract

### 5. Good/Base/Bad Cases
- Good: import full dump with channels + keys + groups + items in one transaction and recover the same graph
- Base: import a setting-only legacy dump without touching unrelated tables
- Bad: insert `group_items` before `groups`/`channel`, or clear parent rows after children, causing FK or graph corruption

### 6. Tests Required
- `BackupIntegrationTests`
  - core export shape
  - optional logs/stats export shape
  - multipart import success
  - JSON body import success
  - rollback on failing import
- persistence-level verification must assert restored dependent rows (`channel_key`, `group_items`, stats rows) after import

### 7. Wrong vs Correct
#### Wrong
- implement backup restore by bloating `SettingService` with cross-table repository orchestration
- write several tables outside a transaction and hope later failures are rare

#### Correct
- keep cross-table orchestration in a dedicated backup service
- wrap full import in one transaction and restore in FK-safe order
