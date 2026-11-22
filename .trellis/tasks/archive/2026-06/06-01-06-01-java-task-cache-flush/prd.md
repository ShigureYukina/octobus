# Java 后台任务与缓存刷盘

## Goal

为 Java 后端补齐后台任务机制，以及 stats / relay log 写入的内存聚合与批量刷盘能力。

## Requirements

- 提供可运行的后台任务机制，用于周期性处理 stats / relay log 相关刷盘工作
- 首版刷盘周期固定为 30s，不在本任务接入 `setting` 表动态配置
- stats 与 relay log 的写入应经过内存聚合后批量落库，而不是继续逐次直写数据库
- 应用正常关闭时，需要执行一次 best-effort flush，尽量减少缓冲区数据丢失
- 正常运行期间不改变现有读取链路的对外语义
- 新增行为需要自动化验证；纯内存组件可优先使用单元测试，刷盘链路需有集成验证

## Acceptance Criteria

- [ ] 存在可运行的后台任务机制，能够按固定周期触发 stats / relay log 刷盘
- [ ] stats / relay log 的后台刷盘周期首版固定为 30s
- [ ] stats 写入路径改为先聚合后批量刷盘，批量结果与原先直接写入语义保持一致
- [ ] relay log 写入路径改为先缓冲后批量刷盘，刷盘后数据库中可见完整日志批次
- [ ] 应用正常关闭时，会执行一次 best-effort flush
- [ ] 正常运行期间，现有读取接口无需改造即可继续工作
- [ ] 对应测试覆盖任务调度、聚合逻辑与批量刷盘结果，且异步落库相关断言通过显式 flush 完成，不依赖 sleep

## Out of Scope

- 通用缓存框架或分片缓存设施
- 动态任务配置中心或刷盘周期热更新
- 与缓存无关的其他后台任务（如渠道延迟测量、自更新）

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
