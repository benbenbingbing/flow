# workflow-process

流程模块负责流程定义、部署发布、节点配置、办理人解析和流程运行态。

## 包边界

- `com.workflow.process.definition`：流程定义、节点模型和设计期校验。
- `com.workflow.process.publish`：发布、版本和回滚。
- `com.workflow.process.instance`：实例、进度、终止和运行态查询。
- `com.workflow.process.task`：审批、驳回、撤回、重提、加签和知会。
- `com.workflow.process.assignment`：办理人、候选人和多实例解析。
- `com.workflow.process.action`：流程动作编排、执行和引擎事件适配。
- `com.workflow.process.engine.infrastructure.flowable`：脚本、DMN、REST 等引擎服务任务委托。
- `com.workflow.process.form/configuration/status/cc/sla`：流程表单、节点配置、状态同步、抄送和时限管理。

各能力按需使用 `api`、`application`、`domain`、`infrastructure`。监听器、条件求值和部署实现归入对应能力的 `infrastructure.flowable`；SQL Provider 与 Mapper 分别位于 `infrastructure.persistence.provider/mapper`。
办理人扩展实现在 `assignment.extension`，配置模型在 `assignment.domain`，组织快照与设计期检查服务在 `assignment.application`。
历史 BPMN 引用的 `restServiceTaskDelegate` 和兼容监听器继续使用原 Bean 名称。HTTP 传输由 `workflow-http` 提供，通用 HTTP 模块不依赖 Flowable。
流程模块不得直接访问其他模块的内部 Service 或 Mapper，应依赖 `workflow-contracts` 端口。

## 跨模块端口

- 对外提供 `ProcessCatalogPort` 和 `ProcessRuntimePort`。
- 通过 `EntityRecordPort` 更新实体记录的流程运行态。
- 通过 `EntityFormRuntimePort` 获取实体表单，不访问实体 Mapper。
- 通过 `IdentityDirectoryPort` 解析用户和用户组，不访问系统模块内部服务。
- 发布资产通过 `MigrationAssetPort` 登记到迁移模块。
