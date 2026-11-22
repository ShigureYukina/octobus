# Design: Java 后台任务与缓存刷盘

## Overview

该子任务负责把当前“relay 完成后立即写 DB”的 observability 写路径，改造成“先写内存、再由后台任务周期性刷盘”。

保持不变的部分：

- 对外查询接口与返回结构不变：`StatsService` 仍经 `StatsRepository` 读取 DB，`LogService` 仍经 `LogRepository` 读取 DB。
- `RelayMetricsRecorder` 仍负责计算 `StatsMetrics`、成本和 `RelayLog` 内容，只改变写入落点。

当前代码现状：

- `relay/metrics/RelayMetricsRecorder.java` 直接调用 `StatsRepository.update*()` 与 `LogRepository.save()`
- `persistence/StatsRepository.java` 已有 5 张 stats 表的累加 UPSERT 能力
- `persistence/LogRepository.java` 只有单条 `save()`，尚无批量写入
- `task/` 包目前为空，尚无 `@EnableScheduling` 或定时任务实现

## Goals

1. 建立可运行的后台任务机制。
2. 将 stats 写入改为内存聚合后刷盘。
3. 将 relay log 写入改为缓冲后批量刷盘。
4. 保持现有读路径与管理端查询接口不变。
5. 提供可重复、非 flaky 的自动化验证方式。

## Non-Goals

- 通用缓存框架
- 分片缓存或高阶并发优化设施
- 动态任务配置中心
- 与本任务无关的后台任务（模型同步、延迟探测、自更新等）

## Architecture

### 1. 调度入口

新增 `task/TaskConfiguration.java`：

- `@Configuration`
- `@EnableScheduling`

新增两个任务 Bean：

- `task/StatsFlushTask.java`
- `task/LogFlushTask.java`

二者使用固定周期调度，首版直接采用**30s 硬编码间隔**；不在本任务接入 `setting` 表动态配置。

### 2. Stats 聚合

新增 `task/StatsAccumulator.java`，负责暂存 5 类 stats 增量：

- `stats_total`
- `stats_daily`
- `stats_hourly`
- `stats_apikey`
- `stats_channel`

#### 关键约束：按“记录发生时”归桶，而不是按“刷盘时”归桶

当前 `RelayMetricsRecorder` 在写库前通过 `LocalDateTime.now()` 计算 `today/hour`。改成异步刷盘后，如果仍在 flush 时计算时间桶，会在跨小时或跨天时把数据写入错误分区。

因此新设计要求：

- `RelayMetricsRecorder.record(...)` 内部先捕获 `recordedAt`
- `StatsAccumulator` 在 `record(recordedAt, statsDelta, apiKeyId, channelId)` 时就决定：
  - total
  - daily(date)
  - hourly(hour + date)
  - apikey(apiKeyId)
  - channel(channelId)

#### 并发模型

首版优先正确性与最小改动，使用**单锁 + 可交换快照状态**：

- `record(...)` 在锁内把增量 merge 到当前状态
- `drain()` 在锁内取出当前状态并替换成新的空状态
- flush 在锁外遍历被 drain 的快照并写库

这样可以避免：

- flush 与 record 并发时的丢增量
- 复杂的原子 swap + late writer 竞态

考虑到写入动作发生在“每次 relay 结束时一次”，本任务中该方案足够简单且可靠。

### 3. Log 缓冲

新增 `task/LogBuffer.java`：

- 使用 `ConcurrentLinkedQueue<RelayLog>` 缓冲日志
- 提供 `append(RelayLog log)`
- 提供 `drainAll()` 返回当前批次日志

log 不需要按时间桶聚合，只需保证：

- 进入队列的日志不丢失
- flush 时可一次性批量写库

### 4. Relay 写路径改造

修改 `relay/metrics/RelayMetricsRecorder.java`：

- 保留成本计算、usage 解析、`RelayLog` 构建逻辑
- 去掉对 `StatsRepository` / `LogRepository` 的直接依赖
- 改为依赖：
  - `StatsAccumulator`
  - `LogBuffer`

写路径变化：

1. 计算 `StatsMetrics`
2. 捕获 `recordedAt`
3. `statsAccumulator.record(...)`
4. `logBuffer.append(log)`

### 5. Flush 写库

#### StatsFlushTask

职责：

- 周期性调用 `statsAccumulator.drain()`
- 对非空快照中的各 bucket 调用现有 `StatsRepository.update*()`

首版**复用现有 repository 单条 UPSERT 能力**，不引入新的 stats 批量 SQL 抽象；“批量”体现在：一批 relay 请求的增量先合并成更少的 bucket 更新，再统一 flush。

#### LogFlushTask

职责：

- 周期性调用 `logBuffer.drainAll()`
- 调用 `LogRepository.saveAll(List<RelayLog>)`

因此 `LogRepository` 需要新增 `saveAll(...)`，内部使用 `JdbcTemplate.batchUpdate(...)` 批量插入 `relay_log`。

### 6. 正常关闭时 flush

本任务纳入“应用正常关闭时 best-effort flush”。

实现原则：

- 在 flush task 或独立 coordinator 上提供可复用的 `flushOnce()` 能力
- 在 Spring 容器正常关闭阶段调用一次 stats/log flush
- 目标是减少正常停机场景下的缓冲数据丢失；不承诺覆盖进程崩溃、kill -9 等非优雅退出

## Testing Strategy

### 单元测试

新增：

- `StatsAccumulatorTests`
  - 同 bucket 累加正确
  - 不同 daily/hourly bucket 正确归类
  - `drain()` 后状态清空
- `LogBufferTests`
  - append / drainAll 行为正确

### 集成测试

优先复用现有测试基础设施：

- `relay/it/RelayObservabilityIT.java`
- `persistence/ModelLogStatsIntegrationTests.java`
- `OctobusApplicationTests.java`

关键点：

1. `RelayObservabilityIT` 当前依赖“relay 完成后立刻落库”的行为；改造后应通过显式触发 flush，再断言 DB 结果，避免用 `sleep` 等待定时任务。
2. `ModelLogStatsIntegrationTests` 继续作为读取链路不变的验证。
3. `OctobusApplicationTests` 继续验证引入调度配置后应用可正常启动。

### 为测试提供的可调用入口

为了让集成测试稳定，`StatsFlushTask` / `LogFlushTask` 除 `@Scheduled` 方法外，应提供一个可直接调用的 `flushOnce()`（或同等语义方法），供测试在断言前显式执行。

该策略也是本任务的既定测试语义：

- 不再要求“relay 完成后 DB 立即可见”
- 改为“显式 flush 后 DB 可见”
- 禁止通过 `sleep` 或等待定时器来完成断言

## Risks and Mitigations

### 风险 1：跨小时 / 跨天错误归桶

缓解：在 `RelayMetricsRecorder.record(...)` 时捕获 `recordedAt`，归桶决策前移。

### 风险 2：现有 observability 集成测试因“非即时落库”失效

缓解：让测试显式调用 flush 方法，不依赖调度时间。

### 风险 3：flush 与 record 并发导致丢数据

缓解：首版采用单锁 drain 模型，优先保证正确性。

### 风险 4：读路径行为被意外改动

缓解：不改 `StatsService` / `LogService` 对外 API，只改 relay 写路径与 repository 的写实现。
