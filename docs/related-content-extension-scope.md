# 关联内容扩展范围

普通实体关系的“子页面”不再提供“关联操作扩展”。目标数据由实体关系确定，新增与编辑继续使用目标表单或列表的标准能力；“数据与扩展”仍可配置自定义展示组件。

普通关系包括 `ENTITY_RELATION`，以及待重新选择实体关系的旧 `REFERENCE_FIELD`、`REVERSE_REFERENCE`、`FIELD_MATCH` 配置。读取这些草稿时清除接口取数和动作接口，保存时不再提交 `specialHandling.interfaceService` 与 `specialHandling.actionServices`。仅接口模式恢复为 `NONE`，接口与组件并用的 `BOTH` 恢复为 `CUSTOM_COMPONENT`，保留展示组件和失败处理方式。普通关系的旧接口配置不再兼容执行。

表单设计器加载旧普通关系后，会将需要清理的记录标记为待保存。即使没有修改其他属性，保存也会把清理结果写入数据库；失败时保留待保存状态，重试成功后恢复为已保存。空的接口编辑占位、仅供回显的默认值及有效独立扩展页不会因此被标记为有修改。列表设计的独立配置弹窗在保存时直接提交清理结果。

旧发布中的普通关系接口不能继续执行：前端不再提供动作接口调用入口，服务端按关系类型限制动作能力及接口绑定。重新发布、历史恢复和导入继续经过配置校验，不能重新引入普通关系的接口配置。

独立“扩展页面”使用 `SAME_RECORD` 或 `INTERFACE_SERVICE`，继续支持接口取数、动作接口、字段映射和自定义组件。表单设计中的配置入口统一放到“数据与扩展”；列表设计的独立配置弹窗保留相应步骤。共享扩展接口引擎和映射能力不因普通关系移除入口而删除。

## 验收

从仓库根目录执行以下命令。模型测试覆盖旧接口清理、独立扩展保存回显和无额外修改时的清理落库；运行时测试覆盖普通关系拒绝接口调用、独立扩展页保留调用能力。浏览器测试验证实际配置入口与草稿更新。

```sh
cd workflow-web
node src/shared/__tests__/related-content.spec.js
node --loader ./scripts/test-alias-loader.mjs src/views/form-designer/__tests__/relatedContentDraftModel.spec.js
npm run test:related-content-extensions
npm run test:related-content-extensions:browser
```

本次调整不涉及数据库迁移。
