# 项目业务模块

`@flow/biz-project` 包含项目成员变更、项目验收以及相关列表、字段和动作。
项目专属实现、扩展清单和测试均在本目录维护。

`src/extensions/<类型>/<注册名>/` 包含同一扩展的 JSON、各端实现及专属测试。PC 与 Embed 在 `pc/`，移动端在 `mobile/`，扩展自己的跨端逻辑在 `shared/`。跨扩展的验收用例在模块根的 `tests/`。

清单使用相对目录的 `implementation.path`，移动端使用 `platforms.mobile.implementation.path`。运行依赖写在本包的 `package.json`，业务实现按需使用 `@flow/workflow-extensions/contracts/*` 和 `/host`。

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
- [清单配置手册](../../packages/workflow-extensions/src/extensions/README.md)
- [项目业务验收](../../../docs/project-extension-acceptance/README.md)
