# 补齐 Java 后端剩余 P0/P1 功能缺口

## Goal

对照 Go 后端，补齐 Java 实现中 `06-01-java-mvp-gap-fill` 未覆盖的剩余 P0/P1 功能缺口，使 Java 后端具备与 Go 基本一致的核心中继能力与管理端功能。

## Background

上阶段（`06-01-java-mvp-gap-fill`）完成了 5 个已知缺口的补齐。当前差距分析发现仍有以下未被覆盖的缺口：

### P0 — 核心中继缺口（5 项）

1. **Anthropic Messages 流式中继缺失**
   - 现有实现仅支持非流式，`AnthropicProviderClient.executeStream()` 抛出 `UnsupportedOperationException`
2. **OpenAI Responses 中继缺失**
   - `POST /v1/responses` 返回 501
3. **OpenAI Embeddings 中继缺失**
   - `POST /v1/embeddings` 返回 501
4. **OpenAI Image Generation 中继缺失**
   - `POST /v1/images/generations` 返回 501
5. **OpenAI Image Edit / Variation 中继缺失**
   - `POST /v1/images/edits` 和 `/v1/images/variations` 返回 501

### P1 — 管理端点缺口（12 项）

6. **Log 管理端点不完整**
   - 缺少 `DELETE /api/v1/log/clear`（清空日志）
   - 缺少 `GET /api/v1/log/stream`（SSE 实时日志推送）
   - 缺少 `GET /api/v1/log/stream-token`（流 token 管理）
7. **内部管理端点缺失**
   - 缺少 `POST /api/internal/flush`（手动触发缓存落盘）
8. **Stats 维度不完整**
   - 缺少 `GET /api/v1/stats/apikey`（虽然 DB 有数据但无 API 暴露）
9. **APIKey 外部端点缺失**
   - 缺少 `GET /api/v1/apikey/stats`（API Key 自身统计）
   - 缺少 `GET /api/v1/apikey/login`（API Key 验证登录）
10. **Model 管理端点不完整**
    - 缺少 `GET /api/v1/model/channel`（渠道可用模型列表）
    - 缺少 `POST /api/v1/model/update-price`（远程更新价格表）
    - 缺少 `GET /api/v1/model/last-update-time`（价格最后更新时间）
11. **Setting 端点不完整**
    - 缺少 `GET /api/v1/setting/export`（数据库 JSON 备份）
    - 缺少 `POST /api/v1/setting/import`（数据库 JSON 恢复）

### P1 — 后台任务缺口（2 项）

12. **BaseURL 延迟检测任务缺失**
    - Go 每小时检查所有渠道 base URL 延迟，Java 从未主动采集，`selectBaseUrl()` 使用 delay 字段时永远为 NULL
13. **价格表自动更新任务缺失**
    - Go 定时从远程源拉取最新模型价格，Java 仅支持手动录入

### P1 — 基础设施缺口（2 项）

14. **param_override 在管线中未使用**
    - 已存入 DB 但在 relay 转发时从未合并到请求体中
15. **CORS 中间件未生效**
    - 设置表中有 `cors_allow_origins` 键但无实际 CORS 过滤器

## Requirements

- 优先级按 P0 > P1 执行
- 保持现有架构风格（Spring WebFlux, JdbcTemplate, domain service 层）
- 新增功能需要对应自动化验证
- 中继相关改动不破坏现有 `/v1/chat/completions` 非流式和流式行为
- Log stream（SSE）复用现有 log 查询能力，不做全量推送

## Acceptance Criteria

### P0
- [ ] Anthropic Messages 流式：`POST /v1/messages` + `stream: true` 返回可消费的 SSE 流
- [ ] OpenAI Responses：`POST /v1/responses` 可正常中继
- [ ] OpenAI Embeddings：`POST /v1/embeddings` 可正常中继
- [ ] OpenAI Image Generation：`POST /v1/images/generations` 可正常中继
- [ ] OpenAI Image Edit/Variation：`POST /v1/images/edits`、`/v1/images/variations` 可正常中继

### P1
- [ ] Log 管理：clear、stream、stream-token 三个端点可用
- [ ] Internal flush 端点可用
- [ ] Stats apikey 端点可用
- [ ] APIKey stats/login 两个端点可用
- [ ] Model channel/update-price/last-update-time 三个端点可用
- [ ] Setting export/import 两个端点可用
- [ ] BaseURL 延迟检测任务：定时 ping 渠道 base URL 并更新 delay
- [ ] 价格自动更新任务：定时从远程源拉取模型价格
- [ ] param_override 在 relay 管线中生效
- [ ] CORS 过滤器根据 `cors_allow_origins` 生效

## Out of Scope

- Gemini / Doubao 提供者适配器（P2+）
- MySQL / PostgreSQL 支持（P3）
- 自更新机制（P3）
- 价格预设自动生成（P3，复用 Go 的 updatePrice.py）
- 静态前端文件服务（P3）
- Snowflake ID / 分片缓存等工具设施
- 流式首 token 超时切换通道
- 日志 SSE 推送的鉴权细化
