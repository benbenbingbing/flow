# 前端扩展契约

本目录集中提供可导入的 JS 契约、可继承的校验类、Vue 模板和接入示例，减少自定义页面重复编写 props、事件、配置解析和表单逻辑。

模板和示例按需注册。自定义校验已接入设计器、草稿/发布配置及表单运行时；应用默认注册了金额校验器。只有在当前表单字段中绑定规则才会执行，不会为其他表单自动增加规则。

## 1. 按修改范围选择契约

| 需要自定义的内容 | 定义 / 模板 | 现有接入方式 |
| --- | --- | --- |
| 单个输入控件 | [form-field.js](./form-field.js) / [CustomFieldTemplate.vue](./templates/CustomFieldTemplate.vue) | `registerFormFieldComponent` |
| 跨字段摘要、特殊表单节点 | [form-node.js](./form-node.js) / [CustomNodeTemplate.vue](./templates/CustomNodeTemplate.vue) | `registerFormNodeComponent` |
| 整表单布局或交互 | [form.js](./form.js) / [CustomFormTemplate.vue](./templates/CustomFormTemplate.vue) | `registerCustomFormComponent` |
| 单列显示格式 | [list-cell.js](./list-cell.js) / [CustomCellTemplate.vue](./templates/CustomCellTemplate.vue) | `registerCellComponent` |
| 整列表布局 | [list.js](./list.js) / [CustomListTemplate.vue](./templates/CustomListTemplate.vue) | `registerCustomListComponent` |
| 工具栏按钮的 JS 处理 | [list-action.js](./list-action.js) / [refreshAction](./examples/register.js) | `registerListToolbarAction` |
| 行按钮的 JS 处理 | [list-action.js](./list-action.js) / [refreshAction](./examples/register.js) | `registerListRowAction` |
| 打开列表后的选择结果处理 | [list-action.js](./list-action.js) | `selectionHandler` 复用工具栏或行动作注册表 |
| 按钮外观及点击交互 | [list-action.js](./list-action.js) / [CustomButtonTemplate.vue](./templates/CustomButtonTemplate.vue) | `registerListButtonComponent` |
| 操作条件参数编辑 | [action-rule.js](./action-rule.js) / [CustomConditionTemplate.vue](./templates/CustomConditionTemplate.vue) | `registerEntityActionRuleCondition` |
| 按钮权限候选项 | [action-rule.js](./action-rule.js) / [注册示例](./examples/register.js) | `registerEntityPermissionOptionProvider` |
| 关联内容自定义呈现 | [related-content.js](./related-content.js) / [RelatedContentTemplate.vue](./templates/RelatedContentTemplate.vue) | 按目标类型复用 FORM/LIST 注册入口，使用独立的 props/runtime |
| 表单 / 字段 / 节点接口调用 | [data-source.js](./data-source.js) | 使用宿主传入的 `dataSourceRuntime`；接口先配置并发布 |
| 子表单输入参数与初始化映射 | [subform.js](./subform.js) | 复用原参数解析契约 |
| **新增：自定义校验** | [validation.js](./validation.js) / [AmountValidator.js](./examples/AmountValidator.js) | JS 类或对象实现 `validate(value, context)`，注册后在字段的“数据校验”中绑定，也可由独立页面调用 |

单个格式、后缀、标题等优先作为 `configSchema` 参数暴露。整表单模板已包装标准 `FormPreviewLinkage`，可以仅修改顶部/底部展示，同时保留字段渲染、联动和标准校验。整列表模板是最小展示起点，只包含刷新、可读列、查看和分页；业务需要的查询、导出、选择和其他动作按契约添加。

## 2. 三个主要入口

```js
// 纯定义和新增校验：不依赖 Vue，不注册、不请求、不启动应用。
import { formFieldProps, formFieldEmits, CustomValidator } from '@/contracts/index.js'

// 复用已有注册中心，不创建第二份注册表。调用 register* 才安装业务扩展。
import { registerCellComponent } from '@/contracts/registration.js'

// 复用已有前端能力：会加载 Vue 和平台依赖。
import { useFormField, safeParseConfig, applySchemaDefaults } from '@/contracts/runtime.js'
```

Vue 组件可直接 `defineProps(formFieldProps)`、`defineEmits(formFieldEmits)`。模板位于 `templates/`，可导入复用或复制到业务目录修改。props 中对象和数组的默认值均使用工厂，避免多页面实例共享表单状态；不要直接修改这些导出的 props 定义对象。

所有导出均使用项目现有的 ES Module JS 风格，配合中文 JSDoc 提供方法和上下文说明。例如：

```js
/** @type {import('@/contracts/list-action.js').ListActionHandler} */
const handler = async context => {
  try {
    await context.refresh()
  } catch (error) {
    console.error('刷新失败', error)
  }
}
```

## 3. 一个只改显示的小例子

复用单元格模板，只注册一次，用参数决定后缀：

```js
import Cell from '@/contracts/templates/CustomCellTemplate.vue'
import { registerCellComponent } from '@/contracts/registration.js'

// 放在业务扩展初始化函数中，由已有项目入口显式调用。
registerCellComponent('BusinessAmountCell', Cell, {
  label: '金额显示',
  supportedEntityCodes: ['your_entity'], // 替换为实际实体编码；空数组表示全部实体
  configSchema: [
    { key: 'suffix', label: '单位', type: 'text', defaultValue: ' 元' },
    { key: 'emptyText', label: '空值文本', type: 'text', defaultValue: '—' }
  ]
})
```

在列表字段配置中选择 `renderComponent: 'BusinessAmountCell'`，配置 `renderConfig: { suffix: ' 元' }`，沿现有流程保存并发布。只设置 `configSchema.defaultValue` 不能保证所有手工调用页面都补值，模板仍应给默认显示兜底。

注册元数据详见 [component.js](./component.js)。完整十类注册函数示例见 [examples/register.js](./examples/register.js)，其中 `registerContractExamples()` **没有被应用调用**。业务扩展按需放到 `src/project/index.js` 的初始化路径，避免重复注册。校验器入口为 `src/project/validators/index.js`。

## 4. 自定义校验：JS 类或普通对象均可实现

### 4.1 最少只写一个方法

```js
import { CustomValidator } from '@/contracts/validation.js'

export default class PositiveValueValidator extends CustomValidator {
  /** 业务要求必须是正数；空值在本规则中也不通过。 */
  validate(value, context) {
    return typeof value === 'number' && value > 0
      ? true
      : `${context.field?.fieldName || '当前值'}必须大于 0`
  }
}
```

不需要继承时，直接导出 `{ validate(value, context) { ... } }`，或使用 `defineCustomValidator((value, context) => ...)`。异步规则声明 `async validate(value, context)` 即可；依赖的 API 应由业务代码明确导入或注入，不从配置字符串执行 JS。

| 实现返回值 | 统一调用结果 |
| --- | --- |
| `true` | `{ valid: true, message: '' }` |
| `false` | `{ valid: false, message: '校验未通过' }` |
| 非空错误文本 | `{ valid: false, message: 错误文本 }` |
| `{ valid: boolean, message?: string }` | 规范化提示；成功时清空提示 |
| 上述值的 Promise | 等待后按相同规则处理 |
| `throw` / Promise rejection | 原样传播，由页面处理执行故障 |
| 忘记返回、空字符串、数字等 | 抛 `TypeError`，避免误放行 |

### 4.2 在表单设计器绑定，以及实体范围

金额校验已注册为 `amount@1`。打开目标实体的表单设计器 → 选中数值字段 → **数据校验 → 自定义校验 → 添加校验器**，选择“金额校验”，填写金额上限和触发时机，保存并发布表单。各表单字段可以使用不同上限。

新实现放入 `src/project/validators/`，在同目录 `index.js` 的启动注册函数中调用：

```js
import { registerCustomValidator } from '@/contracts/validator-registry.js'
import { AmountValidator } from '@/project/validators/AmountValidator.js'

registerCustomValidator('expenseAmount', new AmountValidator(), {
  label: '报销金额',
  version: 1,
  // [] 或 ['*'] = 全部实体；非空编码列表 = 仅指定实体，不是实体 ID。
  supportedEntityCodes: ['expense', 'purchase_order'],
  supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
  configSchema: [
    { key: 'maxAmount', label: '金额上限', type: 'number', min: 0, required: true, defaultValue: 1000 }
  ]
})
```

**范围是校验器的注册声明**，用于决定哪些实体可选择、执行它；字段绑定属于当前表单，不会变成实体的全局强制规则。设计器筛选范围，运行时再次校验，子表使用子实体编码。受限规则缺少实体身份时会报错。修改实现、参数或适用范围请新增版本并保留旧版本，已发布表单不会自动使用新版本。

配置保存到 `validationRules.customValidators`（节点为 `rules.validation.customValidators`），例如：

```json
{
  "version": 1,
  "rules": [
    { "name": "amount", "version": 1, "params": { "maxAmount": 1000 }, "triggers": ["BLUR", "CHANGE"] }
  ]
}
```

- `BLUR`：仅在真实失焦或提交时检查。再次输入、程序回填及其他字段变化均不重跑，保留上次提示直到下一次失焦/提交。自定义控件须发出 `blur` 或调用 `onFieldBlur`；不发失焦事件的控件在提交时检查。
- `CHANGE`：值变化时检查。`triggers: []` 表示仅提交检查。
- 提交始终执行全部已绑定规则并等待异步结果；隐藏、只读字段跳过，首次加载不显示错误。
- 未安装、版本不匹配、不适用、参数错误、抛异常或忘记返回均阻止提交并提示。数据或身份变化会取消旧结果。
- 参数支持 `text`、`textarea`、`number`、`boolean`、`select`、`json`；只存 JSON，不存函数。每字段最多 20 个校验器；参数最多 12 层、1000 个值，单字符串最多 10000 字符。
- 删除全部规则保存 `{ "version": 1, "rules": [] }`，明确覆盖旧配置。
- 服务端校验并保存协议结构，**不执行前端 JS**。已有服务端业务校验继续生效；需要防止绕过浏览器的业务约束应另行实现服务端校验。

标准表单、节点表单、子表行与自绘整表单的提交入口均接入规则。自绘整表单按契约 emit 新对象以触发 CHANGE；失焦和就地错误可这样接入：

```vue
<el-input :model-value="modelValue.amount"
  @update:model-value="$emit('update:modelValue', { ...modelValue, amount: $event })"
  @blur="context.formCustomValidation?.onFieldBlur('amount')" />
<p>{{ context.formCustomValidation?.errorFor('amount') }}</p>
```

平台传入 `context.entityCode / field / fieldCode / form / formData / record.id / mode / params / trigger / signal`；子表还提供 `parent / row / pageParams`。数据是本次校验的副本，只读使用；异步请求应传递 `signal`。

### 4.3 在普通 JS 或页面中调用

```js
import { validateCustomValue } from '@/contracts/validation.js'
import { AmountValidator } from '@/contracts/examples/AmountValidator.js'

const result = await validateCustomValue(new AmountValidator(), 1200, {
  fieldCode: 'amount',
  field: { fieldName: '金额' },
  formData: { amount: 1200 },
  mode: 'create',
  trigger: 'SUBMIT',
  params: { maxAmount: 1000 }
})
// result 为 { valid: false, message: '金额不能超过 1000' }
if (!result.valid) console.warn(result.message)
```

`context` 由调用方组装，字段详见 `ValidationContext`。契约不会猜测 `record.data`、当前用户、字段值或触发时机。`0`、`false`、空值均原样传入，由业务规则决定含义。跨字段规则从本次 `context.formData` 读取其他字段；类实例方法执行时保留 `this`，可以使用构造函数注入的依赖。

异步请求失败不等于业务通过。输入过程中连续校验时，调用页应管理 `AbortSignal` 或调用序号，只展示最新结果；这个契约不管理页面状态。前端规则负责交互反馈，原服务端业务验证仍照常执行。

### 4.4 Element Plus 表单适配

```js
import { createElementPlusValidator } from '@/contracts/validation.js'
import { AmountValidator } from '@/contracts/examples/AmountValidator.js'

// model 是调用页面维护的表单状态。
const rules = {
  amount: [{
    validator: createElementPlusValidator(new AmountValidator(), () => ({
      formData: { ...model },
      params: { maxAmount: 1000 }
    })),
    trigger: 'change'
  }]
}
```

完整 Vue 调用示例见 [ValidatedAmountForm.vue](./examples/ValidatedAmountForm.vue)。适配函数每次读取最新上下文，把业务不通过转换成 Promise rejection 交给 `el-form` 展示；它不会给现有字段注入规则。

已有整表单/复合字段对宿主暴露的是无参 `validate()`，必须返回 `false` 才明确阻止原宿主继续。自行整合新增契约时，要在该方法中 `await validateCustomValue(...)` 并返回 `result.valid`；**不要把 `{ valid: false }` 直接返回给原宿主**。同时保留原有表单校验，不能只执行新增规则。

## 5. 现有协议的关键差异

| 项目 | 现有宿主行为 / 使用约束 |
| --- | --- |
| 字段与节点值 | FIELD 控件的 `modelValue` 是单值；NODE 和整表单的 `modelValue` 是整份业务字段对象 |
| 字段配置参数 | `field.componentProps`；没有独立 `config` prop。节点、单元格、整表单/列表各自有 `config` |
| 字段事件 | `update:modelValue` 同步值；`change` 触发宿主事件和联动，不能遗漏；默认值 watch 可能主动发更新 |
| 字段扩展选择 | 旧字段使用 `componentType`；节点树 FIELD 扩展使用 `componentName` 和 `props.componentExtensionType: 'FIELD'`，不能误当 NODE |
| 自定义节点 | 当前分支没有收集自定义节点的 `validate()`；可编辑节点须显式落实字段权限等业务约束 |
| 整列表分页 | 发 `sizeChange` / `pageChange` 事件，`runtime` 没有对应分页方法 |
| 列表单元格值 | 是宿主已格式化的展示值，原始业务值从 `row` 读取；格式化可能已把空值变为占位符 |
| 列表 JS 动作 | 当前宿主不等待 handler；异步实现自行捕获错误，返回值不会触发刷新 |
| 列表按钮组件 | 收到 `disabled/reason/context`；不保证收到按钮 `config`，点击逻辑由组件自己负责 |
| 选择结果回调 | `rows/selectedRows` 是选择结果；`row` 是原宿主行（若有），不是选中的第一条 |
| 表单动作插槽 | 使用 `formActionSlots.slots` 展示，通过 `trigger(runtimeKey/key)` 或 `form-action` 交回宿主；受理不等于动作完成 |
| 操作条件与权限选项 | 前端只编辑配置和列出选项，须有匹配的后端判定与授权；Provider 注册采用追加方式 |
| 关联内容组件 | 没有普通整列表数据 props；使用 `runtime.query/dispatch/refresh`，宿主不收 `update:modelValue` |
| 组件版本 | FORM/LIST 支持版本和制品摘要，同名同版本不同摘要会拒绝；NODE 支持版本及配置迁移；字段、单元格等不能据此假定有同样的多版本查找 |

注册 name 必须与配置中的名称一致；除了已明确大小写归一的字段类型和条件类型，不要假定其他名称忽略大小写。元数据的 `capabilities` 只是描述，不会生成运行方法或权限。

## 6. 配置式扩展与上下文复用

这些扩展也属于系统可自定义范围，但实现是已发布的结构化配置，不是新的前端 JS 注册表：

- **接口数据源**：表单初始化/加载后/提交前、字段选项/默认值/计算、子表单行数据。用 [data-source.js](./data-source.js) 描述的 `executeOwnerUsage(owner, usage, context)` 复用调用；返回值按接口声明的输出映射解读，再显式回填。浏览器提交前只执行标记为无副作用的客户端预校验绑定。
- **声明式联动与字段事件**：沿用设计器的显隐、只读、必填、选项和值联动，以及已发布事件绑定。字段组件通过标准事件通知宿主；普通页面的内置字段通过 useFormField 执行前端脚本，异步完成后通知事件链；自定义字段若要复用脚本需使用该 composable 的处理方法。Embed 页面仍禁止执行脚本。参考 [FormFieldRendererLinkage.vue](../components/FormFieldRendererLinkage.vue)、[EventBindingEditor.vue](../components/ui-config/EventBindingEditor.vue)。
- **列表列模板与扩展列数据**：列模板组合 `renderComponent/renderConfig` 与 `LIST_COLUMN` 接口绑定；前端渲染用单元格契约，数据 Provider 仍在后端。参考 [ListColumnTemplateEditorDialog.vue](../components/ui-config/ListColumnTemplateEditorDialog.vue)。
- **子表单参数与初始化**：通过 [subform.js](./subform.js) 复用原解析方法。父级 `parameterContract.parameterMapping` 映射到子表单 `context.params`，输入结构由 `inputParameterSchema` 声明；`fieldInitializationMapping` 单独处理字段初始化。映射源及对象形式以 [原契约实现](../shared/subform-parameter-contract.js) 为准，建议用设计器生成配置。
- **跨字段规则、唯一预检、必填和类型规则**：这些已有配置规则照常运行。自绘整表单需显示联动/字段错误，并按需要调用 `context.formUniqueness`；用 `runtime.js` 的 `buildRuntimeFieldRules` 复用内置单字段规则。已绑定的自定义校验由宿主统一执行；自绘组件可通过 `context.formCustomValidation` 显示字段错误和触发失焦检查。
- **外部页面嵌入**：已有独立包 [@flow/embed-sdk](../../packages/flow-embed-sdk/README.md)，宿主通过 `FlowEmbed.mount` 和 `widget.on` 订阅事件，命令/事件类型以包内 `types.d.ts` 为准。它不是应用内组件注册表，本目录不复制其传输和会话实现。

## 7. 核对来源与验证

定义以当前源码真实调用为准，可从以下位置核对宿主行为：

- [customComponentRegistry.js](../utils/customComponentRegistry.js)、[formNodeRegistry.js](../utils/formNodeRegistry.js)、[form-fields/index.js](../components/form-fields/index.js)：组件注册与版本行为。
- [EntityDataList.vue](../views/entity/EntityDataList.vue)、[EntityDataTable.vue](../views/entity/components/EntityDataTable.vue)：整列表、按钮组件、JS 动作与选择回调。
- [EntityDataFormFields.vue](../views/entity/components/EntityDataFormFields.vue)、[FormPreviewLinkage.vue](../components/FormPreviewLinkage.vue)、[FormNodeRuntimeItem.vue](../components/FormNodeRuntimeItem.vue)：表单、字段和节点入参及验证调用。
- [RelatedContentRuntime.vue](../components/related-content/RelatedContentRuntime.vue)、[ActionRuleGroupEditor.vue](../components/ActionRuleGroupEditor.vue)、[entityActionRuleRegistry.js](../utils/entityActionRuleRegistry.js)：关联内容、条件编辑和权限选项。
- [src/project](../project/index.js)：已有业务实现，不因本目录新增而改变。

在 `workflow-web` 目录运行：

```sh
node --test src/contracts/__tests__/validation.spec.js
node src/contracts/__tests__/bundle.spec.js
```

第一项验证新增规则的同步/异步、跨字段上下文、异常和适配行为。第二项对所有新 Vue 模板、示例和转导出入口做实际 Vite 打包检查，只在内存生成产物，不修改应用入口或构建目录。现有 `package.json` 没有新增脚本。

数据库迁移：本次未新增、修改或删除任何迁移文件。
