# Implement Plan — MySQL 数据库支持

## Execution Checklist

1. 审查 `DatabaseProperties`、`PersistenceConfiguration`、Flyway 初始化点
2. 增加数据库类型与 MySQL 连接配置
3. 引入 MySQL JDBC 依赖与 DataSource 创建逻辑
4. 新增 MySQL 迁移脚本 V1-V8
5. 审查并修正 repository SQL 方言差异
6. 增加 SQLite/MySQL 双场景测试
7. 验证默认 SQLite 行为未回归

## Validation Commands

- `cd java && mvn test`
- 如有条件，增加针对 MySQL profile 的测试命令
- 启动应用分别在 sqlite/mysql 配置下完成健康检查

## Review Gates

- Gate 1：配置模型是否兼容现有默认值
- Gate 2：Flyway 目录与版本策略是否清晰
- Gate 3：是否已覆盖 upsert/统计查询等高风险 SQL

## Rollback Points

- DataSource 切换逻辑可单独回退为 SQLite-only
- MySQL 迁移目录与依赖可独立移除，不影响现有 SQLite 运行
