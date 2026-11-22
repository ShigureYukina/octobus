# Implement: Java Model CRUD 与模型列表

## Step 1: 扩展 ModelRepository

### 1.1 新增 CRUD 方法

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/persistence/ModelRepository.java`

新增：

- `findByName(String name)` → `Optional<ModelInfo>`
- `existsByName(String name)` → `boolean`
- `create(String name, double input, double output, double cacheRead, double cacheWrite)` → `void`
- `update(String name, Double input, Double output, Double cacheRead, Double cacheWrite)` → `void`
- `deleteByName(String name)` → `void`
- `findAllNames()` → `List<String>`

要求：

- `create` 使用 `INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)`
- `update` 仅更新非 null 字段（与 `ChannelRepository.update()` 风格一致）
- `findAllNames` 返回 `SELECT name FROM model ORDER BY name`

### 验证

- 编译通过
- 现有 `findAll()` 不受影响

---

## Step 2: 扩展 ModelService

### 2.1 新增业务方法

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/domain/ModelService.java`

新增：

- `createModel(String name, double input, double output, double cacheRead, double cacheWrite)`
  - name 转小写
  - 检查重名 → `ApiException(CONFLICT)`
  - 调用 `modelRepository.create()`
  - 调用 `priceLookup.refresh()`
- `updateModel(String name, Double input, Double output, Double cacheRead, Double cacheWrite)`
  - 检查存在性 → `ApiException(NOT_FOUND)`
  - 调用 `modelRepository.update()`
  - 调用 `priceLookup.refresh()`
- `deleteModel(String name)`
  - 检查存在性 → `ApiException(NOT_FOUND)`
  - 调用 `modelRepository.deleteByName()`
  - 调用 `priceLookup.refresh()`
- `listModelNames()` → `List<String>`
  - 调用 `modelRepository.findAllNames()`

### 2.2 注入 PriceLookup

修改：

- `ModelService` 构造函数新增 `PriceLookup` 依赖

### 验证

- 编译通过
- 现有 `listModels()` 不受影响

---

## Step 3: 扩展 ModelController

### 3.1 新增管理端 CRUD 端点

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/controller/ModelController.java`

新增：

- `POST /api/v1/model/create` — 接收 `ModelCreateRequest` body
- `POST /api/v1/model/update` — 接收 `ModelUpdateRequest` body
- `POST /api/v1/model/delete` — 接收 `ModelDeleteRequest` body

### 3.2 新增请求 record

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/domain/ModelCreateRequest.java`
- `java/src/main/java/io/github/shigureyukina/octobus/domain/ModelUpdateRequest.java`
- `java/src/main/java/io/github/shigureyukina/octobus/domain/ModelDeleteRequest.java`

格式：

- `ModelCreateRequest(String name, double input, double output, double cacheRead, double cacheWrite)`
- `ModelUpdateRequest(String name, Double input, Double output, Double cacheRead, Double cacheWrite)`
- `ModelDeleteRequest(String name)`

### 验证

- 编译通过
- 现有 `GET /api/v1/model/list` 不受影响

---

## Step 4: 实现 /v1/models 公共端点

### 4.1 在 RelayRouter 中注册路由

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/RelayRouter.java`

新增：

- `GET /v1/models` 路由，使用 API Key Auth
- handler 调用 `ModelService.listModelNames()`
- 根据 API Key 的 `supported_models` 过滤结果
- 返回 OpenAI 兼容格式：`{ "object": "list", "data": [{ "id": "...", "object": "model", "created": ..., "owned_by": "octopus" }] }`

### 4.2 新增响应 record

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/domain/OpenAIModel.java`
- `java/src/main/java/io/github/shigureyukina/octobus/domain/OpenAIModelList.java`

格式与 Go 端 `model.OpenAIModel` / `model.OpenAIModelList` 对齐。

### 验证

- 编译通过
- 现有 POST relay 路由不受影响

---

## Step 5: 补测试

### 5.1 新增集成测试

新增：

- `java/src/test/java/io/github/shigureyukina/octobus/controller/ModelControllerIT.java`

覆盖：

- CRUD 成功路径：create → list 包含 → update → list 反映 → delete → list 不包含
- 基础校验：重名创建 409、不存在更新/删除 404
- 模型列表格式：`GET /v1/models` 返回 OpenAI 兼容格式

### 5.2 保留现有测试

复用：

- `OctobusApplicationTests.java`
- `ModelLogStatsIntegrationTests.java`

---

## Validation Commands

### 精准验证

```bash
mvn -pl java -am test -Dtest=ModelControllerIT,OctobusApplicationTests
```

### 全量 Java 测试

```bash
mvn -pl java -am test
```

## Review Gates

在 `task.py start` 之前确认：

1. `prd.md`、`design.md`、`implement.md` 已齐备
2. 设计已明确"写操作后 PriceLookup.refresh()"
3. 设计已明确"model name 转小写"
4. 设计已明确"/v1/models 返回 OpenAI 兼容格式"
5. 范围仍限定在 model CRUD 与公共模型列表

## Rollback Notes

- 若 CRUD 端点有问题，可先保留 `ModelRepository` 新增方法但不暴露 controller 端点
- 若 `/v1/models` 路由与 relay 路由冲突，可改为独立 `PublicModelController`
