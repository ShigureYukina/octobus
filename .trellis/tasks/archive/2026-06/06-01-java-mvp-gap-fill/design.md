# Design: P0/P1 功能补齐

## Architecture Overview

六个功能按顺序实施，每个子功能可独立验证。整体不改变现有 Java 架构风格：

```
┌─────────────────────────────────────────────────┐
│                   RelayRouter                     │
│  POST /v1/chat/completions (Mono + Flux<SSE>)    │
│  POST /v1/messages                     (Mono)     │
└──────────────┬──────────────────────┬────────────┘
               │                      │
     ┌─────────▼──────┐    ┌──────────▼──────────┐
     │ RelayHandler    │    │ StreamingRelayHandler│
     │ (non-streaming) │    │ (Flux<ServerSentEvent>)│
     └────────┬────────┘    └──────────┬──────────┘
              │                        │
     ┌────────▼────────┐     ┌─────────▼─────────┐
     │ RelayRun        │     │ RelayRun+stream    │
     │ (execute)       │     │ (executeStream)    │
     └────────┬────────┘     └─────────┬─────────┘
              │                        │
              └──────────┬─────────────┘
                         │
              ┌──────────▼──────────────┐
              │ TransformerRegistry     │
              │ Inbound → Outbound      │
              │ (OpenAI Chat, Anthropic)│
              └─────────────────────────┘
```

---

## 1. 后台任务框架

### 方案
使用 Spring `@EnableScheduling` + `@Scheduled` 替代 Go 的自定义 task 框架。

### 任务列表

| 任务 | 注解间隔 | 说明 |
|------|---------|------|
| `statsFlushTask` | 30s | Flush stats 内存累加器到 DB |
| `logFlushTask` | 30s | Flush log 缓冲队列到 DB |
| (后续) `modelSyncTask` | 1h | 同步上游模型列表（Phase 6） |
| (后续) `channelDelayTask` | 1h | 测量渠道延迟（后续增强） |

### 配置方式
- 间隔暂时硬编码在 `@Scheduled` 注解中
- 后续可迁移到 `setting` 表动态配置

### 新增文件
- `internal/task/TaskConfiguration.java` — `@Configuration @EnableScheduling`
- `internal/task/StatsFlushTask.java` — 刷 stats 累加器
- `internal/task/LogFlushTask.java` — 刷 log 缓冲队列

---

## 2. SSE Streaming 中继

### 方案

在现有 `RelayHandler` 中添加流式检测路径：当 `stream: true` 时，不走 `Mono<ResponseEntity>` 路径，而是返回 `Flux<ServerSentEvent<?>>`。

### 数据流（仅 `POST /v1/chat/completions`）

```
请求 (stream: true)
  → RelayHandler.handle()
    → RelayRun.executeStream()
      → ProviderHttpClient 发送 POST 请求 (Accept: text/event-stream)
      → 逐行读取 SSE data 行
      → Flux.create() + FluxSink 逐条 emit
      → RelayRouter 包装为 ServerSentEvent
      → 客户端收到 SSE 流
```

### 关键设计点

1. **Handler 返回类型**：`RelayRouter` 中注册的 handler 返回 `ServerResponse`，非流式用 `ServerResponse.ok().body(Mono)`，流式用 `ServerResponse.ok().body(Flux<ServerSentEvent>)`（sse media type）
2. **范围限制**：Streaming MVP 仅覆盖 `POST /v1/chat/completions`；`POST /v1/messages` 在本任务中仅支持非流式 text-only
3. **Provider 请求**：`ProviderHttpClient` 新增 `executeStream()` 方法，返回响应行流（使用 `java.io.BufferedReader` 或 Spring `WebClient` 的 `exchangeToFlux`）
4. **错误处理**：流中间出现上游错误 → 发送错误事件后 close()
5. **客户端断开**：通过 `Sink.onCancel()` 或 `Subscription.cancel()` 检测

### 新增/修改文件

- `internal/relay/RelayStreamingHandler.java` — 流式 handler
- `internal/relay/provider/ProviderHttpClient.java` — 新增 `executeStream()`
- `internal/relay/RelayRouter.java` — 注册流式路由
- `internal/relay/RelayRun.java` — 新增 `executeStream()` 方法

---

## 3. Anthropic Messages 适配器

### 方案

利用现有 SPI 体系，新增 `AnthropicMessagesInboundTransformer` 和 `AnthropicMessagesOutboundTransformer`，然后在 `TransformerRegistry.defaultRegistry()` 中注册。

### 支持的 API 字段（仅非流式 text-only）

**入站（Anthropic → Unified）**：

| Anthropic 字段 | UnifiedRequest 字段 | 处理 |
|---------------|---------------------|------|
| model | model | 直接复制 |
| messages[].role | messages[].role | user/assistant/system |
| messages[].content (string) | messages[].content | 直接复制 |
| messages[].content[] (array) | messages[].content | 仅 text 类型 |
| max_tokens | options["max_tokens"] | 透传 |
| temperature | options["temperature"] | 透传 |
| top_p | options["top_p"] | 透传 |
| stream | stream | 透传 |
| system | messages 首个 system 消息 | 转为 role=system 消息 |

**出站（Unified → Anthropic）**：

| UnifiedRequest | Anthropic HTTP 请求 | 处理 |
|---------------|--------------------|------|
| model | model | 直接复制 |
| messages | messages | 非流式 |
| options | max_tokens/temperature/top_p | 提取透传 |
| stream | stream | 透传 |
| system role message | system 字段 | 提取为顶级 system |

**响应转换**：

| Anthropic 响应字段 | UnifiedResponse 字段 |
|-------------------|---------------------|
| content[].text | primaryContent (JsonNode) |
| model | model |
| usage.input_tokens | usage.promptTokens |
| usage.output_tokens | usage.completionTokens |
| stop_reason | 透传至 rawPayload |
| stop_sequence | 透传至 rawPayload |

### 依赖
- 需要 Go 的反向转换逻辑（Anthropic → OpenAI → Anthropic 无需，Java 直接 Anthropic → Unified → Anthropic 即可，不需要 OpenAI 做中间格式）
- 请求路径：POST /v1/messages，转发到上游 baseUrl + /v1/messages
- 认证头：x-api-key（上游渠道的 key）

### 新增文件
- `internal/relay/spi/AnthropicMessagesInboundTransformer.java`
- `internal/relay/spi/AnthropicMessagesOutboundTransformer.java`
- `internal/relay/provider/anthropic/AnthropicProviderClient.java`

---

## 4. Model CRUD + /v1/models

### 方案

基于现有 `model` 表和 `ModelRepository`，在 `ModelController` 中补全 CRUD 端点，新增 `/v1/models` 端点。

### 新增端点

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/v1/model/create` | 创建 model 记录 |
| POST | `/api/v1/model/update` | 更新 model 价格 |
| POST | `/api/v1/model/delete` | 删除 model 记录 |
| GET | `/api/v1/model/channel` | 按渠道列出所有模型 |
| POST | `/api/v1/model/update-price` | 刷新价格（后续接入 presets） |
| GET | `/api/v1/model/last-update-time` | 最后更新时间 |
| GET | `/v1/models` | OpenAI 兼容模型列表 |

### /v1/models 响应格式

```json
{
  "object": "list",
  "data": [
    {
      "id": "gpt-4o",
      "object": "model",
      "created": 1700000000,
      "owned_by": "octopus"
    }
  ]
}
```

- 数据来源：`model` 表（所有有价格记录的模型）
- 可选：支持 `APIKeyAuth` 中间件，根据 API Key 的 `supported_models` 过滤

### 修改文件
- `internal/controller/ModelController.java`
- `internal/persistence/ModelRepository.java` — 新增 CRUD 方法
- `internal/domain/ModelService.java`

---

## 5. 缓存层（Stats/Log 缓冲）

### 方案

在内存中累加 stats 更新，批量暂存 log 条目，由后台定时任务刷盘。

### Stats 累加器

```java
@Component
public class StatsAccumulator {
    // 每个 stats 表一个 ConcurrentHashMap
    // key: 表标识 (total→1, daily→date, hourly→hour+date, etc.)
    // value: AtomicLong 或 volatile 字段
    private final AtomicLong totalInput = new AtomicLong();
    private final AtomicLong totalOutput = new AtomicLong();
    // ... 每个 metric 一个 AtomicLong
    
    // 对于 channel/apikey 维度，用 ConcurrentHashMap<Integer, StatsMetrics>
    private final ConcurrentHashMap<Integer, StatsMetrics> channelAccum = new ConcurrentHashMap<>();
    
    // 累加方法（在 RelayRun 中调用）
    void recordTotalMetrics(RecordMetrics metrics);
    void recordDailyMetrics(RecordMetrics metrics);
    void recordChannelMetrics(int channelId, RecordMetrics metrics);
    
    // 快照并重置（由 @Scheduled 任务调用）
    StatsSnapshot takeSnapshotAndReset();
}
```

### Log 缓冲

```java
@Component
public class LogBuffer {
    private final ConcurrentLinkedQueue<RelayLog> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger();
    
    void append(RelayLog log);       // RelayRun 写入
    List<RelayLog> drainAll();        // 刷盘任务批量取出
}
```

### 刷盘任务

```java
@Component
public class StatsFlushTask {
    @Scheduled(fixedRate = 30_000)
    void flushStats() {
        StatsSnapshot snapshot = accumulator.takeSnapshotAndReset();
        // 批量写入 DB（5 个 stats 表的 INSERT/UPDATE）
    }
}

@Component
public class LogFlushTask {
    @Scheduled(fixedRate = 30_000)
    void flushLogs() {
        List<RelayLog> batch = buffer.drainAll();
        // 批量 INSERT relay_log
    }
}
```

### 修改的文件
- `internal/task/StatsFlushTask.java` — 新增
- `internal/task/LogFlushTask.java` — 新增
- `internal/relay/metrics/RelayMetricsRecorder.java` — 改为写入累加器/缓冲而不是直接 DB
- `internal/persistence/StatsRepository.java` — 改为支持批量写入
- `internal/persistence/LogRepository.java` — 改为支持批量写入

---

## 6. Channel fetch-models / sync

### 方案

新增 `ProviderModelFetcher` 根据渠道 type 调用不同提供商的 /models 端点，`SyncModelsTask` 定时或手动触发同步流程。

### 模型获取

```java
@Component
public class ProviderModelFetcher {
    List<String> fetchModels(Channel channel);
    // 根据 channel.type() 分派:
    //   openai → GET {baseUrl}/models (Authorization: Bearer {key})
    //   anthropic → GET {baseUrl}/models (x-api-key, Anthropic-Version)
    //   其他 provider 首版不纳入 acceptance，后续单独评审
}
```

### 同步流程

```
POST /api/v1/channel/sync 或 @Scheduled 1h
  → SyncModelsTask.sync()
    → 遍历所有 channel
      → ProviderModelFetcher.fetchModels(channel)
      → 与当前 channel.model 对比差异
      → 有变化时:
        → update channel.model
        → 删除已消失的 group_items
        → 自动分组 (ChannelAutoGroup)
```

### Auto-Group 逻辑

| AutoGroupType | 匹配规则 |
|--------------|---------|
| NONE (0) | 不分组 |
| EXACT (2) | 模型名 == 组名 |
| FUZZY (1) | 模型名 contains 组名 |
| REGEX (3) | 模型名 matches 组正则 |

### 新增文件
- `internal/helper/ProviderModelFetcher.java` — 拉取模型列表
- `internal/task/SyncModelsTask.java` — 同步调度
- `internal/helper/ChannelAutoGroup.java` — 自动分组逻辑

---

## Cross-cutting Concerns

### 测试策略

每个子功能独立验证：
1. 后台任务：验证 Bean 装配与显式 `flushOnce()` 语义，不依赖 `@Scheduled` tick 时序
2. Streaming：`RelayStreamingHandler` 集成测试，使用 `WebTestClient` 验证 SSE 事件流
3. Anthropic：集成测试，使用 `StubUpstream` 模拟 **非流式 text-only** Anthropic 响应
4. Model CRUD：`WebTestClient` 测试所有 CRUD 端点
5. 缓存层：验证 StatsAccumulator 累加正确、flush 后 DB 数据正确
6. Channel sync：使用 `StubUpstream` 模拟 /models 端点，验证 channel.model 更新、group_items 清理与 auto-group，不把 model 主数据维护纳入本 child 默认验收

### 错误处理
- Streaming 中断：客户端断开时自动 cancel，不要求在本任务中补齐流式用量聚合
- API 格式错误：统一返回 400 + 错误描述
- 上游不可达：触发熔断器，切换候选渠道

### 兼容性
- 所有新增端点不影响现有 API 行为
- 缓存层改造后，现有写入路径行为不变（flush 频率保证数据可见性）
