# 前端扩展中心

路径说明：前端工作区根目录为仓库中的 `workflow-frontend/`，npm 依赖统一在该目录安装；包和清单的相对关系不变。

这里维护 PC/Embed 安装入口及注册表。系统实现和声明位于 `packages/workflow-extensions/src/extensions`，业务实现和声明位于 `custom/biz-*/src/extensions`。主应用与 Embed 共用安装入口。

## 按用途阅读

README 只保留在本目录和下一层目录，子模块用法合并到对应说明中。

| 说明 | 什么时候看 |
| --- | --- |
| [manifests](../../../packages/workflow-extensions/src/extensions/README.md) | 注册配置项完整说明、十类类型、填写示例、启用和排错 |
| [templates](../../../packages/workflow-extensions/templates/README.md) | 选择和复制组件模板，完成从模板到注册的步骤 |
| [contracts](contracts/README.md) | 导入 props/emits、调用校验方法、处理宿主上下文 |
| [builtin](builtin/README.md) | 使用平台字段和单元格，复用 useFormField |
| [common](common/README.md) | 复用金额校验，添加通用实现 |
| [业务模块开发](../../../../docs/frontend-business-modules.md) | biz-* 目录、模块选择、SDK、业务依赖边界与验证 |
| [examples](examples/README.md) | 启用 Demo、使用契约示例、复制成正式业务实现 |
| [core](core/README.md) | 平台开发者查询目录、调用注册表和维护适配器 |
| [schemas](../../../packages/workflow-extensions/schemas/README.md) | 编辑器补全、清单格式校验和协议维护 |
| [generated](generated/README.md) | 更新和读取平台字段的派生数据 |
| [__tests__](__tests__/README.md) | 按改动范围选择测试、运行浏览器验收 |

## 从模板新增一个字段

从[扩展模板](../../../packages/workflow-extensions/templates/README.md)复制字段实现和清单，放入业务包同一个目录。例如：

```text
custom/biz-order/src/extensions/fields/OrderNoteField/
├── OrderNoteField.v1.extension.json
├── pc/OrderNoteField.vue
├── mobile/OrderNoteField.vue
└── tests/
```

填写注册名、版本和参数 Schema。顶层 `implementation.path` 指向 `./pc/OrderNoteField.vue`，移动实现由同一清单的 `platforms.mobile.implementation.path` 指向 `./mobile/OrderNoteField.vue`。组件使用 SDK 契约及宿主能力。构建器从模块目录发现清单和实现。

从前端工作区运行 `npm run extensions:check --workspace workflow-web` 和 `npm run build`。进入实体表单设计器选择适用字段，绑定扩展并预览。

## 已有扩展如何配置

| type | 设计器中的用途 | 当前实例参数读取位置 |
| --- | --- | --- |
| FORM | 实体整表单 | form.viewConfig.customComponentProps → config |
| FIELD | 表单组件（输入、展示、子表等） | props.componentProps → config；modelValue 为当前值，formData 为当前表单或子表行 |
| LIST | 整列表布局 | viewConfig.customComponentProps → config |
| LIST_CELL | 列渲染组件 | 列 renderConfig → config |
| LIST_BUTTON | 列表自定义/组件按钮 | mode/row/disabled/reason/context；不保证收到 config |
| LIST_ACTION | 自定义函数按钮、选择回调 | handler(context)，context.config 是按钮配置 |
| VALIDATOR | 字段自定义校验 | validate(value, context)，context.params 是规则参数 |
| ACTION_CONDITION | 操作条件编辑器 | modelValue 是条件参数，需要配套后端判定 |
| PERMISSION_PROVIDER | 权限候选项 | provider(context) 返回选项，需要后端授权支持 |

JSON 的 `metadata.configSchema` 是参数定义，上表中的值才是某个表单或列表的实际配置。参数声明不会自动实现业务行为。

## 入口文件怎么用

- `register.js`：应用启动时安装扩展。现有主应用和 Embed 已调用，业务页面不要再次调用。
- `manifest.js`：应用初始化后读取 `getBundledExtensionManifest()`；需要服务端可治理的四类时使用 `getManagedExtensionManifest()`。
- `index.js`：对外汇总上述接口；普通组件优先直接导入契约或所需查询模块，避免把启动依赖带入实现。

FORM/LIST 用 usageContexts 区分 PAGE 和 RELATED_CONTENT。支持多版本的类型、清单与数据库启停的区别，以及示例开关详见 [注册配置手册](../../../packages/workflow-extensions/src/extensions/README.md)。历史绑定使用稳定名称；制品摘要锁定仍遵守原有检查。
