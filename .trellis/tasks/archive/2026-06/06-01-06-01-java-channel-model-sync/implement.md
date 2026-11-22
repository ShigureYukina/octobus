# Implement: Java Channel 模型拉取与同步

## Step 1: 模型差异工具与 ProviderModelFetcher

### 1.1 新增 ModelDiff

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/helper/ModelDiff.java`

要求：

- `static DiffResult diff(List<String> oldModels, List<String> newModels)`
- 返回 `(List<String> deleted, List<String> added)`
- 比较时忽略大小写

### 1.2 新增 ProviderModelFetcher

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/helper/ProviderModelFetcher.java`

要求：

- `@Component`
- 注入 `WebClient.Builder`
- `List<String> fetchModels(Channel channel)` 方法
- 根据 `channel.type()` 分派：
  - `anthropic/messages` → Anthropic 格式（x-api-key, anthropic-version, 分页）
  - 其他 → OpenAI 格式（Bearer Auth）
- URL 构建：选择最低延迟的 baseUrl + `/v1/models`
- matchRegex 过滤：如果渠道配置了 `matchRegex`，仅保留匹配的模型名
- 异常时抛出 `ApiException`

### 验证

- 编译通过
- `ModelDiff` 可独立测试

---

## Step 2: ChannelAutoGroup 与 GroupRepository 扩展

### 2.1 新增 ChannelAutoGroup

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/helper/ChannelAutoGroup.java`

要求：

- `@Component`
- 注入 `GroupRepository`
- `void autoGroup(Channel channel)` 方法
- 根据 `channel.autoGroup()` 值分派：
  - 0 (NONE) → 直接返回
  - 1 (FUZZY) → 模型名 contains 组名（忽略大小写）
  - 2 (EXACT) → 模型名 == 组名（忽略大小写）
  - 3 (REGEX) → 模型名 matches 组的 matchRegex
- 匹配到的模型名创建 `GroupIDAndLLMName` 并批量插入

### 2.2 扩展 GroupRepository

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/persistence/GroupRepository.java`

新增：

- `deleteItemsByChannelAndModels(int channelId, List<String> modelNames)` — 批量删除指定渠道的指定模型 group_items
- `batchAddItems(int groupId, List<GroupIDAndLLMName> items)` — 批量添加 group_items（复用现有 `batchInsertItems` 逻辑）

### 2.3 新增 GroupIDAndLLMName record

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/domain/GroupIDAndLLMName.java`

格式：`record GroupIDAndLLMName(int channelId, String modelName)`

### 验证

- 编译通过
- 现有 Group CRUD 不受影响

---

## Step 3: ChannelSyncService

### 3.1 新增 ChannelSyncService

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/domain/ChannelSyncService.java`

要求：

- `@Service`
- 注入 `ChannelRepository`、`GroupRepository`、`ProviderModelFetcher`、`ChannelAutoGroup`
- `void syncAllChannels()` — 遍历所有 autoSync 渠道执行同步
- `void syncChannel(int channelId)` — 单渠道同步
- 同步流程：
  1. `fetchModels(channel)` 获取上游模型列表
  2. `ModelDiff.diff(oldModels, newModels)` 计算差异
  3. 有变化时更新 `channel.model`
  4. 删除已消失模型对应的 `group_items`
  5. `autoGroup.autoGroup(channel)` 自动分组

### 验证

- 编译通过
- 现有 Channel/Group/Model 读取不受影响

---

## Step 4: ChannelController 扩展与 SyncModelsTask

### 4.1 扩展 ChannelController

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/controller/ChannelController.java`

新增：

- `POST /api/v1/channel/fetch-models` — 接收渠道信息，返回上游模型列表
- `POST /api/v1/channel/sync` — 触发全量同步
- `GET /api/v1/channel/last-sync-time` — 获取最后同步时间

### 4.2 新增 SyncModelsTask

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/SyncModelsTask.java`

要求：

- `@Component`
- `@Scheduled(fixedRate = 3_600_000)` — 1 小时间隔
- 调用 `ChannelSyncService.syncAllChannels()`
- 记录最后同步时间（`volatile Instant lastSyncTime`）
- 提供 `getLastSyncTime()` 方法

### 4.3 确保 TaskConfiguration 存在

要求：

- 复用 `java-task-cache-flush` child task 已提供的 `TaskConfiguration`
- 本 child task 不重复创建第二个 `@EnableScheduling` 配置入口

### 验证

- 编译通过
- 现有 Channel CRUD 不受影响

---

## Step 5: 补测试

### 5.1 新增单元测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/helper/ModelDiffTests.java`
- `java/src/test/java/io/github/shigureyukina/octobus/helper/ChannelAutoGroupTests.java`

覆盖：

- ModelDiff：新增/删除/无变化/大小写
- ChannelAutoGroup：NONE/FUZZY/EXACT/REGEX 四种模式

### 5.2 新增集成测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/helper/ProviderModelFetcherIT.java`
- `java/src/test/java/io/github/shigureyukina/octobus/domain/ChannelSyncServiceIT.java`

覆盖：

- ProviderModelFetcher：mock 上游 /models 端点，验证模型拉取
- ChannelSyncService：验证同步后渠道模型更新、group_items 清理

### 5.3 保留现有测试

复用：

- `OctobusApplicationTests.java`
- `ModelLogStatsIntegrationTests.java`

---

## Validation Commands

### 精准验证

```bash
mvn -pl java -am test -Dtest=ModelDiffTests,ChannelAutoGroupTests,ProviderModelFetcherIT,ChannelSyncServiceIT
```

### 全量 Java 测试

```bash
mvn -pl java -am test
```

## Review Gates

在 `task.py start` 之前确认：

1. `prd.md`、`design.md`、`implement.md` 已齐备
2. 设计已明确"同步流程对齐 Go 端 SyncModelsTask"
3. 设计已明确"AutoGroup 四种类型"
4. 设计已明确"与 model-crud-models child task 共享 ModelRepository 扩展"
5. 设计已明确“本任务不拥有 model 表主数据生命周期”
6. 范围仍限定在 fetch-models / sync 与模型差异整理

## Rollback Notes

- 若 ProviderModelFetcher 实现有问题，可先保留空实现，controller 端点返回 501
- 若 SyncModelsTask 影响启动，可先移除 `@Scheduled` 注解，仅保留手动触发端点，但仍复用同一 TaskConfiguration
- 若 GroupRepository 扩展影响现有 Group CRUD，可先不暴露 `deleteItemsByChannelAndModels`，同步流程跳过 group_items 清理
