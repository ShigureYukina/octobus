# Implement: P0/P1 功能补齐

## Parent Task Note

当前任务已拆分为 parent/child 结构。`task.py start` 不应直接以本 parent 作为实现目标；后续执行应在对应 child task 中分别完成。

本文件仅保留 child task 的总体排序、共享依赖与集成约束；各 child 的具体实施步骤以各自 `implement.md` 为准。本文件中的旧 phase 拆分不应再作为直接实现脚本使用。

### 建议执行顺序

1. `.trellis/tasks/06-01-06-01-java-task-cache-flush`
2. `.trellis/tasks/06-01-06-01-java-streaming-relay`、`.trellis/tasks/06-01-06-01-java-anthropic-messages`、`.trellis/tasks/06-01-06-01-java-model-crud-models`（可并行）
3. `.trellis/tasks/06-01-06-01-java-channel-model-sync`（最后执行，复用前序 shared infrastructure）

### 共享依赖约束

- `java-task-cache-flush` 拥有 `task/` 调度基础设施；其他 child 只复用，不重复创建 `TaskConfiguration`
- `java-model-crud-models` 拥有 `model` 表主数据写路径与 `/v1/models` 对外语义
- `java-channel-model-sync` 默认不写 `model` 表主数据，不维护 `PriceLookup` 生命周期
- `java-streaming-relay` 与 `java-anthropic-messages` 共享 relay 入口层，合并前需统一检查 `RelayRouter` / `RelayHandler` 改动

## Phase 1: 后台任务框架

### 步骤 1.1：添加 @EnableScheduling + 空任务

```java
@Configuration
@EnableScheduling
public class TaskConfiguration {
}
```

验证：启动时不报错。

### 步骤 1.2：实现 StatsFlushTask

- `@Scheduled(fixedRate = 30_000)` 
- 初始实现：直接调用 `StatsRepository` 现有写入方法
- 后续 Phase 5 改为刷新累加器

### 步骤 1.3：实现 LogFlushTask

- `@Scheduled(fixedRate = 30_000)`
- 初始实现：空操作（无缓冲可刷新）
- 后续 Phase 5 改为刷新缓冲队列

### 新增文件
- `internal/task/TaskConfiguration.java`
- `internal/task/StatsFlushTask.java`
- `internal/task/LogFlushTask.java`

### 验证
```
mvn -pl java -am test -Dtest=OctobusApplicationTests
```
确认应用启动不报错。

---

## Phase 2: SSE Streaming 中继

### 步骤 2.1：HttpClient 添加流式请求支持

在 `ProviderHttpClient` 中添加：

```java
public Flux<String> executeStream(URI uri, String requestBody, Map<String, String> headers) {
    return webClient.post()
        .uri(uri)
        .headers(h -> headers.forEach(h::set))
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(requestBody)
        .retrieve()
        .bodyToFlux(String.class);
}
```

使用 Spring WebClient 的 SSE 支持，自动按行解码 SSE data。

### 步骤 2.2：RelayRun 添加流式执行路径

新增 `executeStream()` 方法：

```java
public Flux<String> executeStream() {
    return providerClient.executeStream(upstreamUri, rawBody, headers)
        .doOnCancel(() -> log.debug("Client disconnected from stream"))
        .doOnError(e -> log.error("Stream error", e));
}
```

### 步骤 2.3：RelayRouter 注册流式分支

保持 `POST /v1/chat/completions` 原有入口不变，在现有 handler 中根据请求体 `stream` 标志切换非流式 / 流式响应路径。

`POST /v1/messages` 不在本阶段引入流式支持，仍按非流式路径处理。

### 步骤 2.4：集成测试

新增 `RelayStreamingIT`：

- TC1: stream=true 请求返回 SSE 事件流
- TC2: 上游返回多个 SSE chunk，验证客户端收到全部事件
- TC3: 客户端断开，验证后端正确 cancel

### 修改文件
- `internal/relay/provider/ProviderHttpClient.java`
- `internal/relay/RelayRun.java`
- `internal/relay/RelayRouter.java`
- `internal/relay/RelayHandler.java`

### 验证
```
mvn -pl java -am test
```
新测试 + 老测试均通过。

---

## Phase 3: Anthropic Messages 适配器

### 步骤 3.1：InboundTransformer

```java
public class AnthropicMessagesInboundTransformer implements InboundTransformer {
    @Override
    public UnifiedRequest transform(String rawBody) throws ApiException {
        // 解析 Anthropic JSON 请求
        // 提取: model, messages (role/content), max_tokens, temperature, top_p, stream, system
        // 构建 UnifiedRequest (requestType=ANTHROPIC_MESSAGE)
    }
}
```

### 步骤 3.2：OutboundTransformer

```java
public class AnthropicMessagesOutboundTransformer implements OutboundTransformer {
    @Override
    public RelayAttemptResult execute(UnifiedRequest request, RelayCandidate candidate) {
        // 将 UnifiedRequest 转换回 Anthropic HTTP 请求
        // 发送到上游
        // 解析响应为 UnifiedResponse
    }
    
    @Override
    public RelayProviderFormat providerFormat() {
        return RelayProviderFormat.ANTHROPIC_MESSAGE;
    }
}
```

### 步骤 3.3：注册到 TransformerRegistry

在 `TransformerRegistry.defaultRegistry()` 中替换 ANTHROPIC_MESSAGES inbound 和 ANTHROPIC_MESSAGE outbound。

### 步骤 3.4：新增 AnthropicProviderClient

或复用现有 `OpenAICompatibleProviderClient`（如果 Anthropic API 兼容）。

### 步骤 3.5：集成测试

新增 `RelayAnthropicIT`：

- TC1: POST /v1/messages 非流式 text-only，返回正确响应
- TC2: API key 验证、模型路由
- TC3: 错误响应转换（400/401/429）

### 新增文件
- `internal/relay/spi/AnthropicMessagesInboundTransformer.java`
- `internal/relay/spi/AnthropicMessagesOutboundTransformer.java`

### 修改文件
- `internal/relay/spi/TransformerRegistry.java`
- `internal/relay/RelayRouter.java` — 注册 /v1/messages 路由（确认已注册）

### 验证
```
mvn -pl java -am test
```

---

## Phase 4: Model CRUD + /v1/models

### 步骤 4.1：ModelRepository 添加 CRUD

```java
public interface ModelRepository {
    List<ModelInfo> findAll();
    ModelInfo findByName(String name);
    void create(ModelInfo model);
    void update(ModelInfo model);
    void delete(String name);
}
```

### 步骤 4.2：ModelService 添加业务方法

```java
public class ModelService {
    public ModelInfo findByName(String name);
    public void create(ModelInfo model);
    public void update(String name, Double input, Double output, ...);
    public void delete(String name);
    public List<ModelInfo> listByChannel(int channelId);
}
```

### 步骤 4.3：ModelController 添加端点

新增方法：create, update, delete, channel, last-update-time, /v1/models

### 步骤 4.4：集成测试

- TC1-4: CRUD 端点功能测试
- TC5: /v1/models 返回正确格式的模型列表

### 修改文件
- `internal/persistence/ModelRepository.java`
- `internal/domain/ModelService.java`
- `internal/controller/ModelController.java`

### 验证
```
mvn -pl java -am test
```

---

## Phase 5: 缓存层 (Stats/Log 缓冲)

### 步骤 5.1：实现 StatsAccumulator

```java
@Component
public class StatsAccumulator {
    // AtomicLong 累加器 for total/daily/hourly
    // ConcurrentHashMap<Integer, StatsMetrics> for channel/apikey
    
    public void record(RecordMetrics metrics, int channelId, int apiKeyId);  // RelayRun 调用
    
    // 内部数据结构
    static class StatsAccum {
        AtomicLong inputToken, outputToken;
        AtomicLong inputCost, outputCost;
        AtomicLong waitTime, success, failed, cacheHit;
    }
}
```

### 步骤 5.2：实现 LogBuffer

```java
@Component
public class LogBuffer {
    private final ConcurrentLinkedQueue<RelayLog> queue = new ConcurrentLinkedQueue<>();
    
    public void append(RelayLog log);     // RelayRun 调用
    public List<RelayLog> drainAll();       // LogFlushTask 调用
}
```

### 步骤 5.3：修改 RelayMetricsRecorder

从直接写 DB 改为写入 StatsAccumulator + LogBuffer。

### 步骤 5.4：改造 StatsFlushTask

从 StatsAccumulator 取出快照，批量写 DB。

### 步骤 5.5：改造 LogFlushTask

从 LogBuffer drain 批量，批量 INSERT relay_log。

### 步骤 5.6：集成测试

- TC1: 多次 relay 后等待 flush，验证 DB 数据正确
- TC2: flush 期间应用不阻塞
- TC3: 应用关闭前全量 flush

### 新增文件
- `internal/task/StatsAccumulator.java`
- `internal/task/LogBuffer.java`

### 修改文件
- `internal/task/StatsFlushTask.java`
- `internal/task/LogFlushTask.java`
- `internal/relay/metrics/RelayMetricsRecorder.java`
- `internal/persistence/StatsRepository.java`
- `internal/persistence/LogRepository.java`
- `internal/domain/StatsService.java`
- `internal/domain/LogService.java`

### 验证
```
mvn -pl java -am test
```

---

## Phase 6: Channel fetch-models / sync

### 步骤 6.1：实现 ProviderModelFetcher

```java
@Component
public class ProviderModelFetcher {
    public List<String> fetchModels(Channel channel) {
        // 根据 channel.type() 选择 fetch 方法
    }
}
```

### 步骤 6.2：实现 ChannelAutoGroup

```java
public class ChannelAutoGroup {
    public void autoGroup(Channel channel, List<String> models);
    // EXACT / FUZZY / REGEX 匹配
}
```

### 步骤 6.3：实现 SyncModelsTask

```java
@Component
public class SyncModelsTask {
    @Scheduled(fixedRate = 3_600_000) // 1h
    public void sync();
    // 手动触发：POST /api/v1/channel/sync
}
```

### 步骤 6.4：ChannelController 添加端点

POST /api/v1/channel/fetch-model, POST /api/v1/channel/sync, GET /api/v1/channel/last-sync-time

### 步骤 6.5：集成测试

- TC1: StubUpstream 模拟 /models 端点，验证 fetch-model
- TC2: sync 流程验证（channel model 更新、group_item 清理）

### 新增文件
- `internal/helper/ProviderModelFetcher.java`
- `internal/helper/ChannelAutoGroup.java`
- `internal/task/SyncModelsTask.java`

### 修改文件
- `internal/controller/ChannelController.java`
- `internal/domain/ChannelService.java`

### 验证
```
mvn -pl java -am test
```

---

## 总体验证

### 每阶段验证
每个 Phase 完成后运行：
```
mvn -pl java -am clean test
```
确保所有既有测试 + 新增测试通过。

### 最终验证
```
mvn -pl java -am verify
```

### 回滚
每个 Phase 的变更应独立可回滚：
- Phase 1: 删除 TaskConfiguration.java 即可
- Phase 2-6: 各自修改的文件互不重叠（除跨层调用的接口外），git revert 单阶段提交即可

---

## 风险点

| 风险 | 影响 | 缓解 |
|------|------|------|
| Streaming 修改 RelayHandler 返回类型影响现有端点 | 可能导致非流式路由异常 | 保留原非流式路径不变，新增独立 StreamHandler |
| Anthropic 请求格式与 OpenAI 差异较大 | 转换逻辑复杂 | 仅支持 basic text-only chat，不做 thinking/tools |
| 缓存层初次 flush 前数据不可见 | 影响 stats 实时查询 | flush 间隔设 30s，对管理端可见性可接受 |
| Channel sync 需要 HTTP 访问外部 API | 测试需 mock | 使用 StubUpstream 模拟 /models 端点 |
