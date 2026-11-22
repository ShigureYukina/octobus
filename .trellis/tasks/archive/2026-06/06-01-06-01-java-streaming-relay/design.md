# Design: Java SSE Streaming 中继

## Overview

为 `POST /v1/chat/completions` 在 `stream: true` 场景下补齐 SSE 中继能力。当前 `RelayHandler.handle()` 对流式请求返回 501；本任务将其改为返回可消费的 SSE 事件流。

保持不变的部分：

- 非流式请求路径与现有成功路径保持兼容
- `RelayRun` 的非流式 `execute()` 不变
- `RelayInboundFormat` 枚举和 `RelayRouter` 的路由注册方式不变
- 认证、路由、熔断、session affinity 等选路逻辑不变

当前代码现状：

- `RelayHandler.handle()` 在 `hasStreamFlag(body) == true` 时直接返回 501
- `ProviderHttpClient` 只有 `postChatCompletion()` 返回 `Mono<RelayAttemptResult<JsonNode>>`
- `RelayRun.execute()` 返回 `Mono<ResponseEntity<?>>`
- `RelayRouter` 将 handler 返回值统一转为 `ServerResponse`

## Goals

1. `POST /v1/chat/completions` 在 `stream: true` 时返回 SSE 事件流
2. 客户端可按流式顺序收到上游全部事件
3. 客户端取消连接时，后端可停止流式处理
4. 非流式请求路径不受影响

## Non-Goals

- `POST /v1/messages` 的流式支持
- 与 Go 完全一致的 streaming pipeline（含首 token 超时治理、流式用量聚合）
- 流式用量聚合与高级流控能力

## Architecture

### 1. 流式检测与分流

修改 `RelayHandler.handle()`：

- 返回类型从 `Mono<ResponseEntity<?>>` 改为 `Mono<ServerResponse>`
- 当 `stream: true` 时，不再返回 501，而是在 `RelayHandler` 内部委托给 `RelayRun.executeStream()` 并直接构造 SSE `ServerResponse`
- 当 `stream: false` 或非流式端点时，继续走 `RelayRun.execute()`，再由 `RelayHandler` 在本地转换为 JSON `ServerResponse`

该分支属于 **Web 层响应分派**，不引入新的 relay 响应抽象类型。`RelayRouter` 仅负责路由注册，不承担流式/非流式响应协议分派。

### 2. RelayRun 流式执行

新增 `RelayRun.executeStream()`：

```
executeStream() → Flux<ServerSentEvent<String>>
  1. 解析请求、认证、选路（复用 executeOpenAIChatPath 的前半段逻辑）
  2. 对第一个可用 candidate 调用 ProviderHttpClient.executeStream()
  3. 将上游 SSE 事件逐条转发为 ServerSentEvent
  4. 流结束时记录 metrics（best-effort，不阻塞流）
```

关键约束：

- 流式路径不做 candidate failover（首版 MVP：流一旦开始，不中途切换渠道）
- 流式路径的 metrics 记录在流结束后 best-effort 执行，失败不影响客户端

### 3. ProviderHttpClient 流式请求

在 `ProviderHttpClient` 接口新增：

```java
Flux<ServerSentEvent<String>> executeStream(
    Channel channel,
    ChannelKey channelKey,
    JsonNode payload,
    OutboundTransformer outboundTransformer
);
```

`OpenAICompatibleProviderClient` 实现：

- 使用 `WebClient.post().uri(...).accept(MediaType.TEXT_EVENT_STREAM).retrieve()`
- 通过 `bodyToFlux(String.class)` 逐行读取 SSE data 行
- 解析每行为 `ServerSentEvent<String>`（data 字段）
- 遇到 `data: [DONE]` 时完成流
- 上游错误时通过 `onErrorResume` 发送错误事件后终止

### 4. 客户端取消处理

- `Flux.create()` 中通过 `FluxSink.onCancel()` 检测客户端断开
- 取消时关闭上游连接（WebClient 的底层连接会随 subscription cancel 自动释放）
- 不需要额外的心跳或超时机制（首版 MVP）

测试与验收重点放在：取消后服务端不抛异常、不影响后续请求；不把“必须观测到底层连接释放时点”作为硬性验收。

### 5. 流式 metrics 记录

流式请求的 metrics 记录策略：

- 流开始时不记录
- 流正常结束后，best-effort 调用 `RelayMetricsRecorder.recordSuccess()`，使用估算的 usage（如果上游在最后一个 chunk 提供了 usage）
- 流异常中断时，best-effort 调用 `RelayMetricsRecorder.recordFailure()`
- 首版不要求精确的流式用量聚合

## Testing Strategy

### 集成测试

新增 `relay/it/RelayStreamingIT.java`：

1. **正常流式返回**：mock 上游返回多个 SSE 事件，验证客户端按序收到全部事件
2. **分块返回**：mock 上游分多段返回，验证客户端可按流式顺序收到
3. **客户端取消**：验证客户端取消后后端不抛异常、不影响其他请求
4. **非流式不受影响**：验证 `stream: false` 仍走原有路径

### 测试基础设施

- 复用现有 `WebTestClient` 绑定
- Mock 上游使用 `MockWebServer`（或 WireMock）返回 SSE 流
- 验证 SSE 事件格式和顺序

## Risks and Mitigations

### 风险 1：WebClient SSE 解析与上游格式不兼容

缓解：首版仅支持 OpenAI Chat Completions 的 SSE 格式（`data: {...}\n\n` + `data: [DONE]\n\n`），这是最常见且最简单的格式。

### 风险 2：流式路径与现有非流式路径的返回类型冲突

缓解：将流式/非流式分支局部化在 `RelayHandler` 内；`RelayRouter` 仍只负责注册路由，避免把单一流式需求扩散成新的公共返回协议。

### 风险 3：流中途上游断开

缓解：通过 `onErrorResume` 发送一个包含错误信息的 SSE 事件后终止流，客户端可据此判断流异常结束。
