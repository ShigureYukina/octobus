# Design — Java 专用管理前端

## Context

- 现有 Go 线是单仓结构：`web/`（前端源码）→ `web/out` → `static/out` → Go embed。
- Java 线已经有运行时静态托管能力，但默认仍指向 `./static/out`，与 Go 产物目录重叠。
- 用户要求这次不是“继续共用前端”，而是**复制出 Java 专用前端，并划清与 Go 前后端的边界**。

## Design Goals

1. 让 Java 线拥有独立前端源码与独立产物
2. 避免 Java 线继续依赖 Go 线的前端构建结果
3. 保持 Java 线默认仍为同源托管，而不是前后端分离部署
4. 保持 Go 线现状可继续单独构建和发布
4. 尽量复用 Java 后端现有静态托管能力，减少不必要后端重写

## Boundary Definition

### Go 线保留不动

- Go 前端源码：`web/`
- Go 前端产物：`web/out`、`static/out`
- Go 后端静态嵌入：`static/static.go`
- Go 构建链路：`build.bat`、`scripts/build.sh`

### Java 线新增并独立

- Java 专用前端源码：`java/web/`，不与 `web/` 混用
- Java 专用前端产物：**独立目录**，不复用 `static/out`
- Java 后端静态托管：继续复用 `StaticFrontendRouter`，但默认静态目录改指向 Java 专用产物
- Java 构建链路：新增 Java 线自己的前端构建说明/脚本

## Proposed Architecture

### 1. 前端源码复制

- 以当前 `web/` 为基线复制出 `java/web/`。
- 复制后，Java 线的 API 配置、构建配置、文案/品牌差异（如果需要）都在 Java 专用目录内演进。
- 本次不引入 monorepo 共享包、workspace 抽象或模板生成器；先完成物理隔离。

### 2. 静态产物隔离

- Java 专用前端构建产物输出到 Java 线自己的目录。
- Java 后端的 `octobus.web.static-dir` 默认值同步调整到该目录。
- `StaticFrontendRouter`、`WebProperties` 设计上已支持目录配置，优先复用，不推翻现有托管逻辑。

### 3. API 与运行边界

- Java 专用前端默认只调用 Java 服务暴露的 API。
- Go 线仍只服务 Go 自己的前端与 API。
- Java 线默认采用“前端独立源码 + Java 后端同源托管”的运行方式。
- 两条线都可以继续保留“同源部署优先、跨域可配置”的运行方式，但配置彼此独立。
- 本次不为 Java 线引入单独前端部署站点、反向代理编排或独立发布流程。

### 4. 变更最小化原则

- Go 线只允许做“被动保留”类改动，不主动重构目录和脚本。
- Java 线优先在 `java/` 范围内增量实现自己的前端目录、构建脚本、默认静态目录。

## Key Trade-offs

### 方案：直接复制一套前端

优点：
- 边界最清晰
- Java 线可以独立演进
- 不再因为共享 `web/` 或 `static/out` 产生误部署

代价：
- 两套前端后续会有同步成本
- 初次复制后需要补 Java 线自己的构建脚本和文档

当前选择该方案，因为用户优先级是“划清界限”，不是“降低重复代码”。

## Impacted Areas

- 新增 Java 专用前端源码目录 `java/web/`
- Java 前端构建配置与环境变量
- Java 后端默认静态目录配置
- Java 线构建/运行文档或脚本
- 可能涉及 Java 静态托管相关测试的默认目录断言

## Risks and Mitigations

- **风险：复制后仍误用 Go 产物目录**
  - 通过修改 `application.yml` 默认值和 Java 构建脚本消除默认重叠
- **风险：两套前端目录职责重新混淆**
  - 在 PRD/设计/README 中明确 Go 线与 Java 线的目录边界
- **风险：Java 前端复制后 API 基址仍指向错误目标**
  - 在 Java 专用前端中单独定义 API 基址策略和示例环境变量
- **风险：Go 线构建被顺手改坏**
  - 明确 Go 线不做结构性重构，只做必要的文档说明

## Validation Strategy

- 验证 Java 专用前端可以独立构建
- 验证 Java 服务从新的默认静态目录提供首页与 SPA fallback
- 验证登录页至少可加载并向 Java API 发请求
- 验证 Go 线原有构建入口与目录未被 Java 线反向依赖
