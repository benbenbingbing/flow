# Flow 前端工作区

本目录是唯一的 npm workspaces 根目录，管理 PC/Embed、移动端、公共包和业务扩展。
产品扩展能力统一由 `@flow/workflow-extensions` 提供；业务包名、注册名、版本和扩展配置协议保持不变。

## 目录职责

```text
workflow-frontend/
├── package.json / package-lock.json    # workspace 清单与唯一锁文件
├── Dockerfile / .dockerignore          # PC/Embed 与移动端的统一镜像构建
├── nginx.conf                         # 三端入口、反向代理与 Embed Origin 隔离
├── workflow-web/                       # PC/Embed 应用、设计器
├── workflow-mobile/                    # Vant 移动应用
├── packages/                           # 产品维护的公共包
│   ├── workflow-core/
│   ├── workflow-api/
│   ├── workflow-extensions/
│   │   ├── src/contracts/              # 运行时契约
│   │   ├── src/host/                   # 宿主能力注入
│   │   ├── manifests/                  # platform/common/examples 产品清单
│   │   └── schemas/                    # 所有清单共用的格式规范
│   └── workflow-mobile-ui/
├── custom/                             # 自定义业务的唯一开发目录
│   └── biz-project/
│       ├── package.json
│       ├── src/                        # shared/api/pc/mobile
│       ├── manifests/                  # 同一业务的 PC/移动声明
│       ├── tests/
│       └── README.md
├── config/frontend-modules.json        # 交付业务模块选择
├── scripts/                            # 构建、监听、生成与边界检查
└── examples/                           # Embed 接入示例
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

两条 dev 命令各自构建并监听所需产品包和选中业务包，等待所有包 watcher 首轮构建完成后启动应用；
模块选择变化会串行重建并重启应用。`npm run test:business` 自动运行所有自定义模块声明的测试，
不受当前交付选择影响。
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
Dockerfile、Nginx 配置和 `.dockerignore` 统一位于本目录。
Docker 的构建上下文为 `workflow-frontend/`，与根 Compose 的 `build.context` 一致。
以下命令在仓库根执行，最后的参数指定前端工作区作为构建上下文：

```bash
docker build -t flow-web:local workflow-frontend
FLOW_FRONTEND_MODULES=none docker compose build web
```

## 扩展合并验证

2026-10-17，业务模块集中到 `custom/`、扩展契约与产品清单合并后的验证已通过：

- 架构、启动与容器输入检查共 82 项；公共包 68 项、业务 14 项、移动单元测试 27 项。
- PC 完整 `test:regression`、扩展浏览器测试和统一组件浏览器测试通过；移动浏览器 E2E 37 项通过。
- 默认选择与 `FLOW_FRONTEND_MODULES=none` 的 PC、Embed、移动生产构建均通过。
  实际模块图确认默认构建只加载各端业务实现，none 三端均没有业务模块。
- 56 份清单的注册名、版本、配置参数及实现入口保持不变，仅业务清单的 Schema 相对路径更新。
  锁文件只调整本地 workspace 名称、位置及依赖声明，324 个第三方依赖条目保持不变。
- 清单校验、源码依赖边界、前端增量维护预算和 Compose 配置校验通过。
- 本地 PC（3000）、移动端（3001）及包 watcher 已从新目录恢复，两个应用均通过启动就绪检查。

Docker daemon 未运行，未执行实际镜像构建；容器 COPY 输入和离线 `npm ci` 依赖计划已验证。
本次未新增、修改或删除数据库迁移文件。

## 前一次工作区迁移验证（历史记录）

以下记录对应将前端整体搬入 `workflow-frontend` 的前一次迁移，不代表本次扩展合并已重新验收。

2026-10-16，前一次迁移后的验证已通过：架构 69 项、公共包 76 项、业务包 14 项、
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
- [扩展注册声明手册](packages/workflow-extensions/manifests/README.md)
- [PC 扩展中心和模板](workflow-web/src/extensions/README.md)
- [移动工作台](workflow-mobile/README.md)
- [Embed 本地集成示例](examples/embed-local-demo/README.md)
- [仓库启动和部署](../README.md)

根 `docs/`、`output/` 中的历史验收记录保留当时路径。前端路径现以本目录为根；
旧 `packages/biz-*` 及其清单现合并于 `custom/biz-*`，旧 `extensions` 的产品清单和 Schema
现位于 `packages/workflow-extensions`。历史通过记录不代表后续迁移已重新执行对应验收。
