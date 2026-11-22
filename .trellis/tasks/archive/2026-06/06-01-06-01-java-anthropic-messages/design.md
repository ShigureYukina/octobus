# Design: Java Anthropic Messages 适配器

## Overview

为 `POST /v1/messages` 补齐非流式、text-only MVP 中继能力。当前 `TransformerRegistry.defaultRegistry()` 中 `ANTHROPIC_MESSAGES` 格式注册的是 `UnsupportedInboundTransformer`，出站 `ANTHROPIC_MESSAGE` 格式注册的是 `UnsupportedOutboundTransformer`；本任务将二者替换为真实实现。

保持不变的部分：

- 现有 SPI 体系（`InboundTransformer` / `OutboundTransformer` / `TransformerRegistry`）不变
- `RelayInboundFormat.ANTHROPIC_MESSAGES` 枚举值和路由注册不变
- `RelayProviderFormat.ANTHROPIC_MESSAGE` 枚举值不变
- 非流式 relay 路径（`RelayRun.execute()`）不变
- 认证、选路、熔断逻辑不变

当前代码现状：

- `RelayInboundFormat.ANTHROPIC_MESSAGES` 已注册路由 `POST /v1/messages`
- `RelayHandler` 对 `ANTHROPIC_MESSAGES` 格式的 `stream: true` 仍返回 501（正确行为，本任务不支持流式）
- `UnsupportedInboundTransformer.transform()` 抛出 `NOT_IMPLEMENTED`
- `UnsupportedOutboundTransformer` 所有方法抛出 `NOT_IMPLEMENTED`
- `OpenAICompatibleProviderClient.postChatCompletion()` 硬编码发送到 `/v1/chat/completions`，无法用于 Anthropic

## Goals

1. `POST /v1/messages` 可完成非流式、text-only 请求的成功中继
2. 文本消息与基础采样参数可正确转发并返回可用结果
3. 常见上游错误响应可转换为本地可识别的错误响应
4. 保持与现有认证、路由和错误返回语义兼容

## Non-Goals

- Anthropic 流式事件协议
- thinking、tools、cache_control
- 多模态内容块与非 text-only 内容

## Architecture

### 1. AnthropicMessagesInboundTransformer

新增 `relay/spi/AnthropicMessagesInboundTransformer.java`，实现 `InboundTransformer`。

入站转换规则（Anthropic JSON → UnifiedRequest）：

| Anthropic 字段 | UnifiedRequest 字段 | 处理 |
|---------------|---------------------|------|
| model | model | 直接复制 |
| messages[].role | messages[].role | user/assistant 直接复制 |
| messages[].content (string) | messages[].content | 包装为 TextNode |
| messages[].content[] (array) | messages[].content | 仅在所有 block 都为 type=text 时提取 text 并拼接为单个 TextNode；若出现任何非 text block，直接返回 400 |
| max_tokens | options["max_tokens"] | 透传 |
| temperature | options["temperature"] | 透传 |
| top_p | options["top_p"] | 透传 |
| stream | stream | 透传（本任务仅处理 stream=false） |
| system | messages 首个 system 消息 | 提取为 role=system 的 UnifiedMessage，插入 messages 列表头部 |

关键约束：

- `system` 字段支持 string 和 array 两种格式（Anthropic API 允许）
- array 格式的 system 仅在所有 block 都为 type=text 时提取；若出现任何非 text block，直接返回 400
- `max_tokens` 在 Anthropic API 中是必填字段，但本任务不做强制校验（上游会返回错误）
- `requestType` 设为 `UnifiedRequestType.CHAT`
- 任何非 text-only 内容均视为超出 MVP 范围，显式拒绝，不做静默降级

### 2. AnthropicMessagesOutboundTransformer

新增 `relay/spi/AnthropicMessagesOutboundTransformer.java`，实现 `OutboundTransformer`。

出站转换规则（UnifiedRequest → Anthropic JSON）：

| UnifiedRequest 字段 | Anthropic 请求字段 | 处理 |
|--------------------|-----------------|------|
| model | model | 直接复制 |
| messages (role=system) | system | 提取为顶级 system 字段（string） |
| messages (role!=system) | messages | 转为 {role, content} 数组 |
| options["max_tokens"] | max_tokens | 提取透传 |
| options["temperature"] | temperature | 提取透传 |
| options["top_p"] | top_p | 提取透传 |
| stream | stream | 透传 |

响应转换规则（Anthropic JSON → UnifiedResponse）：

| Anthropic 响应字段 | UnifiedResponse 字段 | 处理 |
|-------------------|---------------------|------|
| content[0].text | primaryContent | 包装为 JsonNode |
| model | model | 直接复制 |
| usage.input_tokens | usage.promptTokens | 直接映射 |
| usage.output_tokens | usage.completionTokens | 直接映射 |
| stop_reason | rawPayload | 保留在 rawPayload 中 |
| stop_sequence | rawPayload | 保留在 rawPayload 中 |

关键约束：

- `system` 消息从 messages 列表中提取后放到顶级字段，不在 messages 数组中
- 出站时 `content` 字段统一输出为 string 格式（非 array），因为本任务仅支持 text-only
- `cacheReadTokens` / `cacheWriteTokens` 在 Anthropic 响应中暂不映射（首版 MVP）

### 3. AnthropicProviderClient

新增 `relay/provider/anthropic/AnthropicProviderClient.java`。

与 `OpenAICompatibleProviderClient` 的关键差异：

| 差异点 | OpenAI | Anthropic |
|-------|--------|-----------|
| 端点路径 | `/v1/chat/completions` | `/v1/messages` |
| 认证头 | `Authorization: Bearer {key}` | `x-api-key: {key}` |
| 必需请求头 | 无 | `anthropic-version: 2023-06-01` |
| 响应格式 | choices[].message | content[].text |

实现方式：

- 实现 `ProviderHttpClient` 接口
- `postChatCompletion()` 方法名虽沿用，但内部发送到 `/v1/messages`
- 认证头使用 `x-api-key` 而非 Bearer Auth
- 添加 `anthropic-version: 2023-06-01` 请求头
- 响应解析委托给 `AnthropicMessagesOutboundTransformer.parseResponse()`

### 4. TransformerRegistry 注册

修改 `TransformerRegistry.defaultRegistry()`：

- 将 `ANTHROPIC_MESSAGES` 的 inbound 从 `UnsupportedInboundTransformer` 替换为 `AnthropicMessagesInboundTransformer`
- 将 `ANTHROPIC_MESSAGE` 的 outbound 从 `UnsupportedOutboundTransformer` 替换为 `AnthropicMessagesOutboundTransformer`

### 5. RelayRuntimeSupport 与 RelayAttempt 适配

当前 `RelayRuntimeSupport` 只注入了 `OpenAICompatibleProviderClient`。需要扩展：

- 新增 `AnthropicProviderClient` 依赖
- `RelayAttempt.forward()` 根据 `providerFormat` 选择对应的 provider client
- 当 `providerFormat == ANTHROPIC_MESSAGE` 时使用 `AnthropicProviderClient`

### 6. 错误响应转换

Anthropic 上游错误格式：

```json
{
  "type": "error",
  "error": {
    "type": "invalid_request_error",
    "message": "..."
  }
}
```

转换策略：

- 上游 400 → 本地 400 + 错误消息
- 上游 401 → 本地 401 + "Invalid upstream API key"
- 上游 429 → 本地 429 + 错误消息
- 上游 5xx → 本地 502 + "Upstream error"

## Testing Strategy

### 单元测试

新增：

- `relay/spi/AnthropicMessagesInboundTransformerTests.java`
  - 基础 text-only 请求转换
  - system 字段（string 和 array 格式）提取为 system 消息
  - content array 中仅提取 text 类型
  - 缺失 model 字段时的行为
- `relay/spi/AnthropicMessagesOutboundTransformerTests.java`
  - UnifiedRequest → Anthropic JSON 序列化
  - system 消息提取为顶级字段
  - Anthropic JSON → UnifiedResponse 解析
  - usage 字段映射

### 集成测试

新增：

- `relay/it/RelayAnthropicMessagesIT.java`
  - 成功中继：mock 上游返回 Anthropic 格式响应，验证客户端收到正确结果
  - 认证路径：验证 API key 认证正常工作
  - 错误路径：mock 上游返回 400/401/429，验证本地错误响应

## Risks and Mitigations

### 风险 1：Anthropic API 版本兼容性

缓解：硬编码 `anthropic-version: 2023-06-01`，这是当前最稳定的版本。后续可通过 channel 配置覆盖。

### 风险 2：system 消息提取逻辑与多轮对话冲突

缓解：仅提取 role=system 的消息，其余消息保持原序。Anthropic 的 system 是顶级字段，不影响 messages 数组中的 user/assistant 顺序；若 system 或 messages 中含非 text block，直接返回 400，避免静默改写请求语义。

### 风险 3：RelayRuntimeSupport 注入变更影响现有 OpenAI 路径

缓解：新增 `AnthropicProviderClient` 不修改现有 `OpenAICompatibleProviderClient`，`RelayAttempt.forward()` 通过 `providerFormat` 分派，不影响 OpenAI 路径。
