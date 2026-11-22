# Configuration

> Configuration rules for the Java rewrite.

---

## Rules

- Keep runtime configuration in YAML.
- Use environment variables for secrets and deployment-specific overrides.
- Validate required values at startup.
- Prefer explicit defaults over implicit null behavior.

---

## Required Settings

- Server port and host
- Database path / URL
- JWT secret
- Relay timeouts and retry policy
- Cache and circuit breaker tuning

---

## Scenario: Dual database and embedded web settings

### 1. Scope / Trigger
- Trigger: Java MVP now supports SQLite/MySQL switching and optional static frontend hosting, so env keys and default wiring must be captured as executable config contracts.

### 2. Signatures
- `octobus.database.type`: `sqlite` | `mysql`
- `octobus.database.url`: JDBC URL
- `octobus.database.username`: optional string, used for MySQL-like datasources
- `octobus.database.password`: optional string, used for MySQL-like datasources
- `octobus.web.enabled`: boolean
- `octobus.web.static-dir`: filesystem path
- `octobus.web.index-file`: relative file name under `static-dir`

### 3. Contracts
- Default database mode is `sqlite`.
- `sqlite` mode:
  - `octobus.database.url` points to a SQLite JDBC URL such as `jdbc:sqlite:./data/octobus.db`
  - Flyway locations must resolve to `classpath:db/migration/sqlite`
- `mysql` mode:
  - `octobus.database.url` points to a MySQL-compatible JDBC URL
  - runtime datasource uses username/password when provided
  - Flyway locations must resolve to `classpath:db/migration/mysql`
- `octobus.web.enabled=true` enables static frontend file serving from `octobus.web.static-dir`
- `octobus.web.index-file` is used only for SPA fallback routes, not API/relay paths
- Environment override naming follows Spring relaxed binding and repo convention:
  - `OCTOBUS_DATABASE_TYPE`
  - `OCTOBUS_DATABASE_URL`
  - `OCTOBUS_DATABASE_USERNAME`
  - `OCTOBUS_DATABASE_PASSWORD`
  - `OCTOBUS_WEB_ENABLED`
  - `OCTOBUS_WEB_STATIC_DIR`
  - `OCTOBUS_WEB_INDEX_FILE`

### 4. Validation & Error Matrix
- missing `octobus.database.url` -> startup validation failure
- unsupported `octobus.database.type` -> config binding failure at startup
- `mysql` mode with invalid URL/credentials -> datasource/Flyway startup failure
- `octobus.web.enabled=true` but static dir missing -> frontend routes return `404`, admin/relay APIs remain available

### 5. Good/Base/Bad Cases
- Good: `type=mysql` + MySQL JDBC URL + username/password boots with MySQL Flyway tree only
- Base: default config boots in SQLite mode and keeps previous behavior
- Bad: both SQLite and MySQL migration trees are scanned together, producing duplicate Flyway versions

### 6. Tests Required
- `ConfigurationValidationTests`
  - assert invalid config fails before serving requests
  - use fresh temp SQLite DB to avoid checksum pollution from reused local DB files
- `OctobusApplicationTests`
  - assert default SQLite config still boots and migrates
- `StaticFrontendIntegrationTests`
  - assert `octobus.web.static-dir` overrides the served frontend root
- `MysqlCompatibilityIntegrationTests`
  - assert `type=mysql` selects MySQL migration locations and datasource wiring

### 7. Wrong vs Correct
#### Wrong
- keep all migration files in one `db/migration` root while adding MySQL duplicates with the same `V*` versions
- hardcode static frontend paths in controller code

#### Correct
- switch Flyway locations by database type (`sqlite` vs `mysql`)
- keep web hosting configurable under `octobus.web.*` and isolated in config/router wiring
