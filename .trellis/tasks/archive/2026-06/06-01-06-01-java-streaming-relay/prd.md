# Java SSE Streaming 中继

## Goal

为 Java 后端补齐 `POST /v1/chat/completions` 在 `stream: true` 场景下的 SSE 中继能力。

## Requirements

- 仅要求 `POST /v1/chat/completions` 在 `stream: true` 时返回可消费的 SSE 响应
- 非流式请求路径与现有成功路径保持兼容，不因本任务退化
- 流式链路需要有自动化验证，覆盖正常返回、分块返回和客户端取消等核心场景

## Acceptance Criteria

- [ ] `POST /v1/chat/completions` 在请求体包含 `stream: true` 时不再返回 501
- [ ] 客户端能够持续接收上游流式事件，而不是等待完整响应后一次性返回
- [ ] 上游分多段返回时，客户端可按流式顺序收到全部事件
- [ ] 客户端取消连接时，后端可停止相应流式处理而不影响其他请求

## Out of Scope

- `POST /v1/messages` 的流式支持
- 与 Go 完全一致的 streaming pipeline
- 首 token 超时治理
- 流式用量聚合与高级流控能力

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
