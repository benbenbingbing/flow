# 产品扩展能力

`@flow/workflow-extensions` 统一维护扩展契约、宿主能力、产品清单及清单格式。
它由原 `workflow-extension-sdk` 与产品扩展声明合并而来，业务模块独立放在 `../../custom/`。

```text
workflow-extensions/
├── package.json
├── src/
│   ├── index.js              # 默认只导出契约
│   ├── contracts/            # props、事件、参数和能力协议
│   └── host/                 # 按 Vue 应用注入和获取宿主能力
├── manifests/
│   ├── platform/             # 产品内置扩展
│   ├── common/               # 产品通用扩展
│   └── examples/             # 产品示例
├── schemas/extension.schema.json
└── tests/
```

运行时使用明确的公开入口：

```js
import { useExtensionHost } from '@flow/workflow-extensions/host'
import { listCellProps } from '@flow/workflow-extensions/contracts/list-cell'
```

默认入口与 `/contracts/*`、`/host` 不读取清单、不注册组件。构建工具独立读取
`manifests/` 与 `schemas/`，这两类资源也有单独的包导出；它们不进入运行时入口依赖图。
产品组件的实现仍在所属应用或产品包，清单只声明公开身份和两端实现位置。

自定义业务的实现、清单和测试统一放入 `custom/biz-*/`，不在此包增加业务分支或业务清单。
PC/Embed 使用清单顶层 `implementation`，移动端仅使用 `platforms.mobile.implementation`。
移动实现缺失时不回退到 PC。

- [清单配置手册](manifests/README.md)
- [Schema 使用](schemas/README.md)
- [业务模块开发及依赖边界](../../../docs/frontend-business-modules.md)
