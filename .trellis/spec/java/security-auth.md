# Security & Auth

> Authentication and access-control rules for the Java rewrite.

---

## Rules

- Use a dedicated JWT secret from configuration.
- Do not derive signing keys from user credentials.
- Keep API key format stable and deterministic for clients.
- Enforce request authentication at the controller/filter boundary.

---

## Requirements

- JWT issue and verify flows.
- Login and logout behavior.
- API key creation and lookup.
- Access checks for admin endpoints and relay requests.

---

## Scenario: `/v1/*` public relay-adjacent endpoints reuse relay API key validity

### 1. Scope / Trigger
- Trigger: Java 新增 `/v1/models` 公共端点，它虽然不是实际 relay forwarding，但属于 `/v1/*` 公共能力，必须与 relay API key 校验保持一致。

### 2. Signatures
- `GET /v1/models`
- Relay request family under `/v1/*`

### 3. Contracts
- `/v1/models` 不得只做“key 是否存在”检查。
- `/v1/models` 必须复用与 relay 请求同等级的 API key 有效性语义：
  - key 存在
  - key enabled
  - key 未过期

### 4. Validation & Error Matrix
- Missing API key -> `401`
- Invalid API key -> `401`
- Disabled API key -> `403`
- Expired API key -> `403`

### 5. Good/Base/Bad Cases
- Good: disabled / expired key 无法访问 `/v1/models`
- Base: 正常 key 可访问 `/v1/models`，再叠加 `supported_models` 过滤
- Bad: 一个不能调用 relay 的 key 仍可枚举公共模型列表

### 6. Tests Required
- `ModelControllerIT`
  - disabled key -> `403`
  - expired key -> `403`
  - valid key -> `200`

### 7. Wrong vs Correct
#### Wrong
- `/v1/models` 自己写一套简化鉴权，只查库不校验 enabled / expireAt

#### Correct
- `/v1/models` 与 relay 请求保持一致的 API key 有效性约束，避免权限边界漂移
