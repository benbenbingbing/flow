# 项目业务模块

`@flow/biz-project` 包含项目成员变更、项目验收以及相关列表、字段和动作。
项目专属实现、扩展清单和测试均在本目录维护。

| 目录 | 职责 |
| --- | --- |
| `src/shared` | PC 和移动端共用的成员变更、验收模型 |
| `src/api` | 使用宿主请求能力组装业务查询 |
| `src/pc` | PC/Embed 表单、字段、列表、按钮和动作 |
| `src/mobile` | 移动表单和字段 |
| `manifests` | 稳定注册名、版本、参数声明及两端实现入口 |
| `tests` | 业务模型与接入约束回归 |

清单顶层 `implementation.package` 指向 `@flow/biz-project/pc/...`；
移动端实现由同一清单的 `platforms.mobile.implementation.package` 指向 `/mobile/...`。
只在 PC 提供的扩展不声明移动实现，不会被移动端加载。

新增内部辅助组件无需编写清单；需要让设计器或运行时选择的扩展才新增声明。
按需要更新本包 `exports`，通过 `@flow/workflow-extensions/contracts/*` 与 `/host`
使用产品契约及宿主能力，不访问应用私有源码。

在前端根目录 `workflow-frontend/` 执行：

```bash
npm run build:packages
npm run test:business
npm run check:business-boundaries
npm run build
FLOW_FRONTEND_MODULES=none npm run build
```

默认选择来自 `../../config/frontend-modules.json`，禁用模块不会删除已有业务配置。
注册名、版本及制品摘要校验遵循原协议；仅移动目录不要求修改业务参数结构。

- [业务模块开发](../../../docs/frontend-business-modules.md)
- [清单配置手册](../../packages/workflow-extensions/manifests/README.md)
- [项目业务验收](../../../docs/project-extension-acceptance/README.md)
