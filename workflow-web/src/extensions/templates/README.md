# 扩展开发模板

这里的 Vue 文件用于复制，不因导入或新增文件而自动注册。对应的可参考 JSON 位于 `manifests/examples/contracts/`，其默认 disabled 状态用 enabled=false 表达。

## 选哪个文件

| 模板 | 适用类型 | 复制后要改什么、保留什么 |
| --- | --- | --- |
| CustomFieldTemplate.vue | FIELD | 替换输入展示，保留 fieldValue、disabled 和 change/blur/focus；参数从 field.componentProps 读取 |
| CustomNodeTemplate.vue | NODE | 根据 config.fieldCodes 显示摘要，可改标题/字段；modelValue 是整份字段对象 |
| CustomFormTemplate.vue | FORM | 替换布局或复用 FormPreviewLinkage，保留无参 validate 与数据更新、只读、联动 |
| CustomListTemplate.vue | LIST | 改列表布局；分页通过事件，业务动作交给 runtime |
| CustomCellTemplate.vue | LIST_CELL | 改单列展示；value 是格式化值，原始记录取 row |
| CustomButtonTemplate.vue | LIST_BUTTON | 实现按钮操作；执行前检查 disabled，提示 reason，自行处理异步错误 |
| CustomConditionTemplate.vue | ACTION_CONDITION | 修改条件参数编辑；返回完整 modelValue，保留 type，需匹配后端判定 |
| RelatedContentTemplate.vue | FORM / LIST | 使用 RELATED_CONTENT 契约；通过 runtime.query/dispatch/refresh 操作关联数据 |

## 从模板到可选择组件

1. 在 `workflow-web/` 执行复制，例如：

```sh
mkdir -p src/extensions/business/order/list-cells
cp src/extensions/templates/CustomCellTemplate.vue src/extensions/business/order/list-cells/OrderStatusCell.vue
```

2. 修改新文件，读取 [contracts](../contracts/README.md) 约定的 props；不要从组件内部调用注册 API。
3. 在 `manifests/business/order/list-cells/order_status.v1.extension.json` 编写 LIST_CELL 清单，name 设为 OrderStatusCell，path 指向新文件，export=default、kind=COMPONENT。
4. 参数放 metadata.configSchema；模板有 suffix 示例，可声明为 text。新业务清单设 enabled=true。
5. 运行 `npm run extensions:check`、`npm run test:extension-contracts` 和 `npm run build`。契约测试只编译模板及契约示例；新业务组件由生产构建实际编译。
6. 在列表设计器的列渲染组件中选择 OrderStatusCell，填写渲染参数、保存并预览。

完整 FIELD 清单和复制步骤见 [扩展中心快速开始](../README.md)。

## 修改模板本身

模板应保留可运行的最小契约和关键注释，避免包含固定业务接口。修改模板不会同步修改已经复制出去的业务组件；共享行为应放在契约辅助函数或模块中。更新模板时同步检查对应 examples/contracts 清单的参数和类型，运行 `npm run test:extension-contracts`。
