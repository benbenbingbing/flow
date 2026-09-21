# 扩展运行核心

本目录供平台宿主、设计器及扩展机制维护者使用。业务新增组件走 [JSON 清单](../manifests/README.md)，通常不修改核心文件。

| 文件/目录 | 用途 | 使用时机 |
| --- | --- | --- |
| `installer.js` | 验证导出、实例化、幂等安装 | 应用入口或隔离单元测试 |
| `adapters/` | 将十类清单映射到现有注册 API | 安装器验证完成后 |
| `registries/` | 唯一可执行实现查询 | 宿主渲染、设计器选项 |
| `catalog.js` | 已安装扩展的纯元数据 | 目录查询和筛选 |
| `catalogRows.js` | 将元数据投影为管理表格行 | 合并前端与后端扩展目录 |
| `fieldPolicy.js` | 字段默认控件与兼容范围 | 新建字段或检查组件兼容性 |

## 应用初始化怎么用

现有 `src/main.js` 和 `src/embed/embed-main.js` 已调用 `registerApplicationExtensions()`，普通组件不要再次初始化。新宿主应在挂载前调用 `extensions/register.js`，然后查询本目录的注册表。注册失败时停止挂载并修复报错清单，刷新页面重试。

## 查询已经安装的扩展

在应用模块中使用；调用应发生在初始化完成后：

```js
import { getExtensionCatalog } from '@/extensions/core/catalog.js'
import { localExtensionRows } from '@/extensions/core/catalogRows.js'
import { getDefaultFormFieldComponentType } from '@/extensions/core/fieldPolicy.js'

const fields = getExtensionCatalog({ type: 'FIELD', origin: 'BUSINESS' })
const rows = localExtensionRows(getExtensionCatalog(), new Set(), { capabilityType: 'UI_VALIDATOR' })
const defaultType = getDefaultFormFieldComponentType('DECIMAL') // number
```

`getExtensionCatalog()` 返回副本；不会查数据库。`localExtensionRows` 第二个参数是已被后端纳管的身份集合，格式如 `UI_FORM:ProjectMemberChangeForm:1`；过滤参数还可使用 status、implementationOrigin、keyword。`fieldPolicy` 是纯数据策略，可在不加载 Vue 的 Node 检查中使用。

## 平台测试或修改安装器

通过 `createExtensionInstaller(adapters, publish)` 创建独立实例，注入测试适配器，不污染实际注册表。参考 [registration.spec.mjs](../__tests__/registration.spec.mjs)。应用里的安装器由 register.js 持有，不要为每个业务模块新建一套。

安装过程只创建对象，不请求接口或执行业务操作。相同计划重复调用无副作用；清单改变需要整页重载，底层 Map 不提供事务回滚。从 `workflow-web/` 执行 `npm run test:extensions`；修改真实接入链路时再执行 `npm run test:extensions:browser` 和 `npm run build`。

## 注册适配器

本节文件路径相对 `core/adapters/`。

`index.js` 的 `extensionAdapters` 把已校验的 JSON 描述映射到各类型注册表。`installer` 负责加载形态、工厂和类实例；适配器负责最终安装。

### 现有类型如何使用

业务代码只提供实现和 JSON，不直接调用适配器。安装器内部按下列协议调用：

```js
// 平台内部调用约定；不是业务组件的注册示例。
extensionAdapters[entry.type](entry, value, metadata)
```

- `entry`：构建器生成的清单，含 type/name/version/targets/defaultConfig 等。
- `value`：Vue 组件、动作/权限函数或已经创建的校验器实例。
- `metadata`：配置 Schema、能力、来源、名称/版本以及已经导入的函数钩子。

| 类型 | 适配行为 |
| --- | --- |
| FORM / LIST / FIELD / NODE / LIST_CELL | 调用相应组件注册表并传递元数据 |
| LIST_BUTTON | 按名称安装按钮 Vue 组件；参数由宿主上下文提供 |
| LIST_ACTION | 按 targets 安装到工具栏、行或两个位置，不执行函数 |
| VALIDATOR | 安装已有 validate 方法的对象，不在此处 new 类 |
| ACTION_CONDITION | 安装编辑器；使用 createDefault 钩子或深复制 defaultConfig |
| PERMISSION_PROVIDER | 追加候选项函数，页面查询时才执行 |

### 如何新增一种平台类型

仅当现有十类无法表达新的宿主能力时修改此处：先定义契约与运行宿主，再扩展 JSON Schema 和 build/extensions/validate.mjs 的类型检查，随后增加适配器、注册表、目录展示和测试。只在适配器添加一个键不会让设计器或后端自动支持新类型。

从 `workflow-web/` 运行 `npm run test:extensions` 验证注册映射，再运行 `npm run test:extensions:browser` 和 `npm run build` 检查真实组件导出及两种应用入口。

## 类型注册表

本节文件路径相对 `core/registries/`。

各文件保存对应类型的唯一运行实现。生产写入由 [adapters](#注册适配器) 执行；宿主和设计器使用 get/has/query 接口。导入注册表不会自动安装组件。

### 如何查询

先由应用入口安装扩展，再在应用模块中读取：

```js
import { resolveFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'
import { getCellComponent } from '@/extensions/core/registries/listCellRegistry.js'
import { getCustomFormComponent, getCustomFormComponentOptions } from '@/extensions/core/registries/customComponentRegistry.js'

const Field = resolveFieldComponent({ fieldType: 'DECIMAL', componentType: 'number' })
const Cell = getCellComponent('StatusBadge')
const pageForms = getCustomFormComponentOptions('PAGE')
const RelatedForm = getCustomFormComponent('ContractExampleRelatedForm', 1, undefined, 'RELATED_CONTENT')
```

将找到的组件交给 Vue 的动态组件 `:is`；返回 undefined 时由宿主显示缺失或不匹配提示。最后一行只有启用对应示例清单和 Demo 后才有结果；已发布配置携带摘要时应使用其 artifactDigest 替代 undefined。

### 按类型选择 API

| 注册表文件 | 常用读取接口及用途 |
| --- | --- |
| customComponentRegistry.js | getCustomForm/ListComponent、Options、VersionOptions；默认宿主 PAGE，关联内容显式传 RELATED_CONTENT |
| formFieldRegistry.js | resolveFieldComponent 解析平台/自定义字段；getFormFieldComponent 只查非平台字段 |
| formNodeRegistry.js | resolveFormNodeDescriptor 校验节点类型和绑定；migrateFormNodeConfig 处理旧配置 |
| listCellRegistry.js | getCellComponent / getCellComponentOptions 查询列渲染器 |
| listButtonComponentRegistry.js | getListButtonComponent 按名称读取按钮组件 |
| listActionRegistry.js | getListToolbarAction / getListRowAction 按按钮位置读取处理函数 |
| validatorRegistry.js | getCustomValidator(name, version)；getCustomValidatorOptions(entityCode, fieldType) 查询适用规则 |
| entityActionRuleRegistry.js | getEntityActionRuleConditions 查询条件编辑器；await resolveEntityPermissionOptions(context) 收集权限候选 |

表中 Form/List 表示两组分别存在的函数名，不是字面 API 名称。动作返回的 handler 由宿主在点击时调用，不要在初始化时执行。校验器通过 `validateCustomValue(descriptor.validator, value, context)` 归一化结果，避免把错误文本当作成功。

FORM/LIST/NODE/VALIDATOR 保留多版本查询；其余类型由清单检查阻止同名多版本覆盖。修改这些接口时从 `workflow-web/` 运行 `npm run test:extensions` 与相关宿主测试；业务实现不要绕过 JSON 调用 register 函数。
