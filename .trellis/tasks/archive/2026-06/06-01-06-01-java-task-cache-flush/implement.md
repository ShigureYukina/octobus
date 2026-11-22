# Implement: Java 后台任务与缓存刷盘

## Step 1: 建立调度与缓冲骨架

### 1.1 添加任务配置

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/TaskConfiguration.java`

内容：

- `@Configuration`
- `@EnableScheduling`
- flush 周期首版固定为 30s

### 1.2 添加 stats 聚合器

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/StatsAccumulator.java`

要求：

- 支持 total / daily / hourly / apikey / channel 五类 bucket
- 在 `record(...)` 时根据 `recordedAt` 决定时间桶
- 提供 `drain()` 返回当前快照并清空内存状态

### 1.3 添加 log 缓冲

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/LogBuffer.java`

要求：

- `append(RelayLog)`
- `drainAll()`

### 验证

- 应用可编译
- 不影响现有无关测试编译

---

## Step 2: 改造 relay metrics 写路径

### 2.1 修改 `RelayMetricsRecorder`

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/relay/metrics/RelayMetricsRecorder.java`

调整：

- 移除直接写 DB 的 `StatsRepository` / `LogRepository` 依赖
- 注入 `StatsAccumulator` / `LogBuffer`
- 保留原有 `StatsMetrics`、`RelayLog`、cost、usage 的计算逻辑
- 在 `record(...)` 中捕获 `recordedAt`
- 将 stats / log 写入改为内存缓冲

### 2.2 保持查询路径不变

不改：

- `StatsService`
- `LogService`

目标：

- 管理端读取仍只从 DB 查询

### 验证

- 相关代码编译通过
- 现有读路径不需要同步改 controller/service

---

## Step 3: 添加 flush 任务与 repository 支持

### 3.1 添加 `StatsFlushTask`

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/StatsFlushTask.java`

要求：

- `@Scheduled(...)` 周期性触发
- 调度周期固定 30s
- 提供可被测试直接调用的 `flushOnce()`
- `flushOnce()` 内部：
  - `statsAccumulator.drain()`
  - 遍历快照并调用现有 `StatsRepository.updateTotal/updateDaily/updateHourly/updateAPIKey/updateChannel`

### 3.2 添加 `LogFlushTask`

新增：

- `java/src/main/java/io/github/shigureyukina/octobus/task/LogFlushTask.java`

要求：

- `@Scheduled(...)` 周期性触发
- 调度周期固定 30s
- 提供可被测试直接调用的 `flushOnce()`
- `flushOnce()` 内部：
  - `logBuffer.drainAll()`
  - 非空时调用 `LogRepository.saveAll(...)`

### 3.3 扩展 `LogRepository`

修改：

- `java/src/main/java/io/github/shigureyukina/octobus/persistence/LogRepository.java`

新增：

- `saveAll(List<RelayLog> logs)`

要求：

- 使用批量插入
- 与现有单条 `save()` 的列映射保持一致

### 3.4 StatsRepository 尽量最小改动

原则：

- 首版优先复用现有 `update*()` 方法
- 不为 stats flush 额外引入复杂 repository 抽象

### 3.5 添加正常关闭时 flush

要求：

- 在 Spring 正常关闭时触发一次 best-effort stats/log flush
- 复用 `flushOnce()` 能力，不新增第二套写入逻辑
- 仅覆盖优雅关闭场景，不扩展到异常终止恢复

### 验证

- flush 任务可正常创建 Bean
- repository 写接口编译通过

---

## Step 4: 补测试并修正现有 observability 用例

### 4.1 新增单元测试

建议新增：

- `java/src/test/java/io/github/shigureyukina/octobus/task/StatsAccumulatorTests.java`
- `java/src/test/java/io/github/shigureyukina/octobus/task/LogBufferTests.java`

### 4.2 调整 observability 集成测试

修改：

- `java/src/test/java/io/github/shigureyukina/octobus/relay/it/RelayObservabilityIT.java`

方式：

- 在发起 relay 后、断言 DB 前显式调用 `StatsFlushTask.flushOnce()` / `LogFlushTask.flushOnce()`
- 禁止依赖定时等待或睡眠

### 4.3 保留读接口测试

复用：

- `java/src/test/java/io/github/shigureyukina/octobus/persistence/ModelLogStatsIntegrationTests.java`
- `java/src/test/java/io/github/shigureyukina/octobus/OctobusApplicationTests.java`

目标：

- 确认调度配置不影响启动
- 确认 stats / log 查询接口仍正常

---

## Validation Commands

### 精准验证

```bash
mvn -pl java -am test -Dtest=OctobusApplicationTests,ModelLogStatsIntegrationTests,RelayObservabilityIT
```

### 全量 Java 测试

```bash
mvn -pl java -am test
```

## Review Gates

在 `task.py start` 之前确认：

1. `prd.md`、`design.md`、`implement.md` 已齐备
2. 设计已明确“按记录发生时归桶”
3. 设计已明确“测试显式 flush，不依赖 sleep”
4. 设计已明确“首版固定 30s 调度 + 正常关闭时 best-effort flush”
5. 范围仍限定在 stats/log 写入聚合与后台刷盘

## Rollback Notes

- 若 Step 2 改造后影响面超出预期，可先保留新建的 `task/` 结构，但回退 `RelayMetricsRecorder` 到直接写库路径
- 若批量 log flush 引入问题，可先保留 `LogBuffer`，临时让 `LogFlushTask` 内部逐条调用 `save()`，待行为稳定后再切回 `saveAll()`
