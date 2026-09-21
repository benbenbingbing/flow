# 平台内置扩展

`fields/` 实现标准表单控件，`list-cells/` 实现标准单元格。注册声明位于 [platform 清单](../manifests/README.md)，应用启动时自动安装。

## 如何使用现有控件

1. 表单设计器中选择字段，使用平台组件类型，例如 `input`、`number`、`textarea`。字段类型的默认控件由平台清单决定。
2. 列表设计器中编辑列，在渲染组件处选择 `DefaultText`、`StatusBadge` 或 `DateFormatter`，再设置该列的渲染参数。
3. 保存、预览、发布当前表单或列表。无需导入组件、编写新清单或调用注册函数。

控件参数的配置示例分别见 [表单字段用法](#标准表单字段) 和 [单元格用法](#内置列表单元格)。开发宿主时通过注册表查找实现，避免直接绑定某个内部 Vue 文件。

## 如何修改或新增平台能力

- 修改所有使用方共用的行为：修改此处实现，并核对对应清单的 `configSchema`、兼容类型和别名。
- 只服务一个业务的变化：复制模板到 `business/<module>/`，使用独立注册名与业务清单。
- 新增平台字段：实现后在 `manifests/platform/fields/` 写 JSON；确认哪些类型默认使用它，避免与已有 `defaultForFieldTypes` 冲突。

以下命令从 `workflow-web/` 执行；修改平台字段清单后先更新生成数据：

```sh
npm run extensions:generate
npm run extensions:check
npm run test:extensions
npm run build
```

## 标准表单字段

本节文件路径相对 `builtin/fields/`。

`components/` 提供文本、数字、日期、选择、附件、实体引用和子表等控件；`composables/useFormField.js` 共享值同步、选项、禁用态、默认值和标准事件。注册名、别名和类型兼容范围来自 [平台字段清单](../manifests/platform/fields)。

### 在表单中使用

在表单设计器选择实体字段，再选择组件并填写参数。下面是数字字段的配置片段，不是完整表单，也不是注册清单：

```json
{
  "fieldCode": "amount",
  "fieldType": "DECIMAL",
  "componentType": "number",
  "componentProps": { "min": 0, "max": 10000, "precision": 2, "step": 0.5 }
}
```

预览时应显示数字输入框，范围和精度按参数生效。节点树表单请通过设计器选择组件，由设计器写入节点绑定；不要把上述平铺字段片段直接替换整个节点文档。

### 开发控件时复用逻辑

以 [CustomFieldTemplate.vue](../templates/CustomFieldTemplate.vue) 为起点，在 Vue 的 `script setup` 中使用：

```js
import { formFieldProps, formFieldEmits } from '@/extensions/contracts/form-field.js'
import { useFormField } from '@/extensions/contracts/runtime.js'

const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
const { fieldValue, isDisabled, parsedComponentProps, handleChange, handleBlur, handleFocus } = useFormField(props, emit)
```

将控件的值和禁用属性绑定到 `fieldValue`、`isDisabled`，用户输入调用 `handleChange`，失焦/聚焦调用对应方法。参数读取 `parsedComponentProps`，这样同时兼容对象和 JSON 字符串。不要再手工 emit 第二次 change；该 helper 已处理标准事件。

### 开发宿主时查找组件

应用安装扩展后调用 `resolveFieldComponent(field)`，由它处理子表和实体引用的特殊优先级。`getFormFieldComponent()` 只查非平台字段，不能用来判断 `number` 是否已安装。

从 `workflow-web/` 运行 `node src/extensions/builtin/fields/composables/__tests__/useFormField.spec.js` 检查共享逻辑；修改清单时同时运行 `npm run extensions:generate` 和 `npm run test:extensions`。

## 内置列表单元格

本节实现位于 `builtin/list-cells/`。

此目录的三个组件由 [平台单元格清单](../manifests/platform/list-cells) 自动注册，业务列表无需再次注册。

### 如何配置

在列表设计器中编辑目标列，选择渲染组件并填写渲染参数，保存后预览：

| 组件名 | 适合的列 | 参数示例与预期结果 |
| --- | --- | --- |
| `DefaultText` | 普通文本 | `{"emptyText":"暂无"}`：空值显示“暂无” |
| `DateFormatter` | 日期、时间 | `{"pattern":"yyyy-MM-dd"}`：显示年、月、日 |
| `StatusBadge` | 状态 | `{"size":"small","statusMap":{"approved":"success"}}`：原始状态 approved 使用成功色 |

例如日期列的配置片段：

```json
{
  "fieldCode": "createdAt",
  "renderComponent": "DateFormatter",
  "renderConfig": { "pattern": "yyyy-MM-dd HH:mm" }
}
```

`renderComponent` 使用注册名，`renderConfig` 是该列的实例参数；不填写 Vue 路径。日期格式使用当前实现支持的 `yyyy/MM/dd/HH/mm/ss` 标记。

### 如何扩展单元格

复制 [CustomCellTemplate.vue](../templates/CustomCellTemplate.vue) 到业务目录，使用 `listCellProps`，再新增 LIST_CELL 清单。组件的 `value` 已经格式化；原始状态或 ID 应从 `row` 读取。`StatusBadge.statusMap` 按小写原始值匹配，`labelMap` 按传入展示值匹配。

仅改变某列的格式时优先配置参数；新增业务呈现用独立组件名。修改后从 `workflow-web/` 执行 `npm run extensions:check`、`npm run test:list-cell-actions` 和 `npm run build`，并在目标列表验证空值、真实行数据与参数效果。
