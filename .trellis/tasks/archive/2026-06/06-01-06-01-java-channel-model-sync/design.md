# Design: Java Channel 模型拉取与同步

## Overview

补齐 Java 后端的 channel fetch-models / sync 能力，以及模型差异更新后的必要整理动作。当前 Java 端缺少从上游拉取模型列表、与本地渠道模型对比差异、更新渠道模型配置、清理失配 group_items、自动分组等能力。

保持不变的部分：

- `Channel` record 和 `ChannelRepository` 的现有 CRUD 不变
- `GroupRepository` 的现有 CRUD 不变
- `ModelRepository` 的现有读取不变
- `ChannelController` 的现有端点不变

当前代码现状：

- `Channel.autoSync()` 字段已存在但无使用方
- `Channel.autoGroup()` 字段已存在但无使用方
- `Channel.matchRegex()` 字段已存在但无使用方
- `task/` 包仅有 `package-info.java`
- Go 端对应功能：`helper.FetchModels()`、`task.SyncModelsTask()`、`helper.ChannelAutoGroup()`

## Goals

1. 可针对渠道触发模型拉取，并获得可用于同步的上游模型列表
2. 同步执行后，本地渠道模型配置可反映上游模型差异
3. 同步后的必要整理动作会执行完成，避免留下明显失配的关联数据
4. 新增能力需要自动化验证

## Non-Goals

- 与本任务无关的高级调度策略
- 与 relay 适配器等价的全 provider 特性补齐
- 价格预设自动生成
- `model` 表主数据维护（新增默认价格、删除孤立模型价格等）
- `PriceLookup` 生命周期与缓存刷新策略调整

## Architecture

### 1. ProviderModelFetcher

新增 `helper/ProviderModelFetcher.java`，负责根据渠道 type 调用不同提供商的 `/models` 端点。

#### 分派逻辑

| channel.type() | 获取方式 | 认证头 | 端点 |
|----------------|---------|--------|------|
| `openai/chat_completions` | GET | `Authorization: Bearer {key}` | `{baseUrl}/v1/models` |
| `anthropic/messages` | GET | `x-api-key: {key}`, `anthropic-version: 2023-06-01` | `{baseUrl}/v1/models` |
| 其他 | - | - | 首版不承诺支持；如后续扩展需单独评审 |

#### URL 构建

复用 `OpenAICompatibleProviderClient.buildChatCompletionsUri()` 的 baseUrl 选择逻辑（按 delay 排序取最低延迟的 baseUrl），但路径不同：

- OpenAI 类：`{baseUrl}/v1/models`
- Anthropic：`{baseUrl}/v1/models`

#### 响应解析

- OpenAI 格式：`{ "data": [{ "id": "model-name" }] }` → 提取 `id` 列表
- Anthropic 格式：`{ "data": [{ "id": "model-name" }], "has_more": true, "last_id": "..." }` → 分页获取全部 `id`
- matchRegex 过滤：如果渠道配置了 `matchRegex`，仅保留匹配的模型名

#### 实现方式

- 使用 `WebClient` 发送 GET 请求
- 返回 `List<String>` 模型名称列表
- 异常时抛出 `ApiException`

### 2. ChannelSyncService

新增 `domain/ChannelSyncService.java`，封装同步流程。

#### 同步流程（对齐 Go 端 `SyncModelsTask`）

```
syncAllChannels()
  → 遍历所有 channel
  → 跳过 autoSync == false 的渠道
  → ProviderModelFetcher.fetchModels(channel)
  → 与当前 channel.model 对比差异
  → 有变化时:
    → update channel.model
    → 删除已消失模型对应的 group_items
    → 自动分组 (ChannelAutoGroup)
```

#### 单渠道同步

```
syncChannel(int channelId)
  → 获取渠道
  → ProviderModelFetcher.fetchModels(channel)
  → 与当前 channel.model 对比差异
  → 有变化时更新
```

### 3. ChannelAutoGroup

新增 `helper/ChannelAutoGroup.java`，实现自动分组逻辑。

#### AutoGroup 类型

| autoGroup 值 | 匹配规则 |
|--------------|---------|
| 0 (NONE) | 不分组 |
| 1 (FUZZY) | 模型名 contains 组名（忽略大小写） |
| 2 (EXACT) | 模型名 == 组名（忽略大小写） |
| 3 (REGEX) | 模型名 matches 组的 matchRegex |

#### 分组流程

```
autoGroup(Channel channel)
  → 如果 autoGroup == 0，直接返回
  → 获取所有 groups
  → 获取渠道模型名称列表（channel.model + channel.customModel）
  → 对每个 group，根据 autoGroup 类型匹配模型名
  → 匹配到的模型名创建 GroupItem 并批量插入
```

### 4. 模型差异工具

新增 `helper/ModelDiff.java`，实现模型列表差异计算。

```
diff(List<String> oldModels, List<String> newModels)
  → 返回 (deletedModels, addedModels)
```

与 Go 端 `utils/diff.Diff()` 对齐。

### 4.1 数据所有权边界

本 child task 只负责：

- 上游模型列表拉取
- `channel.model` 同步
- 与 channel 直接相关的整理动作（失配 `group_items` 清理、auto-group）

本 child task **不负责**：

- 向 `model` 表写入新增模型
- 删除 `model` 表中的孤立模型
- 维护价格主数据或调用 `PriceLookup.refresh()`

如果后续需要把同步结果投影到 `model` 表，应通过 `java-model-crud-models` 提供的统一写入口，或单独拆分 child task，不在当前 MVP 默认范围内执行。

### 5. ChannelController 扩展

在现有 `ChannelController` 中新增端点：

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/v1/channel/fetch-models` | 拉取指定渠道的上游模型列表 |
| POST | `/api/v1/channel/sync` | 触发全量同步 |
| GET | `/api/v1/channel/last-sync-time` | 获取最后同步时间 |

### 6. SyncModelsTask（后台定时任务）

新增 `task/SyncModelsTask.java`：

- 使用 `@Scheduled` 注解，首版固定 1 小时间隔
- 调用 `ChannelSyncService.syncAllChannels()`
- 记录最后同步时间

**依赖约束**：`SyncModelsTask` 必须复用 `java-task-cache-flush` 已建立的调度基础设施，不重复创建第二个 `TaskConfiguration`。

### 7. GroupRepository 扩展

新增方法：

| 方法 | SQL | 说明 |
|------|-----|------|
| `deleteItemsByChannelAndModels(int channelId, List<String> modelNames)` | `DELETE FROM group_items WHERE channel_id = ? AND model_name IN (...)` | 批量删除指定渠道的指定模型 group_items |
| `batchAddItems(int groupId, List<GroupIDAndLLMName> items)` | `INSERT INTO group_items ...` | 批量添加 group_items |

### 8. 与其他 child task 的依赖

- 本任务依赖 `java-task-cache-flush` 提供调度基础设施（`TaskConfiguration` / `@EnableScheduling`）
- 本任务默认**不修改** `ModelRepository`、`PriceLookup` 或 `model` 表主数据
- 若未来需要把 sync 结果写入 `model` 表，应以 `java-model-crud-models` 为主数据 owner，当前 child 只消费其公开能力，不自行实现重复写路径

## Testing Strategy

### 单元测试

新增：

- `helper/ModelDiffTests.java` — 差异计算正确性
- `helper/ChannelAutoGroupTests.java` — 各 autoGroup 类型的匹配逻辑

### 集成测试

新增：

- `helper/ProviderModelFetcherIT.java` — mock 上游 /models 端点，验证模型拉取
- `domain/ChannelSyncServiceIT.java` — 验证同步后渠道模型更新、group_items 清理、LLM 价格表更新

### 保留现有测试

- `OctobusApplicationTests.java`
- `ModelLogStatsIntegrationTests.java`

## Risks and Mitigations

### 风险 1：上游 /models 端点不可达或超时

缓解：设置合理的连接超时和读取超时（复用 `RelayProperties` 中的配置）；单个渠道拉取失败不影响其他渠道。

### 风险 2：同步过程中 group_items 清理导致正在使用的路由失效

缓解：同步流程在事务中执行，先更新 channel.model 再清理 group_items；如果事务失败则整体回滚。

### 风险 3：与 model-crud-models child task 的主数据所有权冲突

缓解：当前 child 不拥有 `model` 表主数据写权限，默认不修改 `ModelRepository` 与 `PriceLookup`；如后续确需联动，必须通过 parent 重新确认边界。

### 风险 4：Anthropic 分页获取模型

缓解：Anthropic /models 端点支持 `has_more` + `last_id` 分页。首版实现分页循环；若上游返回的并非 Anthropic `/models` 规范响应，则直接报错，不隐式 fallback 到其他 provider 语义。
