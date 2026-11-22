# Design — Gemini AI 提供者

## Context

- 当前已存在 `GEMINI_CONTENTS` 枚举与部分模型价格数据。
- 缺少 Gemini 请求发送、请求/响应转换与错误映射实现。
- 现有实现可参考 OpenAI compatible 与 Anthropic 两条 provider 路径。

## Scope

- 实现 Gemini provider client。
- 实现至少一条可用的 chat/completions 对接路径。
- 支持同步与流式响应。
- 接入现有成本、日志、熔断、负载均衡链路。

## Out of Scope

- 不在首轮补齐所有 Gemini 专有能力（如文件、缓存、工具的全部高级特性）。
- 不同时支持 Vertex AI 与公共 Gemini API 两套鉴权模型；优先公共 Gemini API。

## Proposed Design

### 1. Provider 定位

- 渠道类型 `gemini/contents` 映射到 `GEMINI_CONTENTS`。
- 新增 `GeminiProviderClient`，负责：
  - 构造目标 URL
  - 注入鉴权头/查询参数
  - 发起 HTTP 请求
  - 统一处理同步/流式响应

### 2. Transformer 策略

- 入站：将统一请求或 OpenAI Chat 请求映射到 Gemini `generateContent` / `streamGenerateContent` 结构。
- 出站：将 Gemini 响应映射回统一响应，再适配为 OpenAI 风格返回。
- 首轮重点覆盖：
  - system/user/assistant 文本消息
  - usage / token 统计
  - 流式增量文本

### 3. 错误映射

- 解析 Gemini 错误体。
- 映射为项目统一错误结构，保留状态码与核心 message。

### 4. 兼容与注册

- 在 `TransformerRegistry` 注册 Gemini 相关 transformer。
- 在 provider 选择逻辑中接入 Gemini client。
- 保证现有 OpenAI / Anthropic 路径零改动或最小改动。

## Data Flow

1. 下游请求进入 `/v1/chat/completions`
2. 根据渠道类型识别为 Gemini provider
3. 入站 transformer 转换为 Gemini 请求
4. provider client 调用 Gemini API
5. 出站 transformer 转回统一 / OpenAI 风格响应
6. 继续走现有日志、成本、统计链路

## Risks and Mitigations

- **风险：Gemini 与 OpenAI 消息模型差异较大**
  - 首轮只支持文本对话主路径，复杂 multimodal/tool 行为显式限制。
- **风险：流式协议格式不同**
  - 参考官方文档和现有流式抽象，先覆盖最小稳定子集。
- **风险：usage 字段缺失或格式不同**
  - 对缺失情况提供降级解析，不阻塞主请求。

## Validation Strategy

- transformer 单测
- provider client 错误映射测试
- relay 端到端测试（非流式 / 流式）
- 回归 OpenAI / Anthropic 测试
