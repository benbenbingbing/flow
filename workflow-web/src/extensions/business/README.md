# 业务扩展实现

每个业务模块在自己的目录中维护组件、函数、模型、请求与测试。当前项目模块见 [project 使用说明](#项目业务扩展)；注册声明统一位于 [业务清单目录](../manifests/README.md)。

## 新增一个业务模块

1. 建立 `business/<module>/`，按实际需要创建 `forms/fields/nodes/lists/list-cells/buttons/actions/validators/rules/permissions`，模型与请求可放 `model.js`、`api/`。
2. 从 [templates](../templates/README.md) 复制组件，遵守对应 [contracts](../contracts/README.md)；没有界面的动作或校验器直接提供 JS 导出。
3. 在 `manifests/business/<module>/<类型>/` 新增 JSON，把 `implementation.path` 指向实现文件。实现也可以位于 `src/modules/<module>/`。
4. 设置稳定的 `name`、实体适用范围和参数 Schema，运行检查后在实体设计器绑定名称、填写参数、发布。

例如 `business/order/fields/OrderNoteField.vue` 对应 `manifests/business/order/fields/order_note.v1.extension.json`，清单的源码路径为 `src/extensions/business/order/fields/OrderNoteField.vue`。清单示例见 [业务清单用法](../manifests/README.md)。

不要新增模块注册 `index.js`；模块实现无需知道安装顺序。跨业务可复用的代码放 [common](../common/README.md)，稳定业务名称不随文件搬迁改变。

## 验证和接入流程

从 `workflow-web/` 执行 `npm run extensions:check`、模块自身测试和 `npm run build`。在目标实体的新增、编辑、查看、审批模式验证只读和数据更新。流程通过已有的实体表单绑定使用组件，无需另建流程专用注册入口。

## 项目业务扩展

本节表格中的实现路径相对 `business/project/`。

项目组件、`memberChangeModel.js`、`api/memberChange.js` 及原有测试集中在此。安装由 [项目 JSON 清单](../manifests/README.md) 完成，本目录没有注册入口。

### 现有文件怎么使用

| 实现 | 配置使用方式 |
| --- | --- |
| `forms/ProjectMemberChangeForm.vue` | 实体表单选择 `ProjectMemberChangeForm`，配置 `title`、`showRoutePreview`；依赖项目成员变更业务接口与字段模型 |
| `forms/ProjectExtensionAcceptanceForm.vue` | 整表单选择 `ProjectExtensionAcceptanceForm`，配置 `title`、`showRuntimeTrace`，用于扩展验收 |
| `fields/ProjectAcceptanceScoreField.vue` | 数值字段选择 `project_acceptance_score`，在组件参数中配置 `passScore`，例如 80 |
| `fields/ProjectAcceptanceLevelField.vue` | 字符串字段选择 `project_acceptance_level`，按实际字段定义配置 |
| `nodes/ProjectAcceptanceSummaryNode.vue` | 表单节点选择 `ProjectAcceptanceSummaryNode`，设置 `title`；遵守清单限定的 FIELD / ENTITY_FIELD |
| `lists/ProjectAcceptanceBoardList.vue` | 列表选择 `ProjectAcceptanceBoardList`；`PROJECT_CUSTOM_LIST_SCHEMA` 是同一实现的后端 Schema 集成入口 |
| `list-cells/ProjectAcceptanceScoreCell.vue` | 数值列渲染组件选择 `ProjectAcceptanceScoreCell`，渲染参数填写 `passScore` |
| `buttons/ProjectAcceptanceInspectButton.vue` | 列表按钮设为自定义/组件，`customHandler` 填 `ProjectAcceptanceInspectButton` |
| `acceptanceRuntime.js` | 自定义函数按钮按位置填写 `projectAcceptanceToolbarAction` 或 `projectAcceptanceRowAction`；选择回调填写 `projectAcceptanceSelectionAction` |
| `rules/ProjectAcceptanceRuleCondition.vue` | 在操作条件中选 `PROJECT:CUSTOM_CONDITION`，填写条件参数；需要配套后端判定 |
| `permissions/projectPermissionOptions.js` | 权限配置页按当前实体自动读取“项目复核”候选；不直接赋予用户权限 |

`forms/ProjectMemberChangeSections.vue` 是成员变更表单内部子组件，模型和 API 也是实现依赖；这些文件直接由业务代码导入，不单独编写扩展 JSON。

### 最短体验路径

1. 使用已有测试实体的数值字段，在表单中选择评分字段组件，设置 `passScore=80`。
2. 为相同数据列选择评分单元格，填写相同阈值；保存并预览表单和列表，检查等级与只读模式。
3. 要验收统一数据源、按钮权限和审批，则按仓库 [项目扩展验收说明](../../../../docs/project-extension-acceptance/README.md) 准备后端配置；仅注册组件不会生成业务数据或接口。

### 修改与验证

修改展示和逻辑时编辑这里的实现；修改注册名、参数定义或适用范围时编辑项目清单。新增功能使用新实现和 JSON，无需改本目录的其他文件。

以下命令从 `workflow-web/` 执行：

```sh
node src/extensions/business/project/__tests__/memberChangeModel.spec.js
node src/extensions/business/project/__tests__/memberChangeApi.spec.js
node src/extensions/business/project/__tests__/acceptanceExtensions.spec.js
npm run extensions:check
npm run build
```
