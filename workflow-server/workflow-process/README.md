# workflow-process

流程模块负责流程定义、部署发布、节点配置、办理人解析和流程运行态。

## 包边界

- `com.workflow.process.api`：流程模块对外应用接口和请求适配。
- `com.workflow.process.definition`：流程定义、节点模型和设计期校验。
- `com.workflow.process.deployment`：Flowable 部署适配。
- `com.workflow.process.publish`：发布、版本和回滚。
- `com.workflow.process.runtime`：实例、进度、终止和运行态查询。
- `com.workflow.process.task`：审批、驳回、撤回、重提、加签和知会。
- `com.workflow.process.assignment`：办理人、候选人和多实例解析。
- `com.workflow.process.compatibility`：迁移期间的旧接口适配，不允许新增业务规则。

现有 `com.workflow.controller`、`com.workflow.service`、`com.workflow.mapper` 作为兼容结构逐步迁移。
流程模块不得直接访问其他模块的内部 Service 或 Mapper，应依赖 `workflow-contracts` 端口。

## 跨模块端口

- 对外提供 `ProcessCatalogPort` 和 `ProcessRuntimePort`。
- 通过 `EntityRecordPort` 更新实体记录的流程运行态。
- 通过 `EntityFormRuntimePort` 获取实体表单，不访问实体 Mapper。
- 通过 `IdentityDirectoryPort` 解析用户和用户组，不访问系统模块内部服务。
- 发布资产通过 `MigrationAssetHandler` 登记到迁移模块。
