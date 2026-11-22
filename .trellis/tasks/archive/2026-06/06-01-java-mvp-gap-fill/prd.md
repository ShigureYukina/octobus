# 填补 Java 后端 P0/P1 功能差距

## Goal

对照 Go 后端，补齐 Java 实现中缺失的核心功能（P0）和重要管理功能（P1），使 Java 后端达到功能可用的 MVP 状态。

该任务作为 **parent task**，负责维护总范围、子任务映射、跨子任务约束与最终集成验收；实际实现应在对应 child task 中完成。

## Background

对照当前 Go 后端能力，Java 后端仍存在以下功能缺口：

### P0 — 核心可用性缺口

1. **SSE Streaming 中继缺失**
   - 现有 Java relay 路径对流式请求无法返回可用的 SSE 响应。

2. **Anthropic Messages 入口缺失**
   - `POST /v1/messages` 当前不可用，需要补齐到可中继的 MVP 状态。

3. **后台定时任务缺失**
   - stats 与 relay log 相关的后台周期性处理与刷盘机制尚未建立。

### P1 — 管理与运维能力缺口

4. **Model 管理能力不完整**
   - 当前缺少完整的 model CRUD，以及面向客户端的 `/v1/models` 公共模型列表接口。

5. **写入聚合缓存层缺失**
   - stats 与 relay log 写入目前没有内存聚合与批量刷盘机制。

6. **Channel fetch-models / sync 缺失**
   - 当前无法从上游拉取模型列表，也缺少渠道模型同步能力。

## Child Task Map

| 子任务 | 路径 | 范围 |
|---|---|---|
| 后台任务与缓存刷盘 | `.trellis/tasks/06-01-06-01-java-task-cache-flush` | 后台任务机制、stats/log 写入聚合与批量刷盘 |
| SSE Streaming 中继 | `.trellis/tasks/06-01-06-01-java-streaming-relay` | `POST /v1/chat/completions` 的 `stream: true` SSE 中继 |
| Anthropic Messages 适配器 | `.trellis/tasks/06-01-06-01-java-anthropic-messages` | `POST /v1/messages` 的非流式、text-only MVP |
| Model CRUD 与模型列表 | `.trellis/tasks/06-01-06-01-java-model-crud-models` | 管理端 model CRUD 与 `GET /v1/models` |
| Channel 模型拉取与同步 | `.trellis/tasks/06-01-06-01-java-channel-model-sync` | fetch-models / sync 与模型差异整理 |

## Requirements

- 优先级按 P0 > P1 执行
- 每个子功能独立可交付，不要求一次性全部完成
- 保持 Java 现有架构风格（Spring WebFlux, JdbcTemplate, domain service 层）
- 不为本次 MVP 引入新的复杂抽象或额外运行时依赖，除非单独评审
- 新增功能需要对应自动化验证：接口行为优先集成测试，纯内存组件可使用单元测试验证
- 与 Go 对齐的目标是补齐 MVP 可用性，不要求在本任务中一次性补齐全部高级特性

## Acceptance Criteria

- [ ] 上述 5 个子任务均完成各自规划，并在进入执行前具备清晰、互不重叠的交付边界
- [ ] SSE Streaming 中继：`POST /v1/chat/completions` 在请求体包含 `stream: true` 时返回可消费的 SSE 响应，而不是 501 或非流式一次性响应
- [ ] Anthropic Messages 适配器：`POST /v1/messages` 可完成**非流式、text-only** 请求的中继；不要求支持 thinking、tools、cache_control 或 Anthropic 流式事件协议
- [ ] 后台刷盘任务：存在可运行的后台任务机制，能够周期性处理 stats / relay log 相关刷盘工作
- [ ] Model CRUD：管理端具备完整增删改查能力，并提供 `GET /v1/models` 的 OpenAI 兼容模型列表接口
- [ ] 缓存层：stats 与 relay log 写入经过内存聚合后批量刷盘；正常运行期间不改变现有读取链路的对外语义
- [ ] Channel fetch-models / sync：支持从上游拉取模型列表并同步到本地渠道配置，包含模型差异更新与必要的后续整理动作
- [ ] 最终集成 review 确认各子任务组合后仍满足 parent PRD 的 MVP 范围与 out-of-scope 约束

## Out of Scope

- OpenAI Responses / Embeddings / Images 适配器（P2+）
- Gemini / Doubao 适配器（P2+）
- MySQL / PostgreSQL 支持（P3）
- 自更新机制（不适用于克隆项目）
- 价格预设自动生成（P3）
- Snowflake ID / 分片缓存等工具设施（后续优化）
- Anthropic 的 thinking / tools / cache_control / 流式事件协议支持
- 与 Go 当前 streaming pipeline 的完整逐层对齐

## 已决策

### 决策 1：MVP 范围优先于完整对齐
- 本任务先补齐 P0/P1 的 MVP 可用性，不追求一次性完全复刻 Go 的高级特性。

### 决策 2：Anthropic 适配器范围
- `POST /v1/messages` 在本任务中仅支持**非流式、text-only** 请求/响应。
- 不支持 thinking、tools、cache_control 和 Anthropic 流式事件协议。

### 决策 3：Streaming 范围
- Streaming MVP 仅要求补齐可消费的 SSE 中继能力。
- 不要求在本任务中实现与 Go 一致的完整 streaming pipeline、首 token 超时治理或流式用量聚合。

### 决策 4：缓存层范围
- 本任务的缓存层仅覆盖 stats 与 relay log 的写入聚合与批量刷盘。
- 不要求扩展为通用缓存设施，也不要求改造现有读取链路。
