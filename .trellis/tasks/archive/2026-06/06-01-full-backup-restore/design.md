# Design — 完整备份恢复覆盖全部表

## Context

- 当前仅 `setting` 表支持导入导出。
- 前端已经有导入/导出 UI 与 hook，只是后端能力不完整。
- 全量备份恢复属于高风险数据操作，必须关注事务与兼容性。

## Scope

- 扩展导出结构，覆盖业务核心表。
- 扩展导入逻辑，支持按统一 dump 结构恢复。
- 支持可选包含日志/统计数据。
- 保持现有端点路径不变，尽量兼容前端现有调用。

## Out of Scope

- 不做跨版本复杂迁移工具。
- 不做增量备份调度系统。
- 不引入外部对象存储。

## Proposed Design

### 1. Dump Schema

- 定义统一 dump DTO：
  - `version`
  - `exportedAt`
  - `data`（按表分类）
  - 可选 `options` / `meta`

### 2. 表范围分层

- 核心业务表默认导出：
  - `app_user`
  - `groups`
  - `group_items`
  - `api_key`
  - `channel`
  - `channel_key`
  - `model`
  - `setting`
- 可选表：
  - `relay_log`
  - `stats_*`
- 不导出内部探针表。

### 3. 服务拆分

- 可新增独立 `BackupService` / `BackupRepositorySupport`，避免继续把复杂逻辑堆在 `SettingService`。
- `SettingController` 仍可保留现有端点，对外协议不变。

### 4. 导入顺序与事务

- 清空/恢复顺序遵循外键与业务依赖：
  - groups → group_items
  - channel → channel_key
  - 其他独立表按依赖顺序导入
- 整体导入置于单事务中，任一失败即回滚。

### 5. 兼容与安全

- 保持已有 `include_logs` / `include_stats` 参数语义。
- 保留版本字段，后续可用于格式升级。
- 敏感字段遵循库内现状：已哈希/已存储的值按原状导出，不额外明文转换。

## Risks and Mitigations

- **风险：大日志表导出过慢**
  - 默认不包含日志/统计，显式参数启用。
- **风险：导入顺序错误导致数据不一致**
  - 使用固定顺序和事务；补充失败回滚测试。
- **风险：现有前端依赖旧 setting-only 格式**
  - 保持端点和下载上传行为不变，只扩展文件内容结构。

## Validation Strategy

- dump schema 序列化/反序列化测试
- 成功导出导入测试
- 导入失败回滚测试
- 前端联调验证下载与上传流程
