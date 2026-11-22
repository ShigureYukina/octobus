# Java Anthropic Messages 适配器

## Goal

为 Java 后端补齐 `POST /v1/messages` 的非流式、text-only MVP 中继能力。

## Requirements

- 支持 `POST /v1/messages` 的基础请求/响应中继，满足非流式文本对话 MVP
- 支持模型、messages、system 及基础采样参数等必要字段的最小可用映射
- 若请求包含任何非 text 内容块，应明确拒绝，而不是静默忽略
- 保持与现有认证、路由和错误返回语义兼容
- 新增行为需要自动化验证，覆盖成功中继与常见错误响应转换

## Acceptance Criteria

- [ ] `POST /v1/messages` 可完成非流式、text-only 请求的成功中继
- [ ] 文本消息与基础参数可正确转发并返回可用结果
- [ ] 若请求包含非 text-only 内容，则返回明确的 4xx 错误，而不是静默丢弃内容
- [ ] 常见上游错误响应（如 400/401/429）可转换为本地可识别的错误响应
- [ ] 对应测试覆盖成功路径、认证/路由路径和错误路径

## Out of Scope

- Anthropic 流式事件协议
- thinking、tools、cache_control
- 多模态内容块与非 text-only 内容

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
