# API Contract

> Public HTTP contract for the Java rewrite.

---

## Scope

Preserve the existing admin API and relay endpoints so the frontend and API
clients do not need behavioral changes.

---

## Required Rules

- Keep response envelopes stable for admin endpoints.
- Keep relay endpoints compatible with OpenAI-style request shapes where the
  current product already supports them.
- Keep error payloads predictable and machine-readable.
- Keep pagination and list response fields consistent across resources.

---

## High-Value Endpoints

- `/api/v1/user/login`
- `/api/v1/user/logout`
- `/api/v1/channel/*`
- `/api/v1/group/*`
- `/api/v1/apikey/*`
- `/api/v1/model/*`
- `/api/v1/log/*`
- `/api/v1/stats/*`
- `/v1/chat/completions`
- `/v1/messages`
- `/v1/responses`
- `/v1/embeddings`
- `/v1/images/generations`
- `/v1/images/edits`
- `/v1/images/variations`

---

## Scenario: Relay public model list and MVP relay endpoints

### 1. Scope / Trigger
- Trigger: Java MVP 补齐新增了 `/v1/models`、`/v1/chat/completions` streaming、`/v1/messages` Anthropic non-streaming 能力，需要把公开 HTTP 语义写成可执行约束。

### 2. Signatures
- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/messages`

### 3. Contracts

#### `GET /v1/models`
- Auth: 走 `/v1/*` API key 鉴权链路。
- API key 要求：
  - key 必须存在
  - `enabled=true`
  - `expire_at` 未过期
- 响应格式：
  - `object: "list"`
  - `data[]`
  - 每项至少包含：`id`, `object`, `created`, `owned_by`
- 公开列表语义：
  - 不是简单返回 `model` 表全部记录
  - 仅返回**既在 `model` 表存在、又存在可用 relay 路由**的模型
  - 若 API key 配置了 `supported_models`，结果还要再按白名单过滤

#### `POST /v1/chat/completions`
- 当请求体 `stream=true` 时：
  - 返回 `text/event-stream`
  - 保持 SSE chunk 顺序
  - 保持 `[DONE]` 终止行为
- 当上游在建流前直接返回错误时：
  - 不得吞错并返回假成功 SSE
  - 必须返回对应 HTTP 错误状态

#### `POST /v1/messages`
- Java MVP 仅支持 **non-streaming text-only**
- 请求内容块若包含非 text block，应直接返回 400

### 4. Validation & Error Matrix
- `/v1/models` 缺失 API key -> `401`
- `/v1/models` 非法 API key -> `401`
- `/v1/models` disabled API key -> `403`
- `/v1/models` expired API key -> `403`
- `/v1/chat/completions` `stream=true` 且上游建流失败 -> 透传映射后的错误状态（常见为 `502`）
- `/v1/chat/completions` `stream=true` 但命中当前未支持流式的 provider -> `501`
- `/v1/messages` 含非 text content block -> `400`

### 5. Good/Base/Bad Cases
- Good: `GET /v1/models` 返回 API key 可访问且 relay 可路由的模型列表
- Base: `POST /v1/chat/completions` `stream=false` 继续走原有非流式路径
- Bad: 上游流式失败后仍返回 `200 text/event-stream` 且内部把请求记为成功

### 6. Tests Required
- `ModelControllerIT`
  - 断言 `/v1/models` 需要有效 API key
  - 断言 `supported_models` 白名单过滤
  - 断言 disabled / expired key 被拒绝
- `RelayStreamingIT`
  - 断言 `stream=true` 返回 SSE
  - 断言 chunk 顺序与 `[DONE]`
  - 断言上游建流失败时返回错误状态
  - 断言命中 Anthropic channel 的 streaming 请求返回 `501`
- `RelayAnthropicMessagesIT`
  - 断言 `/v1/messages` non-streaming text-only 成功
  - 断言非 text content block 返回 `400`

### 7. Wrong vs Correct
#### Wrong
- `/v1/models` 只校验 API key 是否存在，不校验 disabled / expired
- `/v1/models` 直接暴露 `model` 表全部记录
- streaming provider client 把上游错误吞成 `event:error`，外层继续记成功

#### Correct
- `/v1/models` 复用与 relay 请求一致的 API key 有效性约束
- `/v1/models` 只暴露“在 model 表存在且当前具备可路由能力”的模型
- streaming 上游错误必须真实冒泡到 Web 层，由 HTTP 状态反映失败

---

## Scenario: Setting export/import full database dump compatibility

### 1. Scope / Trigger
- Trigger: Java admin API extended existing setting export/import endpoints from setting-only payloads to full DB dump/import while keeping frontend and Go-compatible paths unchanged.

### 2. Signatures
- `GET /api/v1/setting/export?include_logs=<bool>&include_stats=<bool>`
- `POST /api/v1/setting/import`

### 3. Contracts
- `GET /api/v1/setting/export`
  - Auth: admin JWT path
  - Returns `ApiResponse<DBDump>` JSON body
  - Adds `Content-Disposition: attachment; filename=octobus-backup-<timestamp>.json`
  - `include_logs=false` and `include_stats=false` by default
- `DBDump` body contains at least:
  - `version`
  - `exported_at`
  - core business arrays for `model`, `setting`, `channel`, `channel_key`, `groups`, `group_items`, `api_key`
  - optional `relay_log`, `stats_total`, `stats_daily`, `stats_hourly`, `stats_apikey`, `stats_channel`
- `POST /api/v1/setting/import`
  - accepts `multipart/form-data` with `file` field containing JSON dump
  - also accepts JSON request body for compatibility and tests
  - accepts both wrapped `{code,message,data}` payloads and raw dump JSON
  - returns standard `ApiResponse` envelope with import result metadata
  - setting-only legacy JSON import remains accepted as a fallback path

### 4. Validation & Error Matrix
- invalid/missing JWT -> `401`
- malformed JSON dump -> `400`
- unsupported dump version -> `400`
- multipart request missing `file` -> `400`
- transactional import conflict / invalid row -> `400` or mapped server error, and database state must roll back

### 5. Good/Base/Bad Cases
- Good: export with `include_logs=true&include_stats=true` returns full downloadable dump
- Base: importing old setting-only JSON still restores settings successfully
- Bad: import partially writes channels/groups/settings before failing and leaves the database in mixed state

### 6. Tests Required
- `BackupIntegrationTests`
  - export core-only dump
  - export with logs/stats included
  - multipart import restores persisted data
  - raw JSON import restores persisted data
  - rollback case leaves original database untouched after failure

### 7. Wrong vs Correct
#### Wrong
- create brand-new backup endpoints and break the frontend’s hardcoded `/api/v1/setting/export` and `/api/v1/setting/import` calls
- accept only JSON body import and reject multipart upload

#### Correct
- preserve the setting endpoint paths and extend their behavior to full dump/import
- support both multipart file upload and JSON-body compatibility modes
