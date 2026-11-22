# Relay Protocol Adapter

> Rules for converting between client-facing and provider-facing LLM protocols.

---

## Scope

This is the most important Java rewrite spec. It defines how OpenAI, Anthropic,
Gemini, Doubao, and OpenAI Responses formats are normalized and emitted.

---

## Required Conversions

- OpenAI Chat Completions inbound and outbound.
- OpenAI Responses inbound and outbound.
- OpenAI Embeddings inbound and outbound.
- OpenAI Images inbound and outbound.
- Anthropic Messages inbound and outbound.
- Gemini Contents outbound.
- Doubao outbound.

---

## Stream Rules

- Preserve SSE chunk ordering.
- Preserve `[DONE]` termination behavior where applicable.
- Aggregate usage only when the provider supplies it or when the adapter can
  derive it safely.
- Convert tool calls and multimodal content through the shared unified model,
  not through ad-hoc endpoint code.

---

## Adapter Principle

- Normalize all requests into one unified model before provider routing.
- Convert provider responses back into the original client-facing API shape.
- Keep provider-specific quirks isolated in adapter implementations.

---

## Scenario: Streaming and Anthropic Messages MVP boundaries

### 1. Scope / Trigger
- Trigger: Java MVP 同时补齐 OpenAI Chat SSE 中继与 Anthropic Messages non-streaming 适配，需要固定 provider 分发和错误传播约束。

### 2. Signatures
- `ProviderHttpClient.postChatCompletion(Channel, ChannelKey, JsonNode, OutboundTransformer)`
- `ProviderHttpClient.executeStream(Channel, ChannelKey, JsonNode, OutboundTransformer)`

### 3. Contracts
- 请求统一先转 `UnifiedRequest`，再按 `RelayProviderFormat` 分发到 provider client。
- non-streaming 与 streaming **都必须**按同一 `RelayProviderFormat` 选择 provider client，禁止在流式路径硬编码 client。
- 当前 Java MVP provider 边界：
  - `OPENAI_CHAT_COMPLETION`: 支持 non-streaming + SSE streaming
  - `ANTHROPIC_MESSAGE`: 支持 non-streaming text-only；`executeStream()` 当前不支持
- Anthropic Messages inbound 仅接受 text-only 内容：
  - `content` 为 string 可接受
  - `content[]` 仅 `type=text` 可接受
  - 其他 block 类型直接 400

### 4. Validation & Error Matrix
- provider format 未注册 outbound transformer -> `501`
- streaming 命中 `ANTHROPIC_MESSAGE` -> `501`
- OpenAI upstream 建流前返回 `400` -> `400`
- OpenAI upstream 建流前返回 `401` -> `401`
- OpenAI upstream 建流前返回 `429` -> `429`
- OpenAI upstream 建流前返回其他非 2xx -> `502`
- Anthropic upstream `429` -> `429`
- Anthropic upstream `5xx/其他` -> `502`

### 5. Good/Base/Bad Cases
- Good: `stream=true` 的 OpenAI chat 请求按顺序转发 SSE，失败时返回真实错误状态
- Base: Anthropic `/v1/messages` 成功中继 text-only 请求，保留原始响应体
- Bad: 流式路径把上游失败转换成正常完成的 SSE，导致 breaker / sticky / metrics 误记为成功

### 6. Tests Required
- `RelayStreamingIT`
  - 成功流式透传
  - 上游流式失败状态映射
  - Anthropic channel + `stream=true` -> `501`
- `AnthropicMessagesInboundTransformerTests`
  - string content
  - text block array
  - 非 text block -> `400`
- `AnthropicMessagesOutboundTransformerTests`
  - `system` 提升到顶级字段
  - usage/response 解析
- `RelayAnthropicMessagesIT`
  - header (`x-api-key`, `anthropic-version`) 断言
  - `429` 状态映射断言

### 7. Wrong vs Correct
#### Wrong
- `executeStream()` 内部吞掉异常并输出一个 error event 后正常结束
- Relay streaming 路径无条件走 OpenAI provider client

#### Correct
- `executeStream()` 对建流前失败直接抛出带状态的异常，由 Web 层返回错误状态
- Relay streaming / non-streaming 路径统一按 `RelayProviderFormat` 分发；当前未支持的 provider 显式返回 `501`

---

## Scenario: Gemini contents outbound through OpenAI chat route

### 1. Scope / Trigger
- Trigger: Java relay now supports Gemini public API for `/v1/chat/completions` by reusing the existing OpenAI-style inbound path and adding Gemini-specific outbound/provider handling.

### 2. Signatures
- Inbound: `POST /v1/chat/completions`
- Channel type: `gemini/contents`
- Upstream endpoints:
  - `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
  - `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse`

### 3. Contracts
- Request normalization remains `OpenAI Chat -> UnifiedRequest`
- Gemini outbound adapter must serialize text-chat requests as:
  - top-level `systemInstruction.parts[].text` for system prompt
  - `contents[].role` = `user` / `model`
  - `contents[].parts[].text`
  - optional `generationConfig` from normalized options (`temperature`, `top_p`, `max_tokens`)
- Non-streaming Gemini responses must be converted back into an OpenAI-compatible chat completion JSON body before returning to the client
- Streaming Gemini responses must be converted into OpenAI-style SSE chat chunks and preserve `[DONE]`
- Upstream auth uses `x-goog-api-key`

### 4. Validation & Error Matrix
- unsupported outbound transformer for `gemini/contents` -> `501`
- Gemini upstream `400` -> `400`
- Gemini upstream `403` -> `403`
- Gemini upstream `404` -> `404`
- Gemini upstream `429` -> `429`
- Gemini upstream `5xx/other non-2xx` before usable response -> `502`
- `stream=true` still enters relay streaming only through `OPENAI_CHAT_COMPLETION` inbound route; Anthropic remains `501`

### 5. Good/Base/Bad Cases
- Good: OpenAI-style chat request on a `gemini/contents` channel returns OpenAI-compatible non-stream JSON or SSE chunks
- Base: `stream=false` continues through normal non-stream relay accounting path
- Bad: return raw Gemini JSON/SSE to an OpenAI chat client without adapting the response shape

### 6. Tests Required
- `GeminiContentsOutboundTransformerTests`
  - assert system/user/assistant mapping into Gemini payload
  - assert Gemini usage/content is converted into OpenAI-compatible completion JSON
- `RelayGeminiContentsIT`
  - assert `x-goog-api-key` header
  - assert non-stream response adaptation
  - assert stream chunk translation + `[DONE]`
- `TransformerRegistryTests`
  - assert Gemini resolves to a real outbound transformer, not guarded fallback
- `RelayStreamingIT`
  - assert Anthropic streaming remains `501`

### 7. Wrong vs Correct
#### Wrong
- add Gemini by returning raw provider payloads directly to `/v1/chat/completions` clients
- broaden streaming support for Anthropic while touching Gemini dispatch

#### Correct
- keep OpenAI chat inbound stable and isolate Gemini quirks inside the Gemini provider client and outbound transformer
- preserve existing provider boundaries while adding Gemini only where explicitly supported
