# Flow 前端工作区

本目录是唯一的 npm workspaces 根目录，管理 PC/Embed、移动端、公共包和业务扩展。
前端包名、注册名、公开入口及依赖方向保持不变；目录迁移不改变扩展配置协议。

## 目录职责

```text
workflow-frontend/
├── package.json / package-lock.json    # workspace 清单与唯一锁文件
├── workflow-web/                       # PC/Embed 应用、设计器、前端 Dockerfile
├── workflow-mobile/                    # Vant 移动应用
├── packages/
│   ├── workflow-core/                  # 通用模型和运行规则
│   ├── workflow-api/                   # 通用 API 工厂
│   ├── workflow-extension-sdk/         # 扩展契约与宿主能力
│   ├── workflow-mobile-ui/             # 通用移动组件
│   └── biz-project/                    # 项目业务 shared/api/pc/mobile
├── extensions/
│   ├── manifests/                     # 注册声明，业务目录使用 biz-* 命名
│   └── schemas/                       # 声明格式规范
├── config/frontend-modules.json        # 交付业务模块选择
├── scripts/                           # 构建、监听、生成与边界检查
└── examples/                          # Embed 等接入示例
```

仓库根的 `start.sh`、`.env`、`deploy/`、`.github/`、`docs/` 继续负责前后端启动、
部署、CI 和仓库文档。前端 workspace 内的路径以本目录为基准；
`implementation.path` 仍相对对应应用，`implementation.package` 仍使用包公开入口。

## 开发与构建

Node.js 至少 22.12，npm 至少 10。从仓库根进入本目录后执行：

```bash
cd workflow-frontend
npm ci
npm run dev:web
# 另一个终端在同一目录启动移动端
npm run dev:mobile
```

两条 dev 命令各自构建并监听所需公共包、SDK、选中业务包；模块选择变化会串行重建并重启应用。
不要在应用或包目录生成另一份锁文件。需要连同后端和数据库一起启动时，在仓库根执行
`./start.sh`；修改模块选择后用 `./start.sh restart` 重启该脚本管理的整组进程。

```bash
# 以下命令在 workflow-frontend/ 执行
npm run build
FLOW_FRONTEND_MODULES=none npm run build
npm run test:architecture
npm run test:packages
npm run test:business
npm run test:mobile
npm run test:e2e --workspace workflow-mobile
npm run test:web
```

默认业务选择由 `config/frontend-modules.json` 管理；`FLOW_FRONTEND_MODULES=none`
构建基础产品，逗号分隔的 `biz-*` 名称可覆盖本次选择。PC/Embed 与移动端仍共同交付在前端镜像中。
Docker 的构建上下文为 `workflow-frontend/`，与根 Compose 的 `build.context` 一致。
以下命令在仓库根执行，最后的参数指定前端工作区作为构建上下文：

```bash
docker build -f workflow-frontend/workflow-web/Dockerfile -t flow-web:local workflow-frontend
FLOW_FRONTEND_MODULES=none docker compose build web
```

## 目录迁移验证

2026-10-16，迁移后的验证已通过：架构 69 项、公共包 76 项、业务包 14 项、
移动单元测试 27 项、Embed 本地示例 18 项、移动浏览器 E2E 37 项；
PC `test:regression`、`test:extensions:browser` 和 `test:unified-components:browser` 均通过。
默认模块和 `FLOW_FRONTEND_MODULES=none` 的 PC、Embed、移动端生产构建均通过。
实际模块图确认默认产物包含各自平台的业务实现，none 三端均不包含业务包。
锁文件内容、workspace 列表和依赖版本保持不变；迁移前已运行的前端服务已从新目录恢复，端口不变。

前端增量维护预算通过。旧全量行数审计仍属于既有治理问题，未通过放宽预算来消除报告。
Docker daemon 未运行，未执行实际镜像构建；构建上下文、离线依赖安装计划和 Compose 配置校验通过。
本次目录迁移未新增、修改或删除数据库迁移文件。

## 开发文档

- [业务模块、SDK、依赖边界与模块选择](../docs/frontend-business-modules.md)
- [扩展注册声明手册](extensions/manifests/README.md)
- [PC 扩展中心和模板](workflow-web/src/extensions/README.md)
- [移动工作台](workflow-mobile/README.md)
- [Embed 本地集成示例](examples/embed-local-demo/README.md)
- [仓库启动和部署](../README.md)

根 `docs/`、`output/` 中的历史验收记录保留当时命令和产物路径；其中旧的
`workflow-web/`、`workflow-mobile/`、`packages/`、`extensions/`、`config/` 和前端 `scripts/`
现位于本目录。历史通过记录不代表目录迁移后已经重新执行对应验收。
