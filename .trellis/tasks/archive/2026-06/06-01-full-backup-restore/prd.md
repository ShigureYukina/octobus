# 完整备份恢复覆盖全部表

## Goal

在 Java 版 Octobus 中实现全数据库表的备份与恢复功能，替换当前仅覆盖 `setting` 表的不完整方案。

## Background

- 当前仅 `GET /api/v1/setting/export` 和 `POST /api/v1/setting/import` 可用，只导出导入 settings 的键值对。
- 数据库共 15 张表：`app_bootstrap_probe`, `model`, `relay_log`, `stats_total`, `stats_daily`, `stats_hourly`, `stats_apikey`, `stats_channel`, `app_user`, `groups`, `group_items`, `api_key`, `channel`, `channel_key`, `setting`。
- Go 版本的备份恢复覆盖全部表，以 JSON dump 格式导出。
- 前端 `useExportDB()` 和 `useImportDB()` 已存在，使用 HTTP 文件下载/上传的方式。

## Requirements

### 功能

- `GET /api/v1/setting/export`（扩展） — 导出全部表数据为一个 JSON 文件。
- `POST /api/v1/setting/import`（扩展） — 从 JSON 文件恢复全部表数据。
- 导出格式为结构化 JSON，包含元数据（版本、导出时间）和各表的数据数组。
- 导入支持全量替换和增量模式（可选）。

### 数据结构

导出 JSON 格式建议（兼容 Go 现有格式）：
```json
{
  "version": 1,
  "exported_at": "2026-06-01T00:00:00Z",
  "data": {
    "model": [...],
    "app_user": [...],
    "groups": [...],
    "group_items": [...],
    "api_key": [...],
    "channel": [...],
    "channel_key": [...],
    "setting": [...]
  }
}
```

注意：不导出 `relay_log`、`stats_*` 等日志/统计表（可根据配置选择包含），`app_bootstrap_probe` 也不导出。

### 约束

- 导出/导入使用已有的认证机制（JWT Bearer token）。
- 导入操作在事务中执行，任一表恢复失败则全部回滚（SQLite 支持事务）。
- 恢复时检查版本兼容性。
- 不暴露密码等敏感信息的明文（用户密码已有哈希存储；渠道 key 需确认处理方式）。
- 前端已有的 `useExportDB()` / `useImportDB()` hook 可以直接对接（当前它们使用原生 fetch 直接访问 API）。

## Acceptance Criteria

- [ ] `GET /api/v1/setting/export?include_logs=false&include_stats=false` 导出指定表的 JSON dump
- [ ] `POST /api/v1/setting/import` 从上传的 JSON 文件恢复数据
- [ ] 导出 JSON 包含版本号和导出时间元数据
- [ ] 导入失败时事务回滚，数据库保持导入前状态
- [ ] 前端导入导出功能在对接后的管理界面中正常工作

## Notes

- 现有 SettingService 中的 `exportAll()` 和 `importDump()` 需要扩展或新建服务类。
- 需要考虑大表（relay_log、stats_hourly）的导出性能，可考虑分页或按时间范围导出。
- Go 版本中此功能位于 `internal/server/handlers/setting.go` 和 `internal/op/setting.go`。
