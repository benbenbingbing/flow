# 关联内容扩展范围

普通实体关系的“子页面”不再提供“关联操作扩展”“展示扩展”或“使用自定义组件”。目标数据由实体关系确定，展示沿用目标表单或列表的 PAGE 自定义配置，新增、编辑和保存继续使用原有能力及权限检查。

普通关系包括 `ENTITY_RELATION`，以及待重新选择实体关系的旧 `REFERENCE_FIELD`、`REVERSE_REFERENCE`、`FIELD_MATCH` 配置。读取这些草稿时清除接口取数、动作接口和关联专属组件覆盖，保存时不再提交 `specialHandling.interfaceService`、`specialHandling.actionServices` 与 `specialHandling.customComponent`。模式统一恢复为 `NONE`，失败策略恢复为 `ERROR`，避免已经没有配置入口的旧隐藏策略继续遮蔽错误。

表单设计器加载旧普通关系后，会将需要清理的记录标记为待保存。即使没有修改其他属性，保存也会把清理结果写入数据库；失败时保留待保存状态，重试成功后恢复为已保存。组件仅剩实例参数、模式已是 `NONE`，或只保留非默认失败策略时也会清理。空的接口/组件编辑占位、仅供回显的默认值及有效独立扩展页不会因此被标记为有修改。列表设计的独立配置弹窗在保存时直接提交清理结果。

旧发布中的普通关系接口和组件覆盖不能继续执行：前端直接走目标页面渲染，不再查找旧 RELATED_CONTENT 组件，也不再要求它补齐版本或摘要；服务端按关系类型限制动作能力及接口绑定。重新发布、历史恢复和导入会清理普通关系的专属组件配置；接口配置仍按现有禁止策略校验，不能重新启用。

独立“扩展页面”使用 `SAME_RECORD` 或 `INTERFACE_SERVICE`，继续支持接口取数、动作接口、字段映射和自定义组件。表单设计中的配置入口统一放到“数据与扩展”；列表设计的独立配置弹窗保留相应步骤。共享扩展接口引擎和映射能力不因普通关系移除入口而删除。

Web 普通关联表单通过 `FormPreviewLinkage` 使用目标表单的 PAGE 组件及实例参数；保存子表单仍调用渲染器校验、提交前预校验和原生表单提交接口，携带发布表单标识与关联上下文。普通关联列表通过 `EntityDataList` 使用目标列表的 PAGE 配置及其操作栏，继续限制关联查询范围与操作权限。

这里只复用已有 PAGE 能力，不扩展它的契约：关联表单的 PAGE 动作槽仍由现有宿主传参决定；PAGE 组件使用现有注册表解析方式，并不继承 RELATED_CONTENT 的版本/摘要钉定机制。移动端仍按现有移动组件注册及标准列表能力渲染，不能据此保证 Web 自定义列表在移动端可用。独立扩展页的 `runtime.dispatch` 继续由 RELATED_CONTENT 组件调用，不注入普通 PAGE 组件。

## 验收

从仓库根目录执行以下命令。模型测试覆盖旧接口/组件/失败策略清理、独立扩展保存回显和无额外修改时的清理落库；运行时测试覆盖普通关系回退目标 PAGE、拒绝接口调用、保存校验及权限、独立扩展页保留调用能力。浏览器测试验证实际配置入口与草稿更新。

```sh
cd workflow-web
node src/shared/__tests__/related-content.spec.js
node --loader ./scripts/test-alias-loader.mjs src/views/form-designer/__tests__/relatedContentDraftModel.spec.js
npm run test:related-content-extensions
npm run test:related-content-extensions:browser
```

本次调整不涉及数据库迁移。
