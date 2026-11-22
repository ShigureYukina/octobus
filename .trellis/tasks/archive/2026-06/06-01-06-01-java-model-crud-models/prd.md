# Java Model CRUD 与模型列表

## Goal

补齐 Java 后端管理端 model CRUD，以及 `GET /v1/models` 的 OpenAI 兼容模型列表接口。

## Requirements

- 管理端具备 model 的完整增删改查能力
- 对外提供 `GET /v1/models`，返回 OpenAI 兼容的模型列表格式
- 不因本任务引入与价格预设生成或其他 provider 专有格式耦合的额外范围
- 新增接口需要自动化验证，覆盖管理端行为与公共列表行为

## Acceptance Criteria

- [ ] 管理端可完成 model 的 list / create / update / delete 基础操作
- [ ] `GET /v1/models` 返回 OpenAI 兼容的 `object=list` + `data[]` 结构
- [ ] 新增或更新后的模型能够体现在模型列表结果中
- [ ] 对应测试覆盖 CRUD 成功路径、基础校验与模型列表格式

## Out of Scope

- 价格预设自动生成
- Anthropic 专有模型列表响应格式
- 与本任务无关的 provider 价格同步逻辑

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
