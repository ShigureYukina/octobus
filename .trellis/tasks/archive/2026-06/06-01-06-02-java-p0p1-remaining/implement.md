# Implement: 补齐剩余 P0/P1 功能缺口

## 执行顺序

```
Phase 1: P0 中继路由补齐       ← 最高优先级
Phase 2: P1 管理端点补齐       ← 可并行
Phase 3: P1 后台任务补齐
Phase 4: 管线修复 (param_override + CORS)
```

---

## Phase 1: P0 中继路由补齐

### 1.1 Anthropic Messages 流式

**涉及文件：**
- `relay/provider/anthropic/AnthropicProviderClient.java`
- `relay/RelayRun.java`

**步骤：**
1. `AnthropicProviderClient` 新增 `executeStream()`:
   - 使用 `webClient.post()` + `accept(TEXT_EVENT_STREAM)`
   - 解析 Anthropic 流式 SSE 事件（`message_start`, `content_block_delta`, `message_delta`, `message_stop`）
   - 每个事件作为独立的 `ServerSentEvent<String>` 发出
2. `RelayRun.executeStream()` 中放开 ANTHROPIC_MESSAGES 限制:
   - 修改流式 handler 路由，`switch` 中允许 `ANTHROPIC_MESSAGE`
   - 复用现有的 `AnthropicProviderClient`

**验证：**
- `mvn -pl java -am test -Dtest=RelayAnthropicMessagesIT,RelayStreamingIT`

### 1.2 OpenAI Responses InboundTransformer

**涉及文件：**
- 新增 `relay/spi/ResponsesInboundTransformer.java`
- 修改 `relay/spi/TransformerRegistry.java`
- 修改 `relay/RelayRouter.java`（移除 guard）

**步骤：**
1. 实现 `ResponsesInboundTransformer`:
   - 解析 `input`、`model`、`instructions` 字段
   - 构建 `UnifiedRequest(requestType=CHAT, ...)`
2. `TransformerRegistry.defaultRegistry()` 注册 `OPENAI_RESPONSES → ResponsesInboundTransformer`
3. `RelayRouter.java` 中 `OPENAI_RESPONSES` 路由改用真实 handler

**验证：** 新增集成测试或手动验证 501 被替换为正常转发

### 1.3 OpenAI Embeddings InboundTransformer

**涉及文件：**
- 新增 `relay/spi/OpenAIEmbeddingsInboundTransformer.java`
- 修改 `relay/spi/TransformerRegistry.java`
- 修改 `relay/RelayRouter.java`

**步骤：**
1. 实现 `OpenAIEmbeddingsInboundTransformer`:
   - 解析 `input`（string 或 array）、`model`
   - 构建 `UnifiedRequest(requestType=EMBEDDING, ...)`
2. 注册到 TransformerRegistry
3. RelayRouter 移除 guard

### 1.4 OpenAI Image InboundTransformer

**涉及文件：**
- 新增 `relay/spi/OpenAIImageInboundTransformer.java`
- 修改 `relay/spi/TransformerRegistry.java`
- 修改 `relay/RelayRouter.java`

**步骤：**
1. 实现 `OpenAIImageInboundTransformer`:
   - 解析 `prompt`、`model`、`n`、`size`、`quality`、`style`
   - 构建 `UnifiedRequest(requestType=IMAGE, ...)`
   - 兼容 generation/edit/variation（仅 `prompt` 字段不同）
2. 注册到 TransformerRegistry
3. RelayRouter 对 3 条 images 路由移除 guard

**注意：** Image 中继 MVP 仅做请求透传，不处理图片二进制数据编码转换

### 验证 (Phase 1)
```
mvn -pl java -am clean test
```

---

## Phase 2: P1 管理端点补齐

以下端点相互独立，可并行实现。每个端点为 Controller → Service → Repository 三层。

### 2.1 Log 端点
- `LogController`: 新增 `clear()` `stream()` `stream-token()`
- `LogService`: 新增 `clear()` `subscribe()`/`unsubscribe()` `streamTokenCreate()`/`verify()`
- `LogRepository`: 新增 `deleteAll()`
- SSE 方案: `LogService` 中 `Sinks.Many<RelayLog>`，relay 写入时同时推送，`stream` 端点返回 `Flux<ServerSentEvent>`
- `stream-token`: 简单的 UUID 生成 + 内存 Set 管理

### 2.2 Internal flush 端点
- 新增 `InternalController`（无前缀 `/api/v1`，直接 `/api/internal/flush`）
- 注入 `StatsFlushTask.flushOnce()` 和 `LogFlushTask.flushOnce()`

### 2.3 Stats apikey 端点
- `StatsController`: 新增 `apikey()` 端点
- `StatsService`: 新增 `apikeyStats()`
- `StatsRepository`: 确认存在 `findByApiKey()` 方法

### 2.4 APIKey stats/login 端点
- `APIKeyController`: 新增 `stats()` `login()`
- `APIKeyService`: 新增 `stats(apiKeyId)` `login(apiKey)` 方法

### 2.5 Model 扩展端点
- `ModelController`: 新增 `channel()` `updatePrice()` `lastUpdateTime()`
- `ModelService`: 新增 `listByChannel(channelId)` `updatePriceFromRemote()` `getLastUpdateTime()`
- `ProviderModelFetcher`: 确认可复用或扩展 fetch 能力

### 2.6 Setting export/import
- `SettingController`: 新增 `export_()` `import_()`
- `SettingService`: 新增 `exportAll()` → 返回所有表数据的 JSON, `importAll(dump)` → 增量 upsert
- `SettingRepository`: 新增 `exportAll()` `importAll()`

### 验证 (Phase 2)
```
mvn -pl java -am test -Dtest=*IntegrationTests,OctobusApplicationTests
```

---

## Phase 3: P1 后台任务补齐

### 3.1 BaseURL 延迟检测
- 新增 `task/ChannelDelayTask.java`：
  - `@Scheduled(fixedRate=3600000)` 每小时
  - 遍历所有 `channel.baseUrls`，对每个 URL 发送 HTTP HEAD 请求
  - 测量响应时间（连接 + 读取超时 5s）
  - 更新 `ChannelRepository` 中的 `baseUrls` JSON 字段
  - 失败时 delay 置为 -1 或保持原值

### 3.2 价格自动更新
- 新增 `task/PriceUpdateTask.java`：
  - `@Scheduled(fixedRate=86400000)` 每天
  - 从 `https://models.dev/api.json` 拉取
  - 按模型名 upsert 到 `model` 表
  - 记录最后更新时间到 setting 表（`model_last_update_time`）

### 验证 (Phase 3)
```
mvn -pl java -am test
```

---

## Phase 4: 管线修复

### 4.1 param_override 合并
- 修改 `RelayAttempt.java` 的 `forward()` 方法
- 在 `outboundTransformer.serializeRequest(request)` 之后
- 从 `channel.paramOverride()` 获取 JSON string
- 使用 Jackson 深度合并到请求 JsonNode
- 处理边界：paramOverride 为 null 或空时不操作

### 4.2 CORS 过滤器
- 新增 `config/CorsConfiguration.java`：
  - `CorsWebFilter` Bean
  - 从 `SettingService` 读取 `cors_allow_origins`
  - 支持 `*` 通配、逗号分隔的 origin 列表
  - 允许 methods: GET, POST, PUT, DELETE, OPTIONS
  - 允许 headers: Content-Type, Authorization, X-API-Key

### 验证 (Phase 4)
```
mvn -pl java -am test
```

---

## 总体验证

### 全量测试
```bash
mvn -pl java -am clean test
```

---

## 回滚

每个 Phase 的变更独立可回滚：
- Phase 1 新增 transformer：移除文件 + 恢复 TransformerRegistry 和 RelayRouter
- Phase 2 管理端点：git revert 单个 commit
- Phase 3 后台任务：删除 Task 文件
- Phase 4 管线修复：恢复 RelayAttempt 和移除 CorsConfiguration

## 风险点

| 风险 | 影响 | 缓解 |
|------|------|------|
| Anthropic 流式协议复杂 | 流式实现偏差 | 初始版本简单透传事件，不做格式转换 |
| Image 请求含二进制数据 | Multipart 请求处理复杂 | MVP 仅做 JSON-only generation，不做 edit/variation 的 multipart |
| SSE 日志推送内存压力 | 大量日志时 OOM | 用容量有限的 Sinks、背压控制 |
| setting export/import 数据一致 | 导入时损坏数据 | Import 用事务包装，失败回滚 |
