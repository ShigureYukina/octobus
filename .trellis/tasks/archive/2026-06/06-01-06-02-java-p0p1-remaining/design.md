# Design: 补齐剩余 P0/P1 功能缺口

## Architecture Overview

```
┌──────────────────────────────────────────────────┐
│               RelayRouter (扩展)                    │
│  POST /v1/responses       (新增 Inbound)          │
│  POST /v1/embeddings      (新增 Inbound)          │
│  POST /v1/images/*        (新增 Inbound)          │
│  POST /v1/messages+stream (扩充 Stream)           │
└──────────────┬────────────────────────────────────┘
               │
     ┌─────────▼──────────────────────┐
     │ InboundTransformer (新增)       │
     │  ResponsesInboundTransformer     │
     │  EmbeddingsInboundTransformer    │
     │  ImagesInboundTransformer        │
     └─────────┬──────────────────────┘
               │
     ┌─────────▼──────────────────────┐
     │ UnifiedRequest → Outbound       │
     │ TransformerRegistry 扩展注册     │
     └─────────────────────────────────┘
```

```
┌──────────────────────────────────────────────────┐
│           管理端点补齐 (Controller 层)              │
│  LogController  → clear, stream, stream-token    │
│  StatsController → apikey                        │
│  APIKeyController → stats, login                 │
│  ModelController → channel, update-price,        │
│                    last-update-time               │
│  SettingController → export, import              │
└──────────────────────────────────────────────────┘
```

---

## 1. Anthropic Messages 流式

### 方案
- 实现 `AnthropicProviderClient.executeStream()`：调用上游 Anthropic `POST /v1/messages` 并传入 `stream: true`
- 响应使用 `SSE` 协议（Anthropic 流式事件协议有 `message_start`, `content_block_delta`, `message_delta`, `message_stop` 等事件）
- 在 Java 侧将 Anthropic 流式事件**透传到客户端**（暂不转为 OpenAI 格式）
- `RelayRun.executeStream()` 中允许 ANTHROPIC_MESSAGES inbound 走流式路径

### 边界
- 不做 Anthropic 事件到 OpenAI 格式的转换（MVP）
- 不做 thinking、tools、cache_control 支持

---

## 2. 新中继路由（Responses / Embeddings / Images）

### 方案
- 新增 3 个 InboundTransformer 实现 InboundTransformer 接口
  - `ResponsesInboundTransformer` — 解析 OpenAI Responses 请求
  - `OpenAIEmbeddingsInboundTransformer` — 解析 Embeddings 请求
  - `OpenAIImageInboundTransformer` — 解析 Images 请求（兼容 generation/edit/variation）
- 上述 5 条路由共享已有的 `TransformerRegistry.defaultRegistry()` 注册
- 出站侧复用现有的 `RelayProviderFormat` 枚举
- 首次 MVP：仅支持 OpenAI 出站格式（非流式）
- RelayRouter 中移除 `UnsupportedInboundTransformer` 的 guard，注册真实 transformer

### 数据流
```
请求 → RelayHandler → RelayRun.execute()
  → InboundTransformer.transform() → UnifiedRequest
  → 选路 → RelayAttempt.forward()
    → OutboundTransformer.serializeRequest(UnifiedRequest)
    → ProviderHttpClient.postChatCompletion()
    → OutboundTransformer.parseResponse()
  → 返回 ResponseEntity
```

---

## 3. 管理端点补齐

### Log 端点
| 端点 | 方法 | 说明 |
|------|------|------|
| `DELETE /api/v1/log/clear` | `LogRepository.deleteAll()` | 全量删除 relay_log |
| `GET /api/v1/log/stream` | SSE 推送 | 复用 `LogService.list()` 轮询或 `Sinks.Many` 模式 |
| `GET /api/v1/log/stream-token` | token 生成 | 类似 Go 的 stream token 管理 |

SSE 方案：使用 Reactor `Sinks.Many<RelayLog>`，LogService 写入时同时推送到 Sink，LogController 注册 SSE 端点返回 Flux。

### Stats / APIKey / Model / Setting 端点
均为标准 Controller + Service + Repository 三层模式，参照现有实现风格。

- Stats apikey: 从 StatsRepository 查询 stats_apikey 表
- APIKey stats/login: APIKeyService 中新增方法
- Model channel/update-price/last-update-time: ModelService + ProviderModelFetcher（复用现有 fetch 能力）
- Setting export/import: 全表导出为 JSON（DB 快照），import 为增量 upsert

---

## 4. 后台任务补齐

### BaseURL 延迟检测
- 新增 `ChannelDelayTask`：`@Scheduled(fixedRate=3600000)` 每小时执行
- 遍历所有 channel，对每个 BaseURL 发送 HEAD/GET 请求测速
- 将延迟结果更新到 channel 的 `baseUrls` JSON 字段中

### 价格自动更新
- 复用现有 `ProviderModelFetcher` 或新增 `PriceUpdateTask`
- `@Scheduled(fixedRate=86400000)` 每天执行
- 从远程源（`https://models.dev/api.json`）拉取最新价格
- 按模型名 upsert 到 `model` 表

---

## 5. 管线修复

### param_override
- 在 `RelayAttempt.java` 的 `forward()` 方法中，在调用 `OutboundTransformer.serializeRequest()` 后
- 将 channel 的 `paramOverride` JSON 合并到序列化后的 JsonNode 中
- 使用 Jackson `ObjectMapper.updateValue()` 或 `JsonNode` 的深度合并

### CORS 过滤器
- 新增 `CorsWebFilter` Bean（WebFlux 原生）
- 从 `SettingService` 读取 `cors_allow_origins` 值
- 支持通配符 `*` 和逗号分隔的 origin 列表

---

## 6. 涉及的文件清单

### 新增文件
| 文件 | 所属模块 |
|------|---------|
| `relay/spi/ResponsesInboundTransformer.java` | 中继 |
| `relay/spi/OpenAIEmbeddingsInboundTransformer.java` | 中继 |
| `relay/spi/OpenAIImageInboundTransformer.java` | 中继 |
| `relay/provider/ResponsesProviderClient.java` | 中继 |
| `relay/provider/EmbeddingsProviderClient.java` | 中继 |
| `relay/provider/ImageProviderClient.java` | 中继 |
| `task/ChannelDelayTask.java` | 后台任务 |
| `task/PriceUpdateTask.java` | 后台任务 |
| `config/CorsConfiguration.java` | 基础设施 |

### 修改文件
| 文件 | 改动 |
|------|------|
| `relay/RelayRouter.java` | 注册新路由、移除 guard |
| `relay/RelayRun.java` | executeStream() 支持 Anthropic |
| `relay/RelayAttempt.java` | forward() 中应用 param_override |
| `relay/provider/anthropic/AnthropicProviderClient.java` | 实现 executeStream() |
| `relay/spi/TransformerRegistry.java` | 注册新 transformer |
| `relay/spi/RelayProviderFormat.java` | 确认枚举完备 |
| `controller/LogController.java` | 新增 clear, stream, stream-token |
| `controller/StatsController.java` | 新增 apikey |
| `controller/APIKeyController.java` | 新增 stats, login |
| `controller/ModelController.java` | 新增 channel, update-price, last-update |
| `controller/SettingController.java` | 新增 export, import |
| `domain/LogService.java` | 新增 clear, stream(token) |
| `domain/StatsService.java` | 新增 apikey |
| `domain/APIKeyService.java` | 新增 stats, login |
| `domain/ModelService.java` | 新增 channel, update-price |
| `domain/SettingService.java` | 新增 export, import |
| `persistence/LogRepository.java` | 新增 deleteAll |
| `persistence/StatsRepository.java` | 新增 findByApiKey |
| `persistence/ModelRepository.java` | 新增 findByChannel |
| `persistence/SettingRepository.java` | 新增 exportAll, importAll |
