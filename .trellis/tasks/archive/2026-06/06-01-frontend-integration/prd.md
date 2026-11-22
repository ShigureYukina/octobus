# Java 专用管理前端

## Goal

从现有 Go 版管理前端复制出一套 **Java 专用前端**，并与原本 Go 前端、Go 后端明确分线：目录独立、构建独立、产物独立、部署独立，避免继续共用同一套前端源码和静态产物。

## Confirmed Facts

- 现有 Go 版管理前端源码位于 `web/`，构建产物位于 `web/out`，再复制到 `static/out` 供 Go 后端嵌入。
- Java 后端当前已具备静态前端托管能力：
  - `java/src/main/java/io/github/shigureyukina/octobus/config/StaticFrontendRouter.java`
  - `java/src/main/java/io/github/shigureyukina/octobus/config/WebProperties.java`
  - `java/src/main/resources/application.yml`
- Java 当前默认静态目录是 `octobus.web.static-dir=./static/out`，这和 Go 版产物目录重叠，不符合“划清界限”的目标。
- 用户已明确要求：
  - “独立出一份 java 使用的前端”
  - “复制一套 web”
  - “划清和原本 go 前后端的界限”

## Requirements

### 功能要求

- 基于现有 `web/` 复制出一套 **Java 专用前端源码目录 `java/web/`**。
- Java 后端启动后，浏览器访问 Java 服务地址即可加载这套 Java 专用前端。
- Java 专用前端继续对接 Java 后端现有管理 API（登录、渠道、模型、用户、日志、统计、设置等）。

### 分线要求

- **源码分线**：Go 前端继续保留在 `web/`；Java 专用前端固定使用 `java/web/`，不再直接复用 `web/` 作为其开发目录。
- **产物分线**：Java 专用前端构建产物使用独立输出目录，不再与 Go 版共用 `web/out` → `static/out` 这条产物链。
- **部署分线**：Java 后端只消费 Java 专用前端产物；Go 后端继续消费 Go 版前端产物。
- **构建分线**：Java 线需有自己明确的构建入口/脚本；不能要求继续走 Go 版 `build.bat` 或 Go 的发布流程。
- **配置分线**：Java 专用前端的 API 基址、环境变量、静态目录配置与 Go 线分开管理。

### 部署形态

- 本任务目标**不是**让 Java 线采用前后端分离部署。
- Java 专用前端源码虽然独立为 `java/web/`，但其构建产物默认仍由 **Java 后端同源托管**。
- 只有在后续新增独立部署需求时，才再扩展 CORS、独立域名、独立发布链路等能力。

### 兼容与边界

- 不把这次工作扩展成 Go 前端重构；Go 版功能保持原样。
- 不要求这次实现 Go/Java 两套前端的源码共享抽象层；本次目标是**先物理拆开**。
- Java 专用前端仍可沿用当前 UI/交互和 API 调用方式，除非为适配 Java 部署必须调整。
- Java 后端现有 API 协议 `{code, message, data}` 不变。

## Acceptance Criteria

- [ ] 仓库内存在独立的 Java 专用前端源码目录 `java/web/`，且与 `web/` 明确分开
- [ ] Java 专用前端有独立构建输出目录，不再使用 Go 线的 `static/out` 作为唯一产物来源
- [ ] Java 后端默认静态目录改为消费 Java 专用前端产物，而非 Go 版前端产物
- [ ] Go 前端/Go 后端的现有源码目录、构建链路和运行方式不因本任务被改成依赖 Java 线
- [ ] Java 服务启动后，至少完成登录页加载与管理前端首页可访问验证
- [ ] 相关任务文档明确写出 Go 线与 Java 线的目录、构建、产物、部署边界

## Out of Scope

- 不在本任务中统一两套前端的长期同步机制
- 不在本任务中重构 Go 后端静态资源嵌入方案
- 不在本任务中把 Java 前端改造成 SSR 或引入新的前端框架
- 不在本任务中把 Java 线改成默认前后端分离部署

## Decision

- Java 专用前端源码目录固定为 `java/web/`
