# wfpack 跨系统 ID 关联核查

> 本文保留修改前的核查结论。后续修复及验证结果见 [实现与验证记录](wfpack-portability-implementation-2026-09-23.md)。

核查日期：2026-09-23。以当前工作区代码为准；用户称为 wpack，项目实际格式及扩展名为 wfpack。

本次只做核查，没有修改业务代码、配置或数据库。没有连接两个真实系统执行导入。验证方式是导出、编解码、导入、发布及运行时调用链审查，已有测试回归，以及调用实际 Java 方法的内存 Mock 探针。

结论：**当前没有完全做到跨系统只按 code/key 关联。** 已确认 5 类当前配置风险，以及 3 类历史/兼容配置的 ID 残留。下表描述的是配置具备相应内容时的行为，不表示每个发布包都会命中。

## 当前配置中已确认的风险

| 序号 | 配置位置 / 字段 | 导出、导入及使用行为 | 不同系统 ID 不同时的影响 |
| --- | --- | --- | --- |
| 1 | 子表单：`forms[].nodes[].propsDocument` 内的 `childFormId`、`childFormReleaseId`；兼容名称 `refFormId/refFormReleaseId`、`publishedFormId/publishedFormReleaseId`，包括 `componentProps.subFormConfig` | 源表单、发布版本 ID 随 JSON 原样保留；导入只重绑节点及字段等已知关联，没有重绑这些引用；节点保存直接按发布版本 ID 查询 | 目标存在同 code 表单但 ID 不同，仍会报子表单引用的发布版本不存在；不保证能因同包包含目标表单而自动解决 |
| 2 | 组件模板：表单节点 `forms[].nodes[].templateId` 及 `templateVersion`；历史列表列/按钮若仍保存模板绑定也适用。当前列表列的“复制模板”入口会清空绑定，不属于此风险 | 未导出 templateCode/key 引用，也没有导入映射；发布时直接按 templateId 找模板，再查对应版本 | 目标同编码模板 ID 不同，会导致初始发布失败；ID 撞到其他合法模板时存在误关联可能 |
| 3 | 数据范围适用对象：`scopeBindings[].matchConfig` 中 `conditions[].targetIds` 或结构化树的 `condition.targetIds`；`scopePolicies[].filterConfig.audience` 同类字段 | 方案自身 policyId 已通过 policyKey 重绑，但 matchConfig/filterConfig 是原样迁移的 JSON；运行时 USER/ROLE/GROUP/DEPT/ORG 仍拿 targetIds 匹配目标库身份 | 原来的用户、角色、组、部门或组织不再命中；若源 ID 在目标库属于其他对象，可能错误命中。USER 支持用户名兼容，但当前指定用户选择器明确保存 ID |
| 4 | 流程动作：`flowActions[].actionDefinitionId` | 保留源 actionDefinitionId；导入映射 interfaceName 后交给动作保存；目录选择优先查 ID，找不到才按 handlerName 查找，且不会核对已命中 ID 的 handlerName 是否等于传入值 | 源 ID 在目标不存在时可按名称回退；**若 ID 撞号，可能换成另一动作处理器**，也可能因那个定义被禁用/不可见而错误阻断 |
| 5 | 固定 ID 条件：按钮 `availabilityRule.visibleWhen/enabledWhen`、数据范围 `filterConfig.root` 内的 `USER_FIELD` 条件，例如 `field=id/deptId/orgId/roleIds` 的 `value` | 规则常量原样保留；在目标环境与当前用户的本地 ID、组织 ID 或角色 ID 比较 | 配置了指定 ID 常量时，按钮显示/启用和数据过滤判断可能改变；用户名、业务编码常量及运行时动态身份变量不属于此类问题 |

### 1. 子表单证据

- 设计器明确序列化这些 ID：[EntityFormDesignByEntity.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:3332)。
- 节点通过 portableMap 导出：[ConfigMigrationAssetService.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:1194)。通用清洗只递归 Map/Collection，对 JSON 字符串原样返回：[sanitizeValue](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:2317)。
- 导入重建节点和 fieldId，但没有子表单引用重写：[applyForms](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:1462)。
- 子表单归一逻辑直接调用 `releaseMapper.selectById(releaseId)`：[EntityFormNodeService.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:950)。
- 内存探针证实源 childFormReleaseId 会留下来，并被作为目标 Mapper 的查询参数；没有该 ID 就抛异常。

### 2. 模板证据

补充核实：当前 wfpack 没有独立的组件模板资产导入。列表列的当前模板入口是复制初始化，`applyListColumnTemplateSnapshot` 会清空 templateId/templateVersion/localOverridesDocument，正常新配置后续不依赖模板。表单节点仍采用锁定模板版本和手动升级的模式，并在发布时检查模板记录；两种路径不能混为一谈。下述列表校验风险仅适用于仍残留模板绑定的历史或其他入口配置。

- 列表列复制后解除绑定：[list-column-template.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/shared/list-column-template.js:108)。
- 表单节点仍锁定版本并支持升级：[EntityFormDesignByEntity.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:4339)。

- `templateId` 不在导出技术字段排除集合：[TECHNICAL_KEYS](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:122)；节点和列表列均通过 portableMap/portableList 导出：[节点导出](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:1194)、[列表列导出](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:1245)。
- 节点、列表列导入没有重新绑定 templateId：[节点导入](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:1494)、[列表列导入](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:2069)。
- 表单节点发布校验按 ID 找模板：[validateTemplateReferences](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java:6433)。
- 列表列/按钮共用的发布校验也按 ID 找模板：[validateTemplateBinding](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java:6545)。
- 内存探针证实 templateId 未清理，并且发布校验查询的就是源模板 ID。

### 3. 数据范围适用对象证据

- 导出只将绑定的 policyId 变成 policyKey：[数据范围导出](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:1289)。
- 导入重绑 policyId 后直接插入 binding，matchConfig 未转换：[applyDataScopes](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:2527)。
- 界面指定用户使用 `value-key="id"`：[EntityDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityDesign.vue:670)。
- USER/ROLE/GROUP/DEPT/ORG 的实际 ID 比较：[PermissionRuleMatcher.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/PermissionRuleMatcher.java:237)。
- 内存探针保持 username=alice 不变，仅将用户 ID 从 source-user-id 改为 target-user-id，匹配由 true 变为 false。

### 4. 流程动作证据

- 导出动作整行经过 portableList，保留 actionDefinitionId：[动作导出](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:2141)。
- 导入只清理动作 id/versionId、替换 processConfigId 并映射 interfaceName：[动作导入](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:2762)。
- 动作保存继续把 actionDefinitionId 传入目录服务：[FlowActionService.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/action/application/FlowActionService.java:98)。
- 目录服务优先 ID，之后才按 handlerName 回退：[FlowActionCatalogService.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-admin/src/main/java/com/workflow/admin/extension/action/application/FlowActionCatalogService.java:152)。
- 内存探针构造“源 ID 在目标属于另一个处理器”的情况，实际返回另一个处理器，且没有查询期望的 handlerName。

### 5. 固定 ID 条件证据

- 数据范围 JSON 原样迁移：[数据范围导入](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:2540)；列表配置除了目标表单、接口扩展、附件等已知引用外，规则值没有身份映射：[列表导入](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:2055)。
- `USER_FIELD` 对原 value 直接比较：[EntityActionRuleEvaluator.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/EntityActionRuleEvaluator.java:174)。
- 用户属性 id/deptId/orgId/roleIds 均取目标环境身份：[readUserField](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/EntityActionRuleEvaluator.java:349)。
- 内存探针对 `USER_FIELD(id EQ source-user-id)` 复现同用户名换 ID 后结果改变。

## 历史及兼容配置中保留的 ID

| 配置 | 未转换字段 | 适用条件和结果 |
| --- | --- | --- |
| 旧多实例人员 | `assigneeConfig/multiInstanceConfig` 中的 `multiInstanceUserIds`、`multiInstanceGroupIds`、`multiInstanceRoleIds`，以及旧混合人员值 | 迁移转换器不遍历这些历史字段；运行时旧解析器仍读取它们。旧配置可能因跨系统 ID 改变而找不到人或错选人；当前统一 candidateUsers/candidateGroups 路径已有转换 |
| 旧节点操作矩阵 | `nodeOperationPolicy.operations.*.targetIds`，例如转办/委派/加签的 FIXED 目标范围 | 迁移不处理该扩展属性；运行时直接与 targetUserIds 比较。仅在旧矩阵仍生效时受影响，新三开关配置出现时旧矩阵被忽略 |
| 知会/抄送规则 | `ccConfig.recipientRules[].values` 中保存的历史用户/角色/组/组织 ID | 迁移不遍历 ccConfig；运行时支持按 ID 查对象。当前设计器主要存 username/code，因此不能说所有新知会配置都有此问题；历史 ID 值及组织 code 缺失回退 ID 的情况仍有风险 |

共同证据：[BPMN 转换只识别人员属性和两个扩展属性](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssignmentSupport.java:74)；[人员字段转换范围](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssignmentSupport.java:162)。

旧多实例：[LegacyMultiInstanceAssignmentParser.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/assignment/application/LegacyMultiInstanceAssignmentParser.java:72)。

旧操作矩阵：[固定目标 ID 比较](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/operation/NodeOperationDecisionService.java:258)；[新三开关优先](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/operation/NodeOperationPolicyParser.java:39)。

知会：[角色/组 ID 或 code 查询](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/cc/application/ProcessCcRuntimeService.java:260)、[组织先按 ID 查询](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/cc/application/ProcessCcRuntimeService.java:308)。

内存探针分别构造上述 3 类配置，确认源 ID 原样保留，而且没有进入迁移引用转换回调。

## 其他需按实际包内容判断的情况

- `flowActions[].paramsJson`、自定义人员解析器 `extraParams`、接口 `implementationConfigDocument`、SQL/表达式、表单默认值、固定筛选值没有通用的“任意业务 ID → code”转换。**其中如果手工写死源环境 ID，不能期待迁移器自动修正。** 是否存在此类具体问题，需要检查实际包和扩展参数契约，不能仅凭字段名确定。
- SLA 的 `resolveSlaUserReference` 保留了“按 username 不存在则 selectById”的兼容分支：[SLA 导入兼容分支](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:621)。但当前正常导出会写 `wf-user://username`，依赖分析也要求目标 username 存在，所以这不是当前正常流程必然会触发的遗漏。
- `resolveFormId` 遇到不带 `wf-form://` 的值原样返回：[表单引用兼容分支](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:3257)。正常新包会生成 entityCode/formKey 引用，主要需要留意历史或自定义包。
- `activeReleaseId`、节点表单 `formReleaseId` 等字段即使可能出现在源快照里，也不能直接认定目标仍依赖源 ID：表单保存保留目标本地的发布状态，流程发布会重新固定目标表单的当前发布版本。本次未把它们列为确定的跨环境误关联。
- BPMN 的 `nodeId`、`elementId`、`referencedNodeId` 是同一份流程图内部坐标，随流程图一起迁移，属于合理保留的 ID。

## 已确认有稳定编码转换的主要链路

实体 entityCode、字段 fieldCode、实体引用 refEntityCode、关系 relationCode/childEntityCode、表单 formKey、列表 listKey、流程 processKey、节点表单 wf-form://entityCode/formKey、表单节点 nodeKey/parentNodeKey、数据范围方案 policyKey、菜单 parentPath、接口扩展 extensionKey/scopeRef、普通流程办理人 username/groupCode/roleCode/orgCode、附件 itemKey，以及关联内容中的目标实体/表单/列表引用，均有相应的稳定标识解析或重建逻辑。

本地查出 code 对应记录后，再把目标本地 ID 写入外键是正常行为。风险是把源 ID 当作目标环境匹配依据，或把源 ID 常量带入运行规则。

## 验证结果与限制

- 已有测试：40 项，0 失败、0 错误，覆盖人员映射、BPMN 表单往返、关联内容、接口引用、表单导入及动作旧字段规范化。测试通过不代表上述遗漏被覆盖。
- 临时 Java 探针：13 个断言通过，调用真实转换/校验方法，持久层均为 Mock；复现了子表单 ID 查询、模板 ID 查询、动作 ID 撞号优先、用户 ID 变更导致权限失配、固定条件失配及 3 类历史字段跳过转换。
- 只读扫描仓库自带的 3 份 project-f01-f0x 示例 wfpack 的 assets JSON，未命中本报告扫描的非空风险字段；这不代表其他发布包没有问题，也不包括对这些示例 BPMN 的全面验证。
- 尚未执行真实双系统导入；没有用户指定的实际发布包，因此本报告不声称某个线上/开发包已发生误关联。
- 业务代码修改：无。迁移 SQL 文件新增、修改、删除：均无。

