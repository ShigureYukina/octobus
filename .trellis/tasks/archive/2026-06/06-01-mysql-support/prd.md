# MySQL 数据库支持

## Goal

在 Java 版 Octobus 中增加 MySQL 数据库支持，使部署者可以在 SQLite 和 MySQL 之间自由选择，满足高并发生产环境对数据库性能的要求。

## Background

- 当前仅支持 SQLite，通过 `SQLiteDataSource` + Spring JDBC `JdbcTemplate` 操作。
- 项目使用 WebFlux 响应式栈，但当前 JDBC 操作是阻塞的（SQLite 本质也是阻塞的）。
- Flyway 用于数据库迁移，已有 V1-V8 共 8 个迁移脚本，全部基于 SQLite DDL。
- MySQL 在 Go 版本中也是受支持的数据库之一。

## Requirements

### 功能

- Java 后端支持 MySQL 作为数据库后端，与 SQLite 可配置切换。
- 所有八个 Flyway 迁移脚本有对应的 MySQL 版本。
- 所有仓库（`*Repository`）的 JdbcTemplate 查询在 MySQL 上正确运行。
- 数据库类型选择通过配置文件或环境变量切换，默认 SQLite。

### 实现方式

- MySQL 方言的 Flyway 迁移脚本放入 `db/migration/mysql/` 目录。
- `PersistenceConfiguration.java` 根据配置选择 `SQLiteDataSource` 或 MySQL 的 `DataSource`（可使用 HikariCP 或响应式方式）。
- 仓库层查询语句需检查 MySQL 兼容性（如 `LIMIT` 语法、`INSERT ... ON DUPLICATE KEY UPDATE` 等）。

### 约束

- 不得损害 SQLite 的现有功能；两种数据库必须同时可工作。
- 测试需要覆盖两种数据库（至少一种经过 CI 验证）。
- MySQL 连接信息可通过环境变量配置（`OCTOBUS_DATABASE_*` 前缀或 Spring 标准 `spring.datasource.*`）。
- 考虑到 WebFlux 响应式栈，评估使用 `r2dbc-mysql` 的可能性；如果复杂度太高，可接受基于 JDBC 的 MySQL 连接（使用 `spring-jdbc` + HikariCP 作为折中方案，需隔离在非响应式路径）。

## Acceptance Criteria

- [ ] 提供 MySQL 版本的 Flyway 迁移脚本（V1-V8）
- [ ] 通过配置 `octobus.database.type=mysql` 和对应的 URL/用户/密码即可切换到 MySQL
- [ ] 所有 CRUD 操作在 MySQL 下正常（用户、渠道、API Key、分组、模型、日志、统计、设置）
- [ ] SQLite 模式不受影响，所有现有测试通过
- [ ] 新增 MySQL 场景的集成测试（至少覆盖核心 CRUD）

## Notes

- 现有 Flyway 迁移在 `java/src/main/resources/db/migration/` 下
- 可考虑使用 Flyway 的 `locations` 配置来根据数据库类型选择不同迁移目录
- 统计相关查询（`stats_hourly`, `stats_daily`）涉及复杂 SQL，需重点验证 MySQL 兼容性
