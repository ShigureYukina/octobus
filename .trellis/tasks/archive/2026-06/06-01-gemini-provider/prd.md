# Gemini AI 提供者

## Goal

实现 Google Gemini 模型的完整中继支持，使 Java 版 Octobus 能够通过 Gemini API 转发聊天、嵌入等请求。

## Background

- `RelayProviderFormat.GEMINI_CONTENTS` 枚举值已定义。
- `presets.json` 中已有约 19 个 Gemini 模型定价条目（如 `gemini-2.5-pro`, `gemini-2.0-flash` 等）。
- `fromChannelType("gemini/contents")` 已支持返回 `GEMINI_CONTENTS`。
- `endpointSuffix()` 对 `GEMINI_CONTENTS` 抛出 `UnsupportedOperationException("Gemini endpoint not implemented")`。
- **缺失**：Gemini 专用的 `ProviderHttpClient`、`InboundTransformer` 和 `OutboundTransformer`。
- 现有提供者：OpenAI 兼容（`OpenAICompatibleProviderClient`）、Anthropic（`AnthropicProviderClient`）。

## Requirements

### 功能

- 支持 Gemini 模型的 **Chat Completions** 格式的请求转发（`/v1/chat/completions` → Gemini API）。
- 支持**流式（SSE）和非流式**响应。
- Gemini 的 `transformers` 负责将 Octobus 统一请求格式转换为 Gemini API 请求格式，并将响应转换回统一格式。
- 成本计算：利用已有 `CostCalculator / PriceLookup` 支持 Gemini 模型定价。

### 实现方式

- 参考 `OpenAICompatibleProviderClient` 的模式，创建 `GeminiProviderClient`。
- 创建 `GeminiChatInboundTransformer` 和 `GeminiChatOutboundTransformer`。
- 在 `TransformerRegistry` 中注册新的 transformer。
- 在 `RelayRuntimeSupport` 中注册新的 provider client 工厂。

### 约束

- 不得破坏现有 OpenAI 和 Anthropic 的中继功能。
- Gemini 请求/响应的 API 细节参考 Google Gemini API 官方文档（Vertex AI 或 Gemini API 端点）。
- 错误处理：Gemini 的 HTTP 错误码需映射到 Octobus 统一错误格式。
- Gemini API key 通过渠道配置传入，与现有机制一致。

## Acceptance Criteria

- [ ] `POST /v1/chat/completions` 经过渠道类型 `gemini/contents` 可以成功转发请求到 Gemini API 并返回结果
- [ ] 流式和非流式响应均正常工作
- [ ] Gemini 模型定价正确计算并记录
- [ ] 错误场景（无效 API key、超限、模型不存在）返回友好的错误信息
- [ ] 现有 OpenAI 和 Anthropic 中继不受影响（已有测试通过）

## Notes

- Google Gemini 有两个 API 端点：`https://generativelanguage.googleapis.com/v1beta/`（公共）和 Vertex AI 端点。优先支持公共 API 端点。
- Gemini API 文档：https://ai.google.dev/api
- 流式响应使用 SSE（`text/event-stream`），格式与 OpenAI 不同，需在 transformer 中处理
