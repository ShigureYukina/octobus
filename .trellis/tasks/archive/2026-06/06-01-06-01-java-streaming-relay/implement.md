# Implement: Java SSE Streaming 中继

## Step 1: 调整 RelayHandler 为直接返回 ServerResponse

### 1.1 修改 RelayHandler

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayHandler.java`

调整：

- `handle()` 返回类型从 `Mono<ResponseEntity<?>>` 改为 `Mono<ServerResponse>`
- 保留当前在 handler 中读取 body 与 `hasStreamFlag()` 判断的结构
- 非流式路径继续调用 `run.execute()`，并在 handler 内转换为 JSON `ServerResponse`
- 流式路径（`stream: true`）委托给 `RelayRun.executeStream()`，并在 handler 内直接构造 `text/event-stream` 响应

### 1.2 修改 RelayRouter

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayRouter.java`

调整：

- `handlerFor()` 直接返回 `handler.handle(request)`
- 删除或内联现有 `toServerResponse(ResponseEntity<?>)` 桥接逻辑
- `RelayRouter` 保持“薄路由注册器”角色，不承担响应协议分派

### 验证

- 编译通过
- 现有非流式测试不受影响

---

## Step 2: ProviderHttpClient 流式请求能力

### 2.1 扩展 ProviderHttpClient 接口

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/provider/ProviderHttpClient.java`

新增：

- `Flux<ServerSentEvent<String>> executeStream(Channel, ChannelKey, JsonNode, OutboundTransformer)`

### 2.2 实现 OpenAICompatibleProviderClient.executeStream()

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/provider/openai/OpenAICompatibleProviderClient.java`

实现：

- 使用 `webClient.post().uri(endpoint).accept(MediaType.TEXT_EVENT_STREAM)`
- 通过 `retrieve().bodyToFlux(String.class)` 逐行读取
- 解析 SSE data 行为 `ServerSentEvent<String>`
- 遇到 `[DONE]` 时完成流
- 错误时通过 `onErrorResume` 发送错误事件后终止

### 验证

- 编译通过
- 现有非流式 `postChatCompletion()` 不受影响

---

## Step 3: RelayRun 流式执行路径

### 3.1 新增 executeStream()

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayRun.java`

新增：

- `Flux<ServerSentEvent<String>> executeStream()` 方法
- 复用 `executeOpenAIChatPath()` 的认证、选路逻辑
- 对第一个可用 candidate 调用 `ProviderHttpClient.executeStream()`
- 流结束后 best-effort 记录 metrics

### 3.2 流式路径不做 failover

约束：

- 流一旦开始，不中途切换渠道
- 上游错误时发送错误事件后终止流
- 不实现首 token 超时切换（out of scope）

### 验证

- 编译通过
- 非流式 `execute()` 不受影响

---

## Step 4: 补测试

### 4.1 新增流式集成测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/relay/it/RelayStreamingIT.java`

覆盖：

- 正常流式返回：mock 上游返回多个 SSE 事件，验证客户端按序收到
- 分块返回：mock 上游分多段返回，验证客户端可按流式顺序收到
- 客户端取消：验证取消后后端不抛异常、不影响后续请求
- 非流式不受影响：验证 `stream: false` 仍走原有路径

### 4.2 保留现有非流式测试

复用：

- `RelayHandlerTests.java`
- `RelayOpenAIChatIntegrationTests.java`

---

## Validation Commands

### 精准验证

```bash
mvn -pl java -am test -Dtest=RelayStreamingIT,RelayHandlerTests,RelayOpenAIChatIntegrationTests
```

### 全量 Java 测试

```bash
mvn -pl java -am test
```

## Review Gates

在 `task.py start` 之前确认：

1. `prd.md`、`design.md`、`implement.md` 已齐备
2. 设计已明确"流式路径不做 failover"
3. 设计已明确“由 RelayHandler 在 Web 层局部分流，直接返回 ServerResponse”
4. 设计已明确"首版仅支持 OpenAI Chat Completions SSE 格式"
5. 范围仍限定在 `POST /v1/chat/completions` 的 `stream: true`

## Rollback Notes

- 若 Step 2 流式 WebClient 实现影响现有非流式路径，可先保留 `executeStream()` 空实现，回退 `RelayHandler` 的 stream 分支到 501 路径
- 若 `RelayHandler` 直接返回 `ServerResponse` 的改动引发非流式回归，可先在 handler 内只保留非流式 `ResponseEntity -> ServerResponse` 桥接，流式分支继续返回 501，避免扩大改动面
