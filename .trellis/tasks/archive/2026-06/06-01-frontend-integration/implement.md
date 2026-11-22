# Implement Plan — Java 专用管理前端

## Execution Checklist

1. 从 `web/` 复制出 `java/web/` 前端目录
3. 调整 Java 专用前端的包信息、构建配置、环境变量说明
4. 为 Java 专用前端指定独立构建输出目录
5. 调整 Java 后端默认静态目录配置，指向 Java 专用前端产物
6. 视情况补 Java 线构建脚本/启动说明
7. 校验 Java 后端同源托管、SPA fallback、登录页加载与 API 请求路径
8. 回归确认 Go 线目录、构建链路和静态产物链未被绑定到 Java 线

## Planned File Focus

- `java/src/main/resources/application.yml`
- `java/src/main/java/io/github/shigureyukina/octobus/config/StaticFrontendRouter.java`（若仅目录切换则尽量不改）
- `java/src/main/java/io/github/shigureyukina/octobus/config/WebProperties.java`（通常无需改）
- 新增 Java 专用前端目录 `java/web/`
- Java 线相关构建脚本 / README / 文档

## Validation Commands

- `cd java/web && pnpm install && pnpm run build`
- `cd java && mvn test`
- `cd java && mvn spring-boot:run` 或等效启动命令
- 访问 `/`、任一 SPA 路由、`/api/v1/user/login` 联调验证

## Review Gates

- Gate 1：Go 线和 Java 线的源码目录是否已彻底分开
- Gate 2：Java 默认静态目录是否仍误指向 Go 的 `static/out`
- Gate 3：Java 构建链路是否无需依赖 Go 的 `build.bat` / `scripts/build.sh`
- Gate 4：Java 线是否仍保持同源托管，而非被做成前后端分离部署
- Gate 5：登录页请求是否明确落到 Java API

## Rollback Points

- Java 专用前端目录可整体回退，不影响 Go 线
- `application.yml` 静态目录默认值可单独回退
- Java 静态托管逻辑若无需修改，应尽量保持可零回滚

## Blocking Decision

- Java 专用前端源码目录已确定为 `java/web/`
