# 前端公共包与业务包

这些包由上一级 `workflow-frontend/package.json` 的 npm workspaces 统一管理，
依赖安装和锁文件更新都在 `workflow-frontend/` 执行。

| 包 | 职责 |
| --- | --- |
| `@flow/workflow-core` | 跨端通用模型、运行规则、校验和可选 Vue 能力 |
| `@flow/workflow-api` | 使用注入 request 的通用 API 工厂 |
| `@flow/workflow-extension-sdk` | 业务扩展可用的契约与宿主注入接口 |
| `@flow/workflow-mobile-ui` | Vant 移动端通用表单和字段组件 |
| `@flow/biz-project` | 项目业务规则、API 组装、PC 和移动实现 |

公共产品包不导入业务包；业务包只使用依赖包的公开 `exports`，不引用应用私有源码。
业务实现由 `../extensions/manifests/biz-*` 清单装配，模块选择来自
`../config/frontend-modules.json`，无需在产品页面硬编码业务导入。

在 `workflow-frontend/` 执行：

```bash
npm ci
npm run build:packages
npm run test:packages
npm run test:business
npm run check:business-boundaries
```

新增业务、宿主能力和依赖边界的完整约定见
[前端业务模块开发](../../docs/frontend-business-modules.md)。
