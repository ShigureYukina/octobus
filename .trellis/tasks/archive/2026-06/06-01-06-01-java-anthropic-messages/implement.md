# Implement: Java Anthropic Messages 适配器

## Step 1: 实现 Anthropic Inbound/Outbound Transformer

### 1.1 新增 AnthropicMessagesInboundTransformer

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/spi/AnthropicMessagesInboundTransformer.java`

要求：

- 实现 `InboundTransformer` 接口
- `inboundFormat()` 返回 `RelayInboundFormat.ANTHROPIC_MESSAGES`
- `transform(String rawBody)` 将 Anthropic JSON 转为 `UnifiedRequest`
- 支持 model、messages（text-only）、system（string/array）、max_tokens、temperature、top_p、stream
- content array 中仅提取 type=text 的 text 字段
- system 字段转为 role=system 的 UnifiedMessage 插入 messages 头部
- `requestType` 设为 `CHAT`

### 1.2 新增 AnthropicMessagesOutboundTransformer

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/spi/AnthropicMessagesOutboundTransformer.java`

要求：

- 实现 `OutboundTransformer` 接口
- `providerFormat()` 返回 `RelayProviderFormat.ANTHROPIC_MESSAGE`
- `supports(UnifiedRequestType.CHAT)` 返回 true
- `serializeRequest()` 将 UnifiedRequest 转为 Anthropic JSON
  - system 消息提取为顶级 system 字段
  - 其余消息转为 {role, content} 数组
  - options 中提取 max_tokens、temperature、top_p
- `parseResponse()` 将 Anthropic 响应转为 UnifiedResponse
  - content[0].text → primaryContent
  - usage.input_tokens → promptTokens
  - usage.output_tokens → completionTokens

### 验证

- 编译通过
- Transformer 可独立实例化

---

## Step 2: 注册 Transformer 并实现 Anthropic Provider Client

### 2.1 修改 TransformerRegistry

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/spi/TransformerRegistry.java`

调整：

- `defaultRegistry()` 中将 `ANTHROPIC_MESSAGES` 的 inbound 替换为 `AnthropicMessagesInboundTransformer`
- `defaultRegistry()` 中将 `ANTHROPIC_MESSAGE` 的 outbound 替换为 `AnthropicMessagesOutboundTransformer`

### 2.2 新增 AnthropicProviderClient

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/provider/anthropic/AnthropicProviderClient.java`

要求：

- 实现 `ProviderHttpClient` 接口
- `postChatCompletion()` 内部发送到 `/v1/messages`
- 认证头使用 `x-api-key` 而非 Bearer Auth
- 添加 `anthropic-version: 2023-06-01` 请求头
- 响应解析委托给 `AnthropicMessagesOutboundTransformer.parseResponse()`
- URL 构建逻辑：baseUrl + `/v1/messages`（与 OpenAI 类似但路径不同）

### 验证

- 编译通过
- `TransformerRegistry.defaultRegistry()` 返回的 inbound/outbound 不再是 Unsupported

---

## Step 3: RelayRuntimeSupport 与 RelayAttempt 适配

### 3.1 扩展 RelayRuntimeSupport

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayRuntimeSupport.java`

调整：

- 新增 `AnthropicProviderClient` 构造参数和 getter
- 保留现有 `OpenAICompatibleProviderClient` 不变

### 3.2 修改 RelayAttempt.forward()

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayAttempt.java`

调整：

- 当 `providerFormat == ANTHROPIC_MESSAGE` 时使用 `runtimeSupport.anthropicProviderClient()`
- 当 `providerFormat == OPENAI_CHAT_COMPLETION` 时继续使用 `runtimeSupport.openAICompatibleProviderClient()`
- 其他 providerFormat 仍返回 501

### 验证

- 编译通过
- 现有 OpenAI Chat 路径不受影响

---

## Step 4: 补测试

### 4.1 新增 Transformer 单元测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/relay/spi/AnthropicMessagesInboundTransformerTests.java`
- `java/src/test/java/io/github/shigureyukina/octobus/relay/spi/AnthropicMessagesOutboundTransformerTests.java`

覆盖：

- 入站：基础请求转换、system 提取、content array text-only 过滤
- 出站：序列化、system 提取为顶级字段、响应解析、usage 映射

### 4.2 新增集成测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/relay/it/RelayAnthropicMessagesIT.java`

覆盖：

- 成功中继路径
- 认证路径
- 错误响应转换（400/401/429）

### 4.3 保留现有测试

复用：

- `RelayHandlerTests.java`
- `RelayOpenAIChatIntegrationTests.java`

---

## Validation Commands

### 精准验证

```bash
mvn -pl java -am test -Dtest=AnthropicMessagesInboundTransformerTests,AnthropicMessagesOutboundTransformerTests,RelayAnthropicMessagesIT
```

### 全量 Java 测试

```bash
mvn -pl java -am test
```

## Review Gates

在 `task.py start` 之前确认：

1. `prd.md`、`design.md`、`implement.md` 已齐备
2. 设计已明确"仅非流式、text-only"
3. 设计已明确"system 消息提取为顶级字段"
4. 设计已明确"Anthropic 认证使用 x-api-key + anthropic-version"
5. 范围仍限定在 `POST /v1/messages` 的非流式 text-only MVP

## Rollback Notes

- 若 Transformer 实现有问题，可先回退 `TransformerRegistry` 到 `Unsupported*`，不影响其他路径
- 若 `AnthropicProviderClient` 影响现有 `OpenAICompatibleProviderClient`，可先不注入到 `RelayRuntimeSupport`，让 Anthropic 路径返回 501
