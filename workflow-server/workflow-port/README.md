# workflow-port

平台跨模块能力契约，Java 包名前缀为 `com.workflow.port`。扩展点由 `workflow-spi` 提供，沿用 `com.workflow.contracts`。

## 能力索引

日常业务调用从 `system`、`entity`、`process` 查起。

| 想做什么 | 去哪个目录 |
| --- | --- |
| 查用户、组织、权限、字典、外部系统、工作日历、公开设置或系统实体 | [system](#系统数据-system) |
| 查业务记录、表单、实体版本，保存草稿或提交数据 | [entity](#业务实体-entity) |
| 查待办或办理历史，审批、回退、撤回、加签 | [process](#流程-process) |
| 迁移配置、导入历史数据、查导入的流程档案 | [migration](#迁移与导入档案-migration) |
| 接入嵌入式表单、列表和流程 | [embed](#嵌入式接入-embed) |
| 读写文件、查事务事件投递状态、记审计、运行启动任务或控制执行预算 | [platform](#平台基础能力-platform) |

优先按业务主表找接口。将表名转成 PascalCase 后加 `Port`，例如 `sys_user` 对应 `SysUserPort`。跨表的流程和动态实体按业务对象命名。展开任意领域包即可直接选中对应接口。

| 典型任务 | Java 方法 |
| --- | --- |
| 查用户、保存来源用户 | `SysUserPort.pageUsers()`、`saveUser()` |
| 按编码查外部系统或读取对接参数 | `SysExternalSystemPort.findByCode()`、`findConfigurationByCode()` |
| 后台同步任务按编码读取已启用外部系统的参数 | `SysExternalSystemPort.findConfigurationForService()` |
| 用 HR 员工或部门 ID 查平台 ID | `SysUserPort.findUserIdBySource()`、`SysOrganizationPort.findOrganizationIdBySource()` |
| 按状态或范围求项目人员投入总额 | `EntityRecordPort.sum()` |
| 按发布工作日历算截止时间 | `WorkCalendarPort.addWorkingMinutes()` |
| 查某流程实例的 SLA 列表和状态数 | `ProcessTaskSlaPort.pageMonitor()`、`countMonitorByStatus()` |
| 登记生成的报告、查文件信息、打开文件流 | `StorageFileObjectPort.upload()`、`getMetadata()`、`open()` |
| 查已提交 ERP 推送的投递状态 | `WorkflowOutboxEventPort.findStatus()` |
| 查业务记录、保存草稿、修改或软删除 | `EntityRecordPort.findById()`、`saveDraft()`、`update()`、`delete()`、`deleteBatch()` |
| 查记录操作权限 | `EntityRecordPort.getRecordCapabilities()` |
| 按业务记录查流程轮次 | `ProcessInstancePort.listRoundsByRecord()` |
| 查系统实体可读字段或批量回显 | `SystemEntityDefinitionPort.describeReadableFields()`、`SystemEntityRecordPort.findByIds()` |
| 查待办及详情、办理或退回 | `ProcessTaskPort.pageTasks()`、`getTaskDetail()`、`complete()`、`returnToHistory()` |
| 跳过运行中的普通待办 | `ProcessTaskPort.skip(new SkipTaskCommand(meta, taskId, reason))`，按当前变量走发布的 BPMN 连线，原因写入流程历史。后台调用可使用 `skip(command, operatorId)` 指定审计操作人 |
| 查实例进度、撤回或终止 | `ProcessInstancePort.getProgress()`、`withdraw()`、`terminate()` |

`find`、`list`、`page`、`get`、`preview` 表示查询或预演，`save`、`update`、`replace`、`complete` 等表示变更。显式加锁使用 `lock`。各方法分别沿用现有业务授权规则。

### 系统数据 `system`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`CurrentActorPort`](src/main/java/com/workflow/port/system/CurrentActorPort.java) | 当前认证身份 | 查询当前操作人、管理员身份和业务权限 | biz、平台 |
| [`ProcessCandidateMemberPort`](src/main/java/com/workflow/port/system/ProcessCandidateMemberPort.java) | 流程候选人 | 展开用户、角色、用户组和组织成员 | 流程候选人解析 |
| [`SysDictPort`](src/main/java/com/workflow/port/system/SysDictPort.java) | `sys_dict`、`sys_dict_item` | 读有效字典树和字典项 | biz、平台 |
| [`SysExternalSystemPort`](src/main/java/com/workflow/port/system/SysExternalSystemPort.java) | `sys_external_system`、`sys_external_system_parameter` | 分页、按 ID 或编码查基本信息、按编码读取对接参数 | biz、平台。按服务凭据查询时校验系统授权 |
| [`SysGlobalSettingPort`](src/main/java/com/workflow/port/system/SysGlobalSettingPort.java) | `sys_global_setting` | 按键读取公开设置 | biz、平台 |
| [`SysGroupPort`](src/main/java/com/workflow/port/system/SysGroupPort.java) | `sys_group` | 按编码或 ID 查用户组 | 平台组目录 |
| [`SysOrganizationPort`](src/main/java/com/workflow/port/system/SysOrganizationPort.java) | `sys_organization` | 查组织、组织树和扩展，查来源 ID 映射，保存来源组织，解析流程所需组织快照 | biz、组织同步、流程选人 |
| [`SysPositionAssignmentPort`](src/main/java/com/workflow/port/system/SysPositionAssignmentPort.java) | `sys_position_assignment` | 查岗位任职、替换成员、按修订号删除任职 | 岗位任职管理 |
| [`SysPositionPort`](src/main/java/com/workflow/port/system/SysPositionPort.java) | `sys_position` | 查可用岗位与有效任职人 | 平台、流程选人 |
| [`SysRolePort`](src/main/java/com/workflow/port/system/SysRolePort.java) | `sys_role` | 查询角色目录 | biz、平台 |
| [`SysUserGroupPort`](src/main/java/com/workflow/port/system/SysUserGroupPort.java) | `sys_user_group` | 查用户所在组、读组成员、替换组成员 | 平台、成员管理 |
| [`SysUserGroupTagPort`](src/main/java/com/workflow/port/system/SysUserGroupTagPort.java) | `sys_group_tag`、`sys_user_group_tag` | 校验组内标签，按标签展开成员 | 流程候选人解析 |
| [`SysUserHandoverPort`](src/main/java/com/workflow/port/system/SysUserHandoverPort.java) | `sys_user` | 查交接涉及的用户，支持事务内锁定 | 流程交接 |
| [`SysUserLookupPort`](src/main/java/com/workflow/port/system/SysUserLookupPort.java) | `sys_user` | 按用户键解析平台身份和可见名称 | 平台身份解析 |
| [`SysUserPort`](src/main/java/com/workflow/port/system/SysUserPort.java) | `sys_user` | 分页查人、批量按 ID 或用户名查人、查来源 ID 映射、保存来源用户及扩展 | biz、身份同步 |
| [`SysUserRolePort`](src/main/java/com/workflow/port/system/SysUserRolePort.java) | `sys_user_role` | 查用户角色、批量替换角色 | biz、平台 |
| [`SystemEntityDefinitionPort`](src/main/java/com/workflow/port/system/SystemEntityDefinitionPort.java) | 系统实体定义 | 查已登记的可读实体、字段和首选显示字段 | biz、平台 |
| [`SystemEntityRecordPort`](src/main/java/com/workflow/port/system/SystemEntityRecordPort.java) | 开放系统实体 | 单查、批量回显、支持排序和字段投影的分页 | biz、平台 |
| [`WorkCalendarPort`](src/main/java/com/workflow/port/system/WorkCalendarPort.java) | `work_calendar` | 查已发布工作日历、按工作分钟计算截止时刻 | biz、平台 |

外部系统参数明细通过 `findConfigurationByCode(systemCode)` 返回，按 `parameters()` 读取。后台任务使用 `findConfigurationForService(systemCode, serviceCode, credential)`，由部署配置 `workflow.external-system.services` 绑定服务编码、BCrypt 凭据摘要和允许的系统编码，凭据从部署密钥读取。该入口只返回仍启用的系统。

### 业务实体 `entity`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`EntityCodeCatalogPort`](src/main/java/com/workflow/port/entity/EntityCodeCatalogPort.java) | — | 列举实体编码并按流程定义反查实体 | 平台、流程绑定 |
| [`EntityDefinitionPort`](src/main/java/com/workflow/port/entity/EntityDefinitionPort.java) | `entity_definition` | 查已发布字段、表单与列表元数据、字典和接口选项 | biz、业务应用 |
| [`EntityFileUploadPermissionPort`](src/main/java/com/workflow/port/entity/EntityFileUploadPermissionPort.java) | — | 检查实体表单的文件上传权限 | 平台上传入口 |
| [`EntityFormDeletionReferencePort`](src/main/java/com/workflow/port/entity/EntityFormDeletionReferencePort.java) | — | 检查表单被哪些配置引用 | 平台表单配置 |
| [`EntityFormPort`](src/main/java/com/workflow/port/entity/EntityFormPort.java) | `entity_form`、`entity_form_published` | 查表单与发布绑定，解析新增记录的发布表单 | 平台表单、流程节点、Embed |
| [`EntityProcessStatePort`](src/main/java/com/workflow/port/entity/EntityProcessStatePort.java) | 实体和流程绑定 | 校验流程结束状态，绑定子流程、同步运行态和活动 | 流程内部 |
| [`EntityRecordPort`](src/main/java/com/workflow/port/entity/EntityRecordPort.java) | 实体动态表 | 查记录和操作能力、条件求和、保存草稿、更新、软删除、发起与重新发起 | biz、业务应用 |
| [`EntityRecordVersionPort`](src/main/java/com/workflow/port/entity/EntityRecordVersionPort.java) | `entity_record_version` | 手工留档、查询版本和快照、分页查子记录差异、恢复预演 | biz、业务应用 |
| [`EntityRelationPort`](src/main/java/com/workflow/port/entity/EntityRelationPort.java) | `entity_relation` | 沿发布关系正向或反向查关联记录 | biz、业务应用 |
| [`EntityTaskSummaryPort`](src/main/java/com/workflow/port/entity/EntityTaskSummaryPort.java) | — | 读取流程待办展示所需的实体摘要 | 流程待办 |
| [`EntityUserFieldPort`](src/main/java/com/workflow/port/entity/EntityUserFieldPort.java) | — | 校验实体人员引用字段并读取用户键 | 流程办理人解析 |
| [`EntityUserPathPort`](src/main/java/com/workflow/port/entity/EntityUserPathPort.java) | — | 读取人员字段路径与候选选项，沿关系读取用户键 | 流程办理人解析 |
| [`TrustedEntityRecordMutationPort`](src/main/java/com/workflow/port/entity/TrustedEntityRecordMutationPort.java) | — | 执行单条或批量实体变更、UPSERT 与唯一性占位校验 | 受控的平台写入和业务扩展 |
| [`UiExtensionCatalogPort`](src/main/java/com/workflow/port/entity/UiExtensionCatalogPort.java) | — | 读取注册的 UI 扩展目录 | 平台 UI 配置 |
| [`UiFormContextPort`](src/main/java/com/workflow/port/entity/UiFormContextPort.java) | — | 解析并校验 UI 扩展调用的运行时表单上下文 | 平台 UI 运行时 |

### 流程 `process`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`EntityBoundProcessPort`](src/main/java/com/workflow/port/process/EntityBoundProcessPort.java) | — | 检查能否重启，启动已完成准备的实体绑定流程 | 实体提交流程编排 |
| [`FlowActionContextPort`](src/main/java/com/workflow/port/process/FlowActionContextPort.java) | — | 动作执行期查实例、任务和实体，读写流程变量 | 流程动作宿主 |
| [`ProcessActionDefinitionPort`](src/main/java/com/workflow/port/process/ProcessActionDefinitionPort.java) | `process_action_definition` | 查可选流程动作、配置可用性与展示名 | 流程设计与发布 |
| [`ProcessActionExecutionPort`](src/main/java/com/workflow/port/process/ProcessActionExecutionPort.java) | `process_action_execution` | 查动作执行记录、重试和登记人工处置 | biz、流程运维 |
| [`ProcessAssigneeIncidentPort`](src/main/java/com/workflow/port/process/ProcessAssigneeIncidentPort.java) | `process_assignee_incident` | 查办理人异常和处置异常 | 办理人事件管理 |
| [`ProcessAssigneeResolverPort`](src/main/java/com/workflow/port/process/ProcessAssigneeResolverPort.java) | — | 检查指定办理人解析器是否已启用 | 流程设计与运行 |
| [`ProcessCcRecordPort`](src/main/java/com/workflow/port/process/ProcessCcRecordPort.java) | `process_cc_record` | 查知会收件箱，标记已读 | biz、业务应用 |
| [`ProcessDefinitionConfigPort`](src/main/java/com/workflow/port/process/ProcessDefinitionConfigPort.java) | `process_definition_config` | 分页查已发布定义、按编码查发布定义、查结束状态要求及锁定绑定状态 | 平台流程目录与配置 |
| [`ProcessHistoryPort`](src/main/java/com/workflow/port/process/ProcessHistoryPort.java) | 任务历史、process_operation_log | 分页查独立任务历史项和操作日志 | biz、业务应用 |
| [`ProcessInstancePort`](src/main/java/com/workflow/port/process/ProcessInstancePort.java) | 流程实例 | 查实例、按业务记录查轮次、查进度和能力，撤回、终止、触发接收节点 | biz、业务应用 |
| [`ProcessNextApprovalPort`](src/main/java/com/workflow/port/process/ProcessNextApprovalPort.java) | 下一审批人 | 预览下一个审批节点，分页查候选人 | biz、业务应用 |
| [`ProcessRecordPermissionPort`](src/main/java/com/workflow/port/process/ProcessRecordPermissionPort.java) | — | 校验当前身份对流程关联实体记录的读取范围 | 平台实体和流程 |
| [`ProcessTaskHandoverPort`](src/main/java/com/workflow/port/process/ProcessTaskHandoverPort.java) | 任务交接 | 分页查可交接任务、按范围交接 | 交接管理 |
| [`ProcessTaskPermissionPort`](src/main/java/com/workflow/port/process/ProcessTaskPermissionPort.java) | — | 查记录的可操作任务、办理身份及能力 | 平台实体和流程 |
| [`ProcessTaskPort`](src/main/java/com/workflow/port/process/ProcessTaskPort.java) | process_task 及任务关联表 | 查待办详情和操作能力，办理、跳过、加签、知会、退回及回退预演 | biz、业务应用 |
| [`ProcessTaskSlaPort`](src/main/java/com/workflow/port/process/ProcessTaskSlaPort.java) | `process_task_sla` | 查单任务 SLA、监控分页及状态统计、确认响应、暂停和恢复计时 | biz、流程运维 |
| [`ProcessTaskSummaryPort`](src/main/java/com/workflow/port/process/ProcessTaskSummaryPort.java) | — | 刷新流程待办业务摘要投影 | 流程和实体同步 |
| [`ProcessVariablePort`](src/main/java/com/workflow/port/process/ProcessVariablePort.java) | — | 读取流程和任务本地变量、提交变量补丁 | biz、授权流程动作 |
| [`RelatedProcessCoordinationPort`](src/main/java/com/workflow/port/process/RelatedProcessCoordinationPort.java) | — | 预览跨实体流程操作影响，提交关联流程联动 | 授权的已发布流程动作 |

### 迁移与导入档案 `migration`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`ConfigMigrationAssetPort`](src/main/java/com/workflow/port/migration/ConfigMigrationAssetPort.java) | `config_migration_asset` | 将配置发布登记为迁移资产 | 平台发布、配置迁移 |
| [`EntityExcelMigrationPort`](src/main/java/com/workflow/port/migration/EntityExcelMigrationPort.java) | — | 导出和预检 Excel 配置，建立迁移作用域，发布与回滚配置 | 配置迁移 |
| [`EntityHistoryImportPort`](src/main/java/com/workflow/port/migration/EntityHistoryImportPort.java) | — | 查询和校验导入结构、源映射及可读字段，创建历史实体记录 | 历史导入、流程归档 |
| [`EntityHistoryWritePolicyPort`](src/main/java/com/workflow/port/migration/EntityHistoryWritePolicyPort.java) | — | 校验历史归档记录是否允许被写入 | 实体、流程宿主 |
| [`ExcelConfigurationPublicationPort`](src/main/java/com/workflow/port/migration/ExcelConfigurationPublicationPort.java) | — | 登记已发布的 Excel 方案和规则 | 配置迁移、Excel 发布 |
| [`ProcessArchiveInstancePort`](src/main/java/com/workflow/port/migration/ProcessArchiveInstancePort.java) | `process_archive_instance` | 查询归档模板、档案列表与详情，保存独立流程档案 | 历史导入、流程归档 |
| [`SysDictMigrationPort`](src/main/java/com/workflow/port/migration/SysDictMigrationPort.java) | — | 应用、停用和刷新迁移字典 | 配置迁移 |

### 嵌入式接入 `embed`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`EmbedFormPermissionPort`](src/main/java/com/workflow/port/embed/EmbedFormPermissionPort.java) | — | 校验原生表单查看或创建权限 | Embed 宿主 |
| [`EmbedLaunchPort`](src/main/java/com/workflow/port/embed/EmbedLaunchPort.java) | `embed_launch` | 为已认证应用签发一次性启动信息 | Embed 开放接入 |
| [`EmbedListDataPort`](src/main/java/com/workflow/port/embed/EmbedListDataPort.java) | — | 读取 Embed 原生列表结构和分页数据 | Embed 宿主 |
| [`EmbedListDependencySnapshotPort`](src/main/java/com/workflow/port/embed/EmbedListDependencySnapshotPort.java) | — | 按已认证会话读取固定版本的列表依赖快照 | Embed 宿主 |
| [`EmbedMappedUserPort`](src/main/java/com/workflow/port/embed/EmbedMappedUserPort.java) | — | 解析映射用户的当前角色与权限快照 | Embed 宿主 |
| [`EmbedProcessRecordPort`](src/main/java/com/workflow/port/embed/EmbedProcessRecordPort.java) | — | 定位流程绑定的实体记录 | Embed 宿主 |
| [`EmbedPublishedFormPort`](src/main/java/com/workflow/port/embed/EmbedPublishedFormPort.java) | — | 定位原生表单及配置摘要，校验发布目标 | Embed 宿主 |
| [`EmbedPublishedListDependencyPort`](src/main/java/com/workflow/port/embed/EmbedPublishedListDependencyPort.java) | — | 读取精确发布列表的关联列表依赖 | Embed 发布 |
| [`EmbedPublishedListPort`](src/main/java/com/workflow/port/embed/EmbedPublishedListPort.java) | — | 定位原生列表，校验列表及关联表单访问 | Embed 宿主 |
| [`EmbedRecordCreatePort`](src/main/java/com/workflow/port/embed/EmbedRecordCreatePort.java) | — | 按已发布 Embed 目标和表单创建实体记录 | Embed 原生表单宿主 |
| [`EmbedRelatedTargetPort`](src/main/java/com/workflow/port/embed/EmbedRelatedTargetPort.java) | — | 解析并校验通过关系打开的原生目标 | Embed 宿主 |
| [`EmbedRequestUserScopePort`](src/main/java/com/workflow/port/embed/EmbedRequestUserScopePort.java) | — | 建立并清理当前 Embed 请求的映射用户上下文 | Embed 宿主 |

### 平台基础能力 `platform`

| Port | 对应主表或对象 | 能力 | 适用调用方 |
| --- | --- | --- | --- |
| [`ExecutionBudgetPort`](src/main/java/com/workflow/port/platform/ExecutionBudgetPort.java) | — | 查询剩余预算并检查取消，绑定取消回调 | 平台传给单次调用内的 Provider |
| [`StorageFileObjectPort`](src/main/java/com/workflow/port/platform/StorageFileObjectPort.java) | `storage_file_object` | 幂等登记上传、读文件元数据、打开流 | biz、平台 |
| [`SystemOperationLogPort`](src/main/java/com/workflow/port/platform/SystemOperationLogPort.java) | `system_operation_log` | 提交跨模块系统操作审计事件 | 平台、受控业务扩展 |
| [`WorkflowBootstrapJobPort`](src/main/java/com/workflow/port/platform/WorkflowBootstrapJobPort.java) | `workflow_bootstrap_job` | 以可恢复的执行记录运行一次性初始化工作 | 平台、接入初始化 |
| [`WorkflowOutboxEventPort`](src/main/java/com/workflow/port/platform/WorkflowOutboxEventPort.java) | `workflow_outbox_event` | 发布幂等事务事件、查询投递状态、重新排队失败事件 | 平台、业务事件生产者 |

## 职责与依赖

- `*Port` 表示平台提供的稳定调用能力，由平台/领域模块实现，其他模块调用。
- Port 的参数、结果及双方共用的基础模型放在本模块，只保留一份定义。
- Port 实现建议命名为 `*Adapter`，实现和 Spring 装配留在宿主，不得以 `Port` 结尾。
- 本模块不得依赖 `workflow-spi`、core、领域实现、Spring、MyBatis、Jackson 或 Servlet。
- `workflow-spi -> workflow-port` 是唯一允许的契约制品依赖方向。
- 普通调用方显式依赖 `workflow-port`；实现或调度 Provider 的模块同时显式依赖两个制品。
- 数据库方言继续由独立 `workflow-database` 维护，不迁入这里。

## 包与模型归属

接口直接声明在 `com.workflow.port.<domain>`，例如 `com.workflow.port.entity.EntityRecordPort`。
模型、上下文和异常先按类型聚合，再按业务拆分。包名使用 `com.workflow.port.<domain>.<role>[.<feature>]`。

| 角色 | 用途 |
| --- | --- |
| 领域根包 | 按主表或业务对象声明能力，同一对象的读写通过方法名区分 |
| model | 请求、命令、结果、描述符和协议枚举，不依赖 Port/SPI |
| context | 调用上下文或生命周期作用域，宿主按明确范围使用 |
| annotation | 元数据声明，拦截、授权、审计实现由宿主提供 |
| error | 边界异常、错误码和错误投影 |

`feature` 表达具体业务能力。任务命令在 `process.model.task`，UI 上下文在 `entity.context.ui`，办理人解析异常在 `process.error.assignment`。领域内复用的类型直接放在对应分类目录中，例如 `process.model` 下的分页、请求标识和回执。注解沿用 `<domain>.<feature>.annotation`。
不另建全局 common/util/dto/vo，也不以 request/response 等角色继续拆分模型目录。
不再使用顶层 action/ui 或 identity.resolver 旧包。

Port 签名涉及的模型及其依赖必须留在本模块；不能因为某个类型名称带 Context 就移入 SPI。
例如 `EntityFormPort` 使用的 `UiRuntimeResolutionContext` 留在这里。
`EntityMutationContext`、`EntityFormRuntimeContext` 是边界数据，仍放所属 model 包。
`UiInvocationContext` 是扩展输入，随 Provider 归入 workflow-spi；`EmbedBoundaryFailure`
是错误投影，保持 error 角色，不强制改成 Port。

## 调用规则

- 系统目录、外部系统、工作日历和文件接口由调用方授权。实体记录查询、提交、版本管理、关系查询和软删，以及流程实例、任务认领、下一审批人查询、变量、SLA、运维操作和历史档案也采用同一规则。HTTP 入口继续按页面和对象授权，流程动作继续按当前发布动作范围授权。
- `findConfigurationByCode` 可能返回凭据，调用方负责授权及安全处理。使用 `findConfigurationForService` 时仍校验服务凭据和系统授权。
- Java 任务查询、审批、转办、回退、加签和人工知会由调用方授权。任务 Port 的能力按发布配置和运行状态计算。用户入口继续检查待办办理身份，权限查询 Port 继续计算当前用户的权限。
- Java 实体能力查询按发布配置和记录状态计算。重新发起仍要求最新实例确实已撤回，并在写入时加锁复核。页面入口额外检查原发起人身份。
- 历史档案 Port 返回归档时的字段，页面入口按当前字段授权投影。
- 流程命令使用执行身份隔离幂等回执。Java 任务办理仍可重放同一身份和载荷的旧回执，用户入口只重放用户权限范围内的回执。实例分页的 `ALL` 范围适用于调用方负责授权的后台查询。
- 更新已有记录时可在 `EntitySubmissionCommand.expectedRevision` 传入详情的 `revision`，过期时抛出 `ENTITY_RECORD_VERSION_CHANGED`。单条和批量删除均设置主记录的逻辑删除标记，批量操作使用同一事务。
- 通过 `EntityRecordPort` 提交时选择与命令意图对应的方法，重试复用同一 `requestId` 和意图。回执按当前登录人隔离，流程启动失败时与实体写入一同回滚。
- `TrustedEntityRecordMutationPort` 的 UPSERT 使用命令的 `recordId` 做精确定位；条件变更将 `findById` 返回的 `revision` 原样传入 `expectedRevision`。
- 关系 Port 查询固定发布路径并保留遍历预算，来源及各跳的数据授权由调用方负责。流程协调等用户范围仍由宿主按逐跳的数据权限计划处理。
- 业务动作调用协调端口时，将当前动作的协调配置发布在 `relatedProcessCoordination` 参数中。预览和执行按当前操作人、动作发布快照和已固定的关系路径校验，写操作随宿主事务写入 Outbox。
- `WorkflowOutboxEventPort` 由 workflow-outbox 负责实际投递与重试，遵循主题、幂等键和外层事务参与规则。

## 验证与兼容性

`ContractModuleBoundaryTest` 检查两个制品无重复类型、单向依赖、六个一级目录、Port 平铺和索引覆盖。
`ContractPackageArchitectureTest` 检查类型目录、纯模型依赖和已退役的契约包。
变更后从干净构建运行测试，例如 `mvn -pl workflow-app -am clean test`。

本次迁包后的完整类名见能力索引。调用方升级时需要同步更新 import 并重新编译。
已发布扩展按版本核对制品摘要，详见 [workflow-spi](../workflow-spi/README.md#发布边界)。
目录与命名空间规则见 [ADR-0011](../../docs/architecture/decisions/0011-platform-port-namespace.md) 和 [ADR-0012](../../docs/architecture/decisions/0012-group-port-supporting-types-by-role.md)。
