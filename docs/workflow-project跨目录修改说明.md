# workflow-project 跨目录修改说明

## 1. 约束执行结论

- 项目管理业务资产全部位于 `workflow-server/workflow-project/`。
- 实体、字段、表单、列表、菜单、数据范围、节点审批和状态映射均通过配置实现。
- 项目专用运行时代码集中在 `workflow-project`：F05 系统资产生效处理器，以及 F03/F06 的四个动作处理器和一个跨实体治理服务。
- 实际发布与验收中发现的平台通用缺陷直接在公共模块修复，并补充回归测试。
- 未创建项目专用前端页面、控制器、Repository、Mapper 或物理业务表。

## 2. 平台通用修改

| 范围 | 文件 | 修改内容 | 发现方式与必要性 |
| --- | --- | --- | --- |
| 模块装配 | `workflow-server/pom.xml` | 注册 `workflow-project` 模块和依赖版本 | 否则模块不参与聚合构建 |
| 运行时装配 | `workflow-server/workflow-app/pom.xml` | 引入 `workflow-project` | 否则配置资源和流程动作处理器不进入应用 |
| 动态建表 | `workflow-entity/.../DynamicTableService.java` | 布尔默认值规范为 `0/1`，拒绝非法值 | 真实发布时 MySQL 严格模式拒绝 `TINYINT DEFAULT 'false'` |
| 关系子表 | `workflow-entity/.../EntityRelationRuntimeService.java` | 空字符串日期等值写库前归一为 `null`；子表按实体字段定义还原字段编码 | 关系子表保存和回显实测发现空日期与字段编码不稳定 |
| 字段映射 | `workflow-entity/.../EntityRuntimeRecordMapper.java` | 按已发布字段定义精确还原 snake_case/camelCase 字段编码 | 防止 `requirement_type` 被错误转成 `requirementType` |
| 流程时间 | `workflow-entity/.../EntityDataDynamicService.java` | 发起时同步 `submitted_at`，正常完成时同步 `approved_at`；两者分别与流程起止时间使用同一时间值 | F01/F05 真实记录显示业务审批时间为空或依赖专用动作 |
| 数据范围迁移 | `EntityListScopePolicyMapper.java`、`EntityListScopeBindingMapper.java` | 重复发布前清理已逻辑删除的策略和绑定草稿 | 唯一索引会阻止相同策略编码再次发布 |
| 配置迁移菜单 | `workflow-migration/.../ConfigMigrationImportApplyService.java` | 导航菜单与隐藏权限节点分离；支持 `M/C` 父级；目录不绑定实体；按 `entityCode + listKey` 稳定更新并清理重复项 | 真实菜单验收出现目录无法展开、错误页面和重复入口 |
| 节点表单同步 | `workflow-process/.../ProcessDefinitionNodeSyncService.java` | 支持标准 `flowable:formKey`，并保留已导入的只读配置 | BPMN 发布后节点表单绑定丢失或只读状态被覆盖 |
| 新增表单解析 | `workflow-process/.../EntityFormResolveService.java` | 新建数据优先使用实体默认表单，无默认表单才回退首任务表单 | 新建流程数据错误加载审批表单 |
| BPMN 图形修复 | `workflow-web/src/utils/bpmnLayout.js` | 校验每条 `sequenceFlow` 都有 `BPMNEdge`；缺失时先重建 incoming/outgoing，再生成完整 DI | 用户截图显示节点存在但完全没有连线 |
| 设计与进度页面 | `ProcessDesign.vue`、`ProcessProgress.vue` | 统一使用完整 DI 检查和修复逻辑 | 仅检查 `BPMNShape` 会把“有节点、无连线”误判为完整 |
| 前端依赖与测试 | `workflow-web/package.json`、`package-lock.json`、`bpmnLayout.spec.js` | 引入 `bpmn-moddle` 并把连线修复测试加入 `test:unit` | 保证部分 DI 和缺失 DI 均可稳定修复 |
| 动作目录端口 | `workflow-contracts/.../FlowActionCatalogPort.java`、`workflow-admin/.../FlowActionCatalogService.java` | 增加“已配置、已启用且 Bean 可用”的统一判断 | 迁移分析和发布原先使用不同条件，出现分析通过但发布失败 |
| 迁移依赖分析 | `workflow-migration/.../ConfigMigrationPackageService.java` | 流程动作处理器依赖复用动作目录判断 | R13 首次发布暴露动作目录假阳性 |
| 异步动作上下文 | `FlowActionTriggerEvent.java`、`FlowActionEngineEventListener.java`、`FlowActionExecutionProcessor.java`、`FlowActionExecutor.java` | 保存操作人 ID/用户名；提交后动作执行前恢复用户上下文，结束后清理 | F03 批准动作跨实体查询被匿名数据范围过滤为空 |

上述修改均为平台通用能力修复，不包含项目名称、项目字段或 F01/F05 专用判断。

## 3. 回归测试

| 测试 | 覆盖内容 |
| --- | --- |
| `DynamicTableServiceTest` | 布尔默认值和字符串默认值 DDL |
| `EntityRuntimeRecordMapperTest` | 精确字段编码还原 |
| `EntityDataDynamicServiceSubFormTest` | 空关系值归一、流程提交和批准时间同步 |
| `EntityListScopeMapperSqlTest` | 仅清理 `deleted=1` 的范围草稿 |
| `ConfigMigrationMenuImportSupportTest` | 菜单稳定身份、目录归属、父类型和重复清理 |
| `ProcessDefinitionNodeSyncServiceTest` | `flowable:formKey` 与只读配置保留 |
| `EntityFormResolveServiceTest` | 默认新增表单优先级 |
| `bpmnLayout.spec.js` | 无 DI、部分 DI、完整 DI 和连线数量 |
| `FlowActionDefinitionServiceTest`、`FlowActionCatalogServiceTest` | 动作 Bean 存在但未配置时不可用于迁移发布 |
| `FlowActionExecutionProcessorTest`、`FlowActionEngineEventListenerTest` | 异步动作保存、恢复和清理操作人上下文 |
| `ProjectGovernanceServiceTest` | F03 跨实体校验和初始化、F06 ADD 生效与 REMOVE 阻断 |

## 4. 配置优先实现

当前 R13 全量发布包管理：

| 配置内容 | 数量 |
| --- | ---: |
| 审批流程 | 4 |
| 流程实体 | 4 |
| 普通实体 | 10 |
| 自定义字段 | 287 |
| 表单 | 22 |
| 列表 | 22 |
| 数据范围策略/绑定 | 34/37 |
| 菜单 | 17 |
| 用户任务 | 27 |
| 状态映射 | 48 |
| 流程动作 | 9 |
| 迁移资产 | 18 |

发布包：

`workflow-server/workflow-project/src/main/resources/project-config/packages/project-f01-f06-v2.wfpack`

BPMN 源文件必须位于 `project-config/bpmn/`，不能位于类路径默认 `processes/`。后者会触发 Flowable 启动自动部署，绕过配置迁移的用户、表单和动作引用解析，并可能抢占最新流程版本。

## 5. 项目专用扩展

项目专用代码全部位于：

```text
workflow-server/workflow-project/src/main/java/com/workflow/project/
```

当前包含：

| 代码 | 用途 |
| --- | --- |
| `CreateSystemAssetHandler` | F05 批准后创建系统资产并回写申请 |
| `ValidateProjectInitiationHandler` | F03 启动时调用跨实体立项门禁 |
| `ApplyProjectInitiationHandler` | F03 批准后初始化关系、成员和角色 |
| `ValidateProjectSystemChangeHandler` | F06 启动时校验 ADD/UPDATE/REMOVE |
| `ApplyProjectSystemChangeHandler` | F06 批准后生效项目系统关系 |
| `ProjectGovernanceService` | 集中实现需求比例、系统状态、成员、唯一关系和移除阻断规则 |

保留原因：

1. F05 批准后必须创建 `system_asset` 并回写 `approved_system_id`。
2. F03 批准后需要原子更新两个关系实体，并幂等创建成员、角色目录和角色分配。
3. F06 REMOVE 需要聚合项目角色、需求影响、方案评审、发布和并行变更，无法由单表表单规则表达。
4. 当前平台可以通过配置编排动作处理器，但没有通用的跨实体聚合校验和多实体写入 DSL。

平台后续提供通用跨实体查询、聚合校验和幂等写入动作后，可逐步替换为纯配置。

## 6. 2026-07-28 实际验收

- R11 上传、分析和发布成功，8 个资产无阻断。
- F01 `REQ2026072800005` 完成 7 个任务，最终 `BACKLOG`，提交和批准时间与流程起止时间差值均为 0 微秒。
- F05 `SYSAPP2026072800001` 完成 6 个任务，最终 `APPROVED`。
- F05 自动生成 `SYS2026072800001`，并正确回写系统资产 ID。
- 三条流程动作均为 `SUCCESS`，重试次数均为 0。
- F01 实际渲染 30 条连线，F05 实际渲染 24 条连线。
- 干净构建后重启日志显示流程引擎无项目 BPMN 自动部署资源，最新受管定义未被覆盖。
- R13 `WFP-PROJECT-F01-F06-V2-20260728-R13` 上传、分析和发布成功。
- F03 项目 `PRJ2026072800003` 完成 7 个任务，最终 `APPROVED`。
- F03 将同一需求已有 60% 分配补充 40% 至 100%，初始化 1 条需求关系、1 条系统关系、1 名去重成员和 3 条关键角色。
- F06 ADD 完成 7 个任务，申请最终 `EFFECTIVE`，新项目系统关系为 `ACTIVE`。
- F06 REMOVE 在存在 1 条有效系统级角色时返回 `PROJECT_SYSTEM_REMOVAL_BLOCKED`。
- F03/F06 六条动作均为 `SUCCESS`、重试次数 0，并保留 `operatorId=1`、`operatorName=admin`。
- F03/F06 设计器分别实际渲染 30/28 条连线。
- 端到端证据位于 `docs/project-governance-e2e/project-governance-20260728060232.json`。

## 7. 阶段性边界

- F06 `UPDATE` 和无阻断 `REMOVE` 的完整审批路径仍待专项验收。
- F03 研究咨询无系统关系、条件跳过和驳回路径仍待专项验收。
- F01 合并决策自动写入 `requirement_relation` 尚未闭环。
- F05 上下游选择自动写入 `system_dependency_link` 尚未闭环。
- 多账号角色解析、职责分离、异常分支、数据范围隔离、关系循环和动作失败重试仍需生产推广前专项验收。

## 8. 回退方式

1. 在配置迁移页面回滚或停用 `PROJECT-F01-F06-V2` 发布批次。
2. 从 `workflow-app/pom.xml` 移除 `workflow-project` 依赖。
3. 从父 `pom.xml` 移除模块和依赖管理声明。

回退不物理删除已提交业务历史；业务数据按状态和配置版本保留。
