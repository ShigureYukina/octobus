# Design: Java Model CRUD 与模型列表

## Overview

补齐 Java 后端管理端 model CRUD，以及 `GET /v1/models` 的 OpenAI 兼容模型列表接口。当前 `ModelController` 仅有 `GET /api/v1/model/list`，`ModelRepository` 仅有 `findAll()`，`ModelService` 仅有 `listModels()`。

保持不变的部分：

- `model` 表结构不变（name PK, input, output, cache_read, cache_write）
- `ModelInfo` record 不变
- 现有 `GET /api/v1/model/list` 端点行为不变
- `PriceLookup` 缓存语义不变（构造时加载，`refresh()` 重读）

当前代码现状：

- `ModelController`：仅 `GET /api/v1/model/list`
- `ModelRepository`：仅 `findAll()` 返回 `List<ModelInfo>`
- `ModelService`：仅 `listModels()`
- Go 端对应功能：`/api/v1/model/create`、`/api/v1/model/update`、`/api/v1/model/delete`、`/api/v1/model/channel`、`/v1/models`

## Goals

1. 管理端具备 model 的完整增删改查能力
2. 对外提供 `GET /v1/models`，返回 OpenAI 兼容的模型列表格式
3. 新增或更新后的模型能够体现在模型列表结果中

## Non-Goals

- 价格预设自动生成（out of scope）
- Anthropic 专有模型列表响应格式（out of scope）
- 与本任务无关的 provider 价格同步逻辑

## Architecture

### 1. ModelRepository 扩展

在现有 `ModelRepository` 中新增方法：

| 方法 | SQL | 说明 |
|------|-----|------|
| `findByName(String name)` | `SELECT ... FROM model WHERE name = ?` | 按名称查找 |
| `existsByName(String name)` | `SELECT COUNT(*) FROM model WHERE name = ?` | 判断是否存在 |
| `create(String name, double input, double output, double cacheRead, double cacheWrite)` | `INSERT INTO model ...` | 创建 |
| `update(String name, Double input, Double output, Double cacheRead, Double cacheWrite)` | `UPDATE model SET ... WHERE name = ?` | 更新（仅更新非 null 字段） |
| `deleteByName(String name)` | `DELETE FROM model WHERE name = ?` | 删除 |
| `findAllNames()` | `SELECT name FROM model ORDER BY name` | 返回所有模型名称 |

关键约束：

- `name` 为主键，创建时需检查是否已存在
- 更新时仅更新传入的非 null 字段（与 `ChannelRepository.update()` 风格一致）
- 删除时需同步刷新 `PriceLookup` 缓存

### 2. ModelService 扩展

在现有 `ModelService` 中新增方法：

| 方法 | 说明 |
|------|------|
| `createModel(String name, double input, double output, double cacheRead, double cacheWrite)` | 创建模型，检查重名 |
| `updateModel(String name, Double input, Double output, Double cacheRead, Double cacheWrite)` | 更新模型，检查存在性 |
| `deleteModel(String name)` | 删除模型，检查存在性 |
| `listModelNames()` | 返回所有模型名称列表 |

关键约束：

- 创建时若 name 已存在，抛出 `ApiException(CONFLICT, "Model already exists")`
- 更新/删除时若 name 不存在，抛出 `ApiException(NOT_FOUND, "Model not found")`
- 创建/更新/删除后调用 `PriceLookup.refresh()` 刷新缓存

### 3. ModelController 扩展

在现有 `ModelController` 中新增端点：

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/v1/model/create` | 创建 model 记录 |
| POST | `/api/v1/model/update` | 更新 model 价格 |
| POST | `/api/v1/model/delete` | 删除 model 记录 |

请求体格式（与 Go 对齐）：

**Create**：
```json
{
  "name": "gpt-4o",
  "input": 1.0,
  "output": 2.0,
  "cache_read": 0.5,
  "cache_write": 1.0
}
```

**Update**：
```json
{
  "name": "gpt-4o",
  "input": 1.5,
  "output": 3.0
}
```

**Delete**：
```json
{
  "name": "gpt-4o"
}
```

### 4. /v1/models 公共端点

新增 `PublicModelController`（或直接在 `RelayRouter` 中注册）：

| 方法 | 路径 | 认证 | 功能 |
|------|------|------|------|
| GET | `/v1/models` | API Key Auth | OpenAI 兼容模型列表 |

响应格式（OpenAI 兼容）：

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

数据来源：

- 从 `model` 表获取所有有价格记录的模型名称
- 如果 API Key 有 `supported_models` 限制，则过滤结果

实现方式：

- 在 `RelayRouter` 中注册 `GET /v1/models` 路由
- 使用 `ServerRequest` 提取 API Key 信息
- 调用 `ModelService.listModelNames()` 获取模型列表
- 根据 API Key 的 `supported_models` 过滤

### 5. PriceLookup 刷新

`PriceLookup` 当前在构造时加载缓存，提供 `refresh()` 方法重读数据库。

本任务要求：

- `ModelService` 的 create/update/delete 操作后调用 `PriceLookup.refresh()`
- `refresh()` 使用 `ModelRepository.findAll()` 重新加载缓存

## Testing Strategy

### 集成测试

新增 `controller/ModelControllerIT.java`（或扩展现有测试）：

1. **CRUD 成功路径**：create → list 包含新模型 → update → list 反映更新 → delete → list 不包含
2. **基础校验**：创建重名模型返回 409、更新/删除不存在模型返回 404
3. **模型列表格式**：`GET /v1/models` 返回 OpenAI 兼容格式

### 保留现有测试

- `OctobusApplicationTests.java` — 启动验证
- `ModelLogStatsIntegrationTests.java` — 读取链路验证

## Risks and Mitigations

### 风险 1：PriceLookup 缓存与 DB 不一致

缓解：每次写操作后立即 `refresh()`，保证缓存与 DB 一致。首版不引入更复杂的缓存失效机制。

### 风险 2：/v1/models 路由与现有 relay 路由冲突

缓解：`/v1/models` 是 GET 请求，现有 relay 路由都是 POST，不会冲突。在 `RelayRouter` 中单独注册即可。

### 风险 3：model name 大小写处理

缓解：Go 端在 `LLMCreate` 中将 name 转为小写。Java 端保持与 Go 一致，在 `ModelService.createModel()` 中将 name 转为小写。
