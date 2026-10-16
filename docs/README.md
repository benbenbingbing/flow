# 仓库文档

本目录保留产品设计、接入规范、测试计划和历史验收资料。前端 npm 工作区已经统一迁入
[`workflow-frontend/`](../workflow-frontend/README.md)；后端仍在 `workflow-server/`，
启动脚本、部署配置和仓库文档继续位于仓库根。

## 当前开发入口

- [仓库启动、构建与部署](../README.md)
- [前端工作区命令和目录](../workflow-frontend/README.md)
- [前端业务模块与 SDK](frontend-business-modules.md)
- [扩展清单手册](../workflow-frontend/extensions/manifests/README.md)
- [移动工作台](../workflow-frontend/workflow-mobile/README.md)
- [后端模块与包边界](backend-package-layout.md)

## 阅读历史资料

历史验收报告保留当时的日期、命令、模块数和产物路径，不因目录迁移重写验证结果。
定位旧前端路径时使用以下对应关系：

| 旧仓库路径 | 当前仓库路径 |
| --- | --- |
| `workflow-web/` | `workflow-frontend/workflow-web/` |
| `workflow-mobile/` | `workflow-frontend/workflow-mobile/` |
| `packages/` | `workflow-frontend/packages/` |
| `extensions/` | `workflow-frontend/extensions/` |
| `config/` | `workflow-frontend/config/` |
| 前端 `scripts/` | `workflow-frontend/scripts/` |
| `examples/` | `workflow-frontend/examples/` |
| 根 `package.json`、`package-lock.json` | `workflow-frontend/` 下的同名文件 |

旧报告中的根目录 npm 命令现在从 `workflow-frontend/` 执行。
`./start.sh`、Docker/Compose、部署脚本和后端命令仍按仓库根或各自文档指定的目录执行。
`docs/examples/` 中的文档示例未移动。源码已提取或退役时，以当前模块 README 和实现为准。
