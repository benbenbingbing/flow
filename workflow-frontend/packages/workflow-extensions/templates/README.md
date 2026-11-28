# 扩展开发模板

这里索引可复制的契约示例。每个示例旁已有一份默认禁用的清单，可一并复制到自己的扩展目录。

## 选哪个文件

| 模板 | 适用类型 | 复制后要改什么、保留什么 |
| --- | --- | --- |
| [CustomFieldTemplate.vue](../src/extensions/examples/contracts/fields/contract_example_text/pc/CustomFieldTemplate.vue) | FIELD | 替换输入展示，保留 fieldValue、disabled 和 change/blur/focus；参数从 field.componentProps 读取 |
| [CustomSummaryFieldTemplate.vue](../src/extensions/examples/contracts/fields/ContractExampleNode/pc/CustomSummaryFieldTemplate.vue) | FIELD | 根据 config.fieldCodes 显示摘要，读取 formData；与输入组件使用同一协议 |
| [CustomFormTemplate.vue](../src/extensions/examples/contracts/forms/ContractExampleForm/pc/CustomFormTemplate.vue) | FORM | 替换布局或复用 FormPreviewLinkage，保留无参 validate 与数据更新、只读、联动 |
| [CustomListTemplate.vue](../src/extensions/examples/contracts/lists/ContractExampleList/pc/CustomListTemplate.vue) | LIST | 改列表布局；分页通过事件，业务动作交给 runtime |
| [CustomCellTemplate.vue](../src/extensions/examples/contracts/list-cells/ContractExampleCell/pc/CustomCellTemplate.vue) | LIST_CELL | 改单列展示；value 是格式化值，原始记录取 row |
| [CustomButtonTemplate.vue](../src/extensions/examples/contracts/list-buttons/ContractExampleButton/pc/CustomButtonTemplate.vue) | LIST_BUTTON | 实现按钮操作；执行前检查 disabled，提示 reason，自行处理异步错误 |
| [CustomConditionTemplate.vue](../src/extensions/examples/contracts/action-conditions/CONTRACT_EXAMPLE-FIELD_EQUALS/pc/CustomConditionTemplate.vue) | ACTION_CONDITION | 修改条件参数编辑；返回完整 modelValue，保留 type，需匹配后端判定 |
| [RelatedContentTemplate.vue](../src/extensions/examples/contracts/forms/ContractExampleRelatedForm/pc/RelatedContentTemplate.vue) | FORM / LIST | 使用 RELATED_CONTENT 契约；通过 runtime.query/dispatch/refresh 操作关联数据 |

把示例与清单复制到业务包的 `src/extensions/<类型>/<注册名>/`。按需改用 SDK 契约、宿主能力和业务参数，设置新注册名及相对本清单的实现路径。从前端工作区运行 `npm run extensions:check --workspace workflow-web` 和 `npm run build`。

[清单配置手册](../src/extensions/README.md) · [业务模块开发](../../../../docs/frontend-business-modules.md)
