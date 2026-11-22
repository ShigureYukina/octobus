# Java Channel 模型拉取与同步

## Goal

补齐 Java 后端的 channel fetch-models / sync 能力，以及模型差异更新后的必要整理动作。

## Requirements

- 支持从上游拉取渠道模型列表，并同步到本地渠道配置
- 同步过程需要处理模型差异更新以及必要的后续整理动作
- 本任务只拥有 channel 配置及其直接关联整理动作，不拥有 `model` 表主数据生命周期
- 本任务保持 MVP 范围，不要求扩展到与本次目标无关的全量 provider 特性
- 新增能力需要自动化验证，覆盖模型拉取与同步结果

## Acceptance Criteria

- [ ] 可针对渠道触发模型拉取，并获得可用于同步的上游模型列表
- [ ] 同步执行后，本地渠道模型配置可反映上游模型差异
- [ ] 同步后的必要整理动作会执行完成，避免留下明显失配的关联数据（如失配 `group_items`）
- [ ] 对应测试覆盖模型拉取成功路径和同步后的状态变化

## Out of Scope

- 与本任务无关的高级调度策略
- 与 relay 适配器等价的全 provider 特性补齐
- 价格预设自动生成
- `model` 表增删改、模型价格主数据维护、`PriceLookup` 缓存刷新策略

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
