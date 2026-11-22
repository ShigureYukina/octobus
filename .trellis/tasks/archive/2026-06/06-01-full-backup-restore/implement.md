# Implement Plan — 完整备份恢复覆盖全部表

## Execution Checklist

1. 审查现有 `SettingController` / `SettingService` 的 export/import 实现
2. 设计并新增 dump DTO
3. 抽离或新增备份服务，聚合各 repository 数据
4. 实现按表顺序导出与导入
5. 加入 `include_logs` / `include_stats` 控制
6. 为导入流程加事务与版本校验
7. 增加成功/失败回滚测试
8. 验证前端现有导入导出交互无须改动或仅最小改动

## Validation Commands

- `cd java && mvn test`
- 若有 controller/integration 测试，定向执行 setting/backup 相关测试

## Review Gates

- Gate 1：dump schema 是否足够稳定、可扩展
- Gate 2：默认导出表范围是否合理
- Gate 3：导入事务和回滚测试是否覆盖高风险场景

## Rollback Points

- 新增备份服务与 DTO 可独立回退
- 若全表恢复不稳定，可暂时保留 setting-only 旧逻辑作为兜底分支
