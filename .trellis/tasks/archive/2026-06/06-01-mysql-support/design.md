# Design — MySQL 数据库支持

## Context

- 当前 Java 后端仅支持 SQLite，使用 `JdbcTemplate` + Flyway。
- 现有 SQL、迁移脚本、连接初始化均默认 SQLite。
- 目标是在不破坏 SQLite 的前提下增加 MySQL 支持。

## Scope

- 增加数据库类型配置。
- 按数据库类型选择 DataSource。
- 为 Flyway 提供 MySQL 迁移路径。
- 修正仓库 SQL 中与 MySQL 不兼容的部分。

## Out of Scope

- 不把整个持久层迁移到 JPA。
- 不在本任务中切到 R2DBC 全响应式持久层。
- 不处理 PostgreSQL 等额外数据库。

## Proposed Design

### 1. 数据库配置模型

- 扩展 `DatabaseProperties`：
  - `type`：`sqlite` / `mysql`
  - `url`
  - `username`
  - `password`
  - 可选连接池参数
- 默认仍为 SQLite，保持现有行为。

### 2. DataSource 选择

- 在 `PersistenceConfiguration` 中根据 `type` 创建不同 DataSource：
  - SQLite：保留当前实现
  - MySQL：使用标准 JDBC MySQL DataSource / HikariCP

### 3. Flyway 迁移组织

- 保留现有 SQLite 迁移目录。
- 新增 MySQL 专用迁移目录，版本号保持语义一致。
- Flyway locations 根据数据库类型动态设置。

### 4. SQL 兼容策略

- 审核各 `Repository`：
  - upsert 语法
  - 时间函数/布尔值映射
  - 分页/limit
  - JSON 文本字段处理
- 若差异较小，使用条件 SQL 分支；若差异集中，可抽出少量方言辅助函数。

### 5. 事务与一致性

- 沿用 Spring 事务管理。
- 涉及多表写入的操作在 MySQL 下行为应与 SQLite 一致。

## Compatibility

- 默认配置仍启动 SQLite。
- 现有 repository 接口与 service 层接口尽量不变。
- Flyway 版本编号不分叉，仅按方言提供实现。

## Risks and Mitigations

- **风险：迁移脚本语义不一致**
  - 对照 SQLite schema 逐表校验字段、索引、唯一约束。
- **风险：SQL 方言遗漏导致运行时失败**
  - 为关键 CRUD 与统计查询增加 MySQL 集成测试。
- **风险：阻塞 JDBC 与 WebFlux 模型不完全匹配**
  - 维持现有架构，不扩大变更范围；后续再评估 R2DBC。

## Validation Strategy

- SQLite 回归测试。
- MySQL 核心 CRUD 集成测试。
- Flyway 启动迁移验证。
- 渠道、模型、统计、设置等高频路径烟雾验证。
