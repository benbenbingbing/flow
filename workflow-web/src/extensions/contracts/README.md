# 前端扩展契约使用说明

本目录定义组件 props、emits、动作上下文与校验协议。组件通过这些定义与宿主对接，导入契约不等于注册扩展。实现完成后仍需按 [清单手册](../manifests/README.md) 添加 JSON。

## 根据实现选择文件

| 文件 | 如何使用、注意什么 |
| --- | --- |
| form-field.js | defineProps(formFieldProps)、defineEmits(formFieldEmits)；modelValue 是单个字段值 |
| form-node.js | 使用 formNodeProps/formNodeEmits；modelValue 是整份业务字段对象，更新需保留其他字段 |
| form.js | 使用 customFormProps/customFormEmits；处理模式、联动、错误和动作插槽，并暴露 validate() |
| list.js | 使用 customListProps/customListEmits；消费宿主数据/分页并通过 runtime 执行受控动作 |
| list-cell.js | 使用 listCellProps；config 来自列渲染参数，value 已经格式化 |
| list-action.js | JS handler 按注释读取 context；Vue 按钮使用 listButtonProps 并遵守 disabled/reason |
| action-rule.js | 使用 actionRuleConditionProps/Emits 编辑条件；权限提供器按注释返回候选项 |
| related-content.js | 使用 relatedContentProps；通过 runtime.query/dispatch/refresh 操作关联内容 |
| validation.js | 实现 CustomValidator 或 defineCustomValidator；用 validateCustomValue 归一化结果，或适配 Element Plus |
| data-source.js | 参照注释和 FORM_DATA_SOURCE_USAGES 使用宿主传入的受控数据源，不自行构造发布身份 |
| subform.js | 阅读子表输入参数、父级/行上下文和初始化映射约定；这是文档契约，不是独立注册器 |
| component.js | 参考元数据类型说明，使用 FORM_MODES、LIST_BUTTON_MODES 常量 |
| runtime.js | 在 Vue 宿主中复用 useFormField、配置解析、默认值、规则与动作辅助能力 |
| index.js | 汇总纯契约导出；需要运行帮助时单独导入 runtime.js，子表说明直接读 subform.js |

## Vue 组件接入示例

下面可用于一个单元格组件；完成后声明 LIST_CELL JSON，在列配置中选择其 name：

```vue
<template>
  <span>{{ value ?? config.emptyText ?? '—' }}</span>
</template>

<script setup>
import { listCellProps } from '@/extensions/contracts/list-cell.js'
defineProps(listCellProps)
</script>
```

字段组件可照 [CustomFieldTemplate.vue](../templates/CustomFieldTemplate.vue) 导入 formFieldProps/emits 和 useFormField。保留宿主传入的 readonly/disabled，不原地修改 props 中的对象；数组和对象默认值采用工厂。

## 校验契约怎么调用

```js
import { defineCustomValidator, validateCustomValue } from '@/extensions/contracts/validation.js'

const rule = defineCustomValidator(value => value !== '' || '请填写内容')
const result = await validateCustomValue(rule, '', { fieldCode: 'remark' })
if (!result.valid) console.log(result.message)
```

字段校验的 validate(value, context) 可返回 boolean、非空错误文本或 {valid,message}，可为异步；不要把返回文本的真值当成通过。整表单组件的 validate() 是另一套无参接口，应返回 boolean/Promise<boolean> 并通过 defineExpose 暴露。错误和接口异常不能转为校验成功。

Element Plus 独立页面可参考 [ValidatedAmountForm.vue](../examples/contracts/ValidatedAmountForm.vue)，按每次校验的最新上下文调用 createElementPlusValidator。

## 关联内容与普通页面

关联组件必须使用 relatedContentProps，并在 FORM/LIST 清单声明 usageContexts=["RELATED_CONTENT"]。该宿主不监听 update:modelValue，也不自动调用组件 validate；修改应走受控 runtime.dispatch。普通整表单/列表使用 PAGE 契约，不把两套 props 混用。

修改契约后同步检查模板和宿主，从 `workflow-web/` 执行 `npm run test:extension-contracts`；涉及字段校验时执行 `npm run test:custom-validators`。只读、实体范围、授权仍由组件、宿主和后端共同落实。
