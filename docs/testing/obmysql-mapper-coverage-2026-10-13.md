# OceanBase MySQL 生产 Mapper 执行覆盖（2026-10-13）

这是 `OceanBaseMapperDatabaseTest` 在本机 OceanBase CE 4.4.2.1 MySQL 模式独立库上的单次执行快照。统计只取 `StatementHandler.query/update` 真正调用 JDBC 后的记录；测试用 JDBC 事务回滚写入，并清理随机动态表。

本文件与[逐 Mapper TSV](obmysql-mapper-coverage-2026-10-13.tsv)只保存类名、源码路径、语句标识和计数，不保存 SQL、绑定参数、业务记录、密码、令牌或原始异常。完整兼容结论见[实库验证报告](obmysql-compatibility-2026-10-13.md)。

## 统计口径

- 源码共有 119 个生产 `@Mapper` 接口，119 个均至少有一条真实执行成功的 SQL。这里的“覆盖 119 个 Mapper”不等于每个接口的所有方法和动态分支均已成功执行。
- 成功 JDBC 调用 996 次，涉及 694 个不同的 `MappedStatement` 标识；同一标识在不同参数或多次调用下重复计数，不能据此推算已覆盖的 SQL 分支数。
- 通用探测样本约束拒绝 0 次，涉及 0 个不同语句；只有拒绝、没有任何成功记录的语句为 0 个。拒绝次数单独列示，不计为成功写入。
- 下表“成功语句”按每个 Mapper 内的完整语句标识去重；TSV 的 `*_statement_local_ids` 仅省略本行 Mapper 类名前缀，可拼回完整 `MappedStatement` 标识。
- 缓存命中、只构造 SQL、空路径提前返回以及未到达 JDBC 执行阶段的调用均不计入成功次数。

## 验证边界

- 反射冒烟调用生产接口的声明方法，并对继承 `BaseMapper` 的接口补测 `selectById`、`selectList`、`selectCount`；不保证全部继承 CRUD 方法均有执行证据。
- 通用样本多使用不存在的记录 ID、限制结果的权限条件和小分页，很多成功的查询或更新可能返回空集或影响 0 行；它们证明该参数下的绑定及 SQL 执行可用，不代表相应业务写入已完成。
- 合法数据断言另覆盖当前表单发布与覆盖、默认表单关联、组织路径顺序与逻辑删除、动态导出权限和续页游标，以及 12 条依赖应用/用户/授权配置的 INSERT、读回与回滚；SQL 冒烟不替代权限、审批监听器、外部集成或完整业务流程回归。
- 动态 Provider 的全部条件组合、权限规则、NULL/边界值、分页总数、并发事务和失败恢复并未由本计数穷尽。锁、元数据、错误分类和 HTTP 验证见主报告，不能与本表次数相加为业务场景覆盖数。
- 本快照只适用于所列 OceanBase 版本、初始化结构、生产 Mapper 与测试代码；不外推其他数据库版本或迁移全部历史数据库的结果。

## 逐 Mapper 记录

| Mapper | 成功调用 | 成功语句 | 约束拒绝调用 | 仅拒绝语句 |
| --- | ---: | ---: | ---: | ---: |
| [SystemOperationLogMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/audit/infrastructure/persistence/mapper/SystemOperationLogMapper.java) | 4 | 3 | 0 | 0 |
| [AuthRefreshSessionMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/auth/infrastructure/persistence/mapper/AuthRefreshSessionMapper.java) | 7 | 7 | 0 | 0 |
| [LoginThrottleMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/auth/infrastructure/persistence/mapper/LoginThrottleMapper.java) | 4 | 4 | 0 | 0 |
| [SysMenuMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/menu/infrastructure/persistence/mapper/SysMenuMapper.java) | 12 | 6 | 0 | 0 |
| [SysRoleMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/role/infrastructure/persistence/mapper/SysRoleMapper.java) | 6 | 4 | 0 | 0 |
| [SysRoleMenuMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/role/infrastructure/persistence/mapper/SysRoleMenuMapper.java) | 7 | 5 | 0 | 0 |
| [SysDictItemMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/dictionary/infrastructure/persistence/mapper/SysDictItemMapper.java) | 8 | 5 | 0 | 0 |
| [SysDictMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/dictionary/infrastructure/persistence/mapper/SysDictMapper.java) | 4 | 3 | 0 | 0 |
| [FlowActionDefinitionEntityMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/extension/action/infrastructure/persistence/mapper/FlowActionDefinitionEntityMapper.java) | 5 | 5 | 0 | 0 |
| [FlowActionDefinitionMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/extension/action/infrastructure/persistence/mapper/FlowActionDefinitionMapper.java) | 6 | 3 | 0 | 0 |
| [PersonResolverDefinitionMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/extension/person/infrastructure/persistence/mapper/PersonResolverDefinitionMapper.java) | 3 | 3 | 0 | 0 |
| [ExternalSystemMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/externalsystem/infrastructure/persistence/mapper/ExternalSystemMapper.java) | 9 | 6 | 0 | 0 |
| [ExternalSystemParameterMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/externalsystem/infrastructure/persistence/mapper/ExternalSystemParameterMapper.java) | 6 | 5 | 0 | 0 |
| [SysGroupMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/group/infrastructure/persistence/mapper/SysGroupMapper.java) | 10 | 9 | 0 | 0 |
| [SysUserGroupMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/group/infrastructure/persistence/mapper/SysUserGroupMapper.java) | 7 | 6 | 0 | 0 |
| [SysPositionAssignmentBatchMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/position/infrastructure/persistence/mapper/SysPositionAssignmentBatchMapper.java) | 4 | 3 | 0 | 0 |
| [SysPositionAssignmentMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/position/infrastructure/persistence/mapper/SysPositionAssignmentMapper.java) | 19 | 14 | 0 | 0 |
| [SysPositionMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/position/infrastructure/persistence/mapper/SysPositionMapper.java) | 17 | 12 | 0 | 0 |
| [SysUserMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/user/infrastructure/persistence/mapper/SysUserMapper.java) | 19 | 14 | 0 | 0 |
| [SysUserRoleMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/user/infrastructure/persistence/mapper/SysUserRoleMapper.java) | 7 | 5 | 0 | 0 |
| [SysOrganizationMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/organization/infrastructure/persistence/mapper/SysOrganizationMapper.java) | 19 | 8 | 0 | 0 |
| [GlobalSettingMapper](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/setting/infrastructure/persistence/mapper/GlobalSettingMapper.java) | 7 | 5 | 0 | 0 |
| [EmbedIdempotencyMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedIdempotencyMapper.java) | 5 | 4 | 0 | 0 |
| [EmbedLaunchEntryMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedLaunchEntryMapper.java) | 2 | 1 | 0 | 0 |
| [EmbedLaunchPersistenceMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedLaunchPersistenceMapper.java) | 10 | 7 | 0 | 0 |
| [EmbedMaintenanceMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedMaintenanceMapper.java) | 9 | 8 | 0 | 0 |
| [EmbedOperationReceiptMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedOperationReceiptMapper.java) | 4 | 2 | 0 | 0 |
| [EmbedRuntimeReleaseMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedRuntimeReleaseMapper.java) | 3 | 2 | 0 | 0 |
| [EmbedSessionExchangeMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedSessionExchangeMapper.java) | 13 | 12 | 0 | 0 |
| [EmbedSessionPersistenceMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedSessionPersistenceMapper.java) | 15 | 10 | 0 | 0 |
| [EmbedTrafficControlMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/infrastructure/persistence/mapper/EmbedTrafficControlMapper.java) | 8 | 8 | 0 | 0 |
| [EmbedManagementMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/management/infrastructure/persistence/EmbedManagementMapper.java) | 62 | 47 | 0 | 0 |
| [EmbedOperationsMapper](../../workflow-server/workflow-embed/src/main/java/com/workflow/embed/management/infrastructure/persistence/EmbedOperationsMapper.java) | 15 | 9 | 0 | 0 |
| [EntityDataDynamicMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/infrastructure/persistence/mapper/EntityDataDynamicMapper.java) | 43 | 29 | 0 | 0 |
| [EntityFieldFileItemMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/infrastructure/persistence/mapper/EntityFieldFileItemMapper.java) | 5 | 4 | 0 | 0 |
| [EntityFlowStatusMappingMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/infrastructure/persistence/mapper/EntityFlowStatusMappingMapper.java) | 9 | 4 | 0 | 0 |
| [EntityRelationMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/infrastructure/persistence/mapper/EntityRelationMapper.java) | 16 | 8 | 0 | 0 |
| [EntityRelationProjectionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/infrastructure/persistence/mapper/EntityRelationProjectionMapper.java) | 5 | 3 | 0 | 0 |
| [EntityCodeRuleMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityCodeRuleMapper.java) | 7 | 6 | 0 | 0 |
| [EntityDefinitionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityDefinitionMapper.java) | 14 | 8 | 0 | 0 |
| [EntityFieldMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityFieldMapper.java) | 7 | 4 | 0 | 0 |
| [EntityFieldOptionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityFieldOptionMapper.java) | 5 | 4 | 0 | 0 |
| [EntityPublishHistoryMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityPublishHistoryMapper.java) | 11 | 5 | 0 | 0 |
| [EntityStatusMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/infrastructure/persistence/mapper/EntityStatusMapper.java) | 7 | 4 | 0 | 0 |
| [EntityFormMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/infrastructure/persistence/mapper/EntityFormMapper.java) | 9 | 4 | 0 | 0 |
| [EntityFormNodeMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/infrastructure/persistence/mapper/EntityFormNodeMapper.java) | 9 | 6 | 0 | 0 |
| [EntityFormPublishedMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/infrastructure/persistence/mapper/EntityFormPublishedMapper.java) | 13 | 9 | 0 | 0 |
| [EntityFormUniqueClaimMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/uniqueness/infrastructure/persistence/mapper/EntityFormUniqueClaimMapper.java) | 2 | 2 | 0 | 0 |
| [EntityFormUniqueValueGateMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/uniqueness/infrastructure/persistence/mapper/EntityFormUniqueValueGateMapper.java) | 1 | 1 | 0 | 0 |
| [EntityListActionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/list/infrastructure/persistence/mapper/EntityListActionMapper.java) | 6 | 5 | 0 | 0 |
| [EntityListConfigMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/list/infrastructure/persistence/mapper/EntityListConfigMapper.java) | 8 | 4 | 0 | 0 |
| [EntityListFieldMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/list/infrastructure/persistence/mapper/EntityListFieldMapper.java) | 6 | 5 | 0 | 0 |
| [EntityMutationReceiptMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/mutation/infrastructure/persistence/mapper/EntityMutationReceiptMapper.java) | 6 | 5 | 0 | 0 |
| [EntityListScopeAuditLogMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/infrastructure/persistence/mapper/EntityListScopeAuditLogMapper.java) | 3 | 3 | 0 | 0 |
| [EntityListScopeBindingMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/infrastructure/persistence/mapper/EntityListScopeBindingMapper.java) | 5 | 4 | 0 | 0 |
| [EntityListScopeDelegationMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/infrastructure/persistence/mapper/EntityListScopeDelegationMapper.java) | 5 | 4 | 0 | 0 |
| [EntityListScopePolicyMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/infrastructure/persistence/mapper/EntityListScopePolicyMapper.java) | 5 | 4 | 0 | 0 |
| [EntityListScopeReleaseMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/infrastructure/persistence/mapper/EntityListScopeReleaseMapper.java) | 6 | 5 | 0 | 0 |
| [UiComponentTemplateMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiComponentTemplateMapper.java) | 4 | 4 | 0 | 0 |
| [UiComponentTemplateVersionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiComponentTemplateVersionMapper.java) | 4 | 3 | 0 | 0 |
| [UiConfigReleaseAuditMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiConfigReleaseAuditMapper.java) | 3 | 3 | 0 | 0 |
| [UiConfigReleaseMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiConfigReleaseMapper.java) | 24 | 8 | 0 | 0 |
| [UiEventBindingMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiEventBindingMapper.java) | 10 | 7 | 0 | 0 |
| [UiExtensionDefinitionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiExtensionDefinitionMapper.java) | 3 | 3 | 0 | 0 |
| [UiViewCompositionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/infrastructure/persistence/mapper/UiViewCompositionMapper.java) | 8 | 6 | 0 | 0 |
| [EntityRecordVersionCounterMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityRecordVersionCounterMapper.java) | 3 | 3 | 0 | 0 |
| [EntityRecordVersionDatasetMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityRecordVersionDatasetMapper.java) | 5 | 3 | 0 | 0 |
| [EntityRecordVersionDatasetRowMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityRecordVersionDatasetRowMapper.java) | 6 | 3 | 0 | 0 |
| [EntityRecordVersionMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityRecordVersionMapper.java) | 13 | 5 | 0 | 0 |
| [EntityVersionConfigMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityVersionConfigMapper.java) | 10 | 7 | 0 | 0 |
| [EntityVersionRolloutBridgeMapper](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/infrastructure/persistence/mapper/EntityVersionRolloutBridgeMapper.java) | 10 | 8 | 0 | 0 |
| [ConfigAssetBaselineMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigAssetBaselineMapper.java) | 3 | 3 | 0 | 0 |
| [ConfigEnvironmentMappingMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigEnvironmentMappingMapper.java) | 3 | 3 | 0 | 0 |
| [ConfigExportPackageItemMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigExportPackageItemMapper.java) | 3 | 3 | 0 | 0 |
| [ConfigExportPackageMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigExportPackageMapper.java) | 3 | 3 | 0 | 0 |
| [ConfigImportItemMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigImportItemMapper.java) | 4 | 4 | 0 | 0 |
| [ConfigImportPackageMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigImportPackageMapper.java) | 4 | 4 | 0 | 0 |
| [ConfigMigrationAssetDependencyMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigMigrationAssetDependencyMapper.java) | 5 | 4 | 0 | 0 |
| [ConfigMigrationAssetMapper](../../workflow-server/workflow-migration/src/main/java/com/workflow/migration/infrastructure/persistence/mapper/ConfigMigrationAssetMapper.java) | 3 | 3 | 0 | 0 |
| [IntegrationApiRequestLeaseMapper](../../workflow-server/workflow-open-api/src/main/java/com/workflow/openapi/infrastructure/persistence/mapper/IntegrationApiRequestLeaseMapper.java) | 5 | 5 | 0 | 0 |
| [IntegrationApplicationMapper](../../workflow-server/workflow-open-api/src/main/java/com/workflow/openapi/infrastructure/persistence/mapper/IntegrationApplicationMapper.java) | 8 | 5 | 0 | 0 |
| [IntegrationCredentialMapper](../../workflow-server/workflow-open-api/src/main/java/com/workflow/openapi/infrastructure/persistence/mapper/IntegrationCredentialMapper.java) | 8 | 6 | 0 | 0 |
| [IntegrationRateLimitMapper](../../workflow-server/workflow-open-api/src/main/java/com/workflow/openapi/infrastructure/persistence/mapper/IntegrationRateLimitMapper.java) | 3 | 3 | 0 | 0 |
| [OutboxRecordMapper](../../workflow-server/workflow-integration/workflow-outbox/src/main/java/com/workflow/outbox/infrastructure/persistence/mapper/OutboxRecordMapper.java) | 16 | 13 | 0 | 0 |
| [FlowActionExecutionMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/action/infrastructure/persistence/mapper/FlowActionExecutionMapper.java) | 22 | 18 | 0 | 0 |
| [FlowActionMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/action/infrastructure/persistence/mapper/FlowActionMapper.java) | 9 | 4 | 0 | 0 |
| [ProcessOperationLogMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/audit/infrastructure/persistence/mapper/ProcessOperationLogMapper.java) | 3 | 3 | 0 | 0 |
| [ProcessCcRecordMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/cc/infrastructure/persistence/mapper/ProcessCcRecordMapper.java) | 14 | 6 | 0 | 0 |
| [AssigneeConfigMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/configuration/infrastructure/persistence/mapper/AssigneeConfigMapper.java) | 4 | 3 | 0 | 0 |
| [NodeConfigMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/configuration/infrastructure/persistence/mapper/NodeConfigMapper.java) | 6 | 4 | 0 | 0 |
| [ProcessNodeApprovalMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/configuration/infrastructure/persistence/mapper/ProcessNodeApprovalMapper.java) | 6 | 4 | 0 | 0 |
| [ProcessNodeApprovalOptionMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/configuration/infrastructure/persistence/mapper/ProcessNodeApprovalOptionMapper.java) | 5 | 4 | 0 | 0 |
| [ProcessDefinitionConfigMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/definition/infrastructure/persistence/mapper/ProcessDefinitionConfigMapper.java) | 12 | 7 | 0 | 0 |
| [ProcessVersionHistoryMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/definition/infrastructure/persistence/mapper/ProcessVersionHistoryMapper.java) | 9 | 5 | 0 | 0 |
| [FormConfigMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/form/infrastructure/persistence/mapper/FormConfigMapper.java) | 4 | 3 | 0 | 0 |
| [FormFieldConfigMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/form/infrastructure/persistence/mapper/FormFieldConfigMapper.java) | 4 | 3 | 0 | 0 |
| [ProcessNodeFormMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/form/infrastructure/persistence/mapper/ProcessNodeFormMapper.java) | 8 | 4 | 0 | 0 |
| [EntityProcessLinkMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/instance/infrastructure/persistence/mapper/EntityProcessLinkMapper.java) | 13 | 10 | 0 | 0 |
| [StartedProcessPageMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/instance/infrastructure/persistence/mapper/StartedProcessPageMapper.java) | 3 | 2 | 0 | 0 |
| [ProcessUiReleaseBindingMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/publish/infrastructure/persistence/mapper/ProcessUiReleaseBindingMapper.java) | 6 | 4 | 0 | 0 |
| [WorkCalendarBindingMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/calendar/infrastructure/persistence/mapper/WorkCalendarBindingMapper.java) | 7 | 4 | 0 | 0 |
| [WorkCalendarExceptionMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/calendar/infrastructure/persistence/mapper/WorkCalendarExceptionMapper.java) | 6 | 4 | 0 | 0 |
| [WorkCalendarExceptionPeriodMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/calendar/infrastructure/persistence/mapper/WorkCalendarExceptionPeriodMapper.java) | 5 | 4 | 0 | 0 |
| [WorkCalendarMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/calendar/infrastructure/persistence/mapper/WorkCalendarMapper.java) | 8 | 4 | 0 | 0 |
| [WorkCalendarPeriodMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/calendar/infrastructure/persistence/mapper/WorkCalendarPeriodMapper.java) | 5 | 4 | 0 | 0 |
| [TaskSlaEscalationStepMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/policy/infrastructure/persistence/mapper/TaskSlaEscalationStepMapper.java) | 6 | 4 | 0 | 0 |
| [TaskSlaPolicyMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/policy/infrastructure/persistence/mapper/TaskSlaPolicyMapper.java) | 6 | 4 | 0 | 0 |
| [ProcessTaskSlaEventMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/runtime/infrastructure/persistence/mapper/ProcessTaskSlaEventMapper.java) | 16 | 12 | 0 | 0 |
| [ProcessTaskSlaMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/runtime/infrastructure/persistence/mapper/ProcessTaskSlaMapper.java) | 11 | 6 | 0 | 0 |
| [ProcessTaskSlaPauseMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/sla/runtime/infrastructure/persistence/mapper/ProcessTaskSlaPauseMapper.java) | 5 | 4 | 0 | 0 |
| [ProcessStatusSyncMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/status/infrastructure/persistence/mapper/ProcessStatusSyncMapper.java) | 1 | 1 | 0 | 0 |
| [ProcessTaskAddSignMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskAddSignMapper.java) | 8 | 6 | 0 | 0 |
| [ProcessTaskAddSignUserMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskAddSignUserMapper.java) | 8 | 6 | 0 | 0 |
| [ProcessTaskCandidateGroupMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskCandidateGroupMapper.java) | 5 | 4 | 0 | 0 |
| [ProcessTaskCandidateUserMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskCandidateUserMapper.java) | 5 | 4 | 0 | 0 |
| [ProcessTaskMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskMapper.java) | 26 | 18 | 0 | 0 |
| [TaskHandoverMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/TaskHandoverMapper.java) | 4 | 4 | 0 | 0 |
| [TaskInboxMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/TaskInboxMapper.java) | 3 | 3 | 0 | 0 |
| [TaskInboxProjectionMapper](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/TaskInboxProjectionMapper.java) | 13 | 7 | 0 | 0 |

## 依赖对象完整的 INSERT 补测

探索阶段有 12 条 INSERT 的通用样本被完整性约束拒绝；最终测试改为先建立应用、用户和授权依赖，再经生产 Mapper 写入并断言影响 1 行、读回结果及新连接中回滚后无残留。不禁用约束，不把旧拒绝记录并入本次成功计数。

| 语句 | 本次成功次数 | 本次拒绝次数 |
| --- | ---: | ---: |
| `EmbedLaunchPersistenceMapper.insertAssertionReplay` | 1 | 0 |
| `EmbedLaunchPersistenceMapper.insertLaunch` | 1 | 0 |
| `EmbedManagementMapper.insertBinding` | 1 | 0 |
| `EmbedManagementMapper.insertGrant` | 1 | 0 |
| `EmbedManagementMapper.insertOrigins` | 1 | 0 |
| `EmbedManagementMapper.insertProvider` | 1 | 0 |
| `EmbedManagementMapper.insertRelease` | 1 | 0 |
| `EmbedManagementMapper.insertView` | 1 | 0 |
| `EmbedOperationReceiptMapper.insert` | 1 | 0 |
| `EmbedSessionExchangeMapper.insertSession` | 1 | 0 |
| `EmbedTrafficControlMapper.insertRuntimeLease` | 1 | 0 |
| `IntegrationApiRequestLeaseMapper.insert` | 1 | 0 |

## 证据快照

- 原始审计文件：`workflow-server/workflow-app/target/obmysql-mapper-statements.tsv`（构建产物会被后续运行覆盖，仓库保留本次脱敏汇总）。
- 原始审计 SHA-256：`fb96c5b041a694173cdd546ff999fb3ed9a15f95f965a55c5de5edebc5e22a42`。
- 生产 Mapper 回归测试 SHA-256：`50c97268e245cff94b892420d01e83fb8d6af71b3923d4c6a3d5a21357215575`。
- 初始化脚本 SHA-256：`4c86d5ae5c6412c7208bb9f0e90f282f486cbc4753e58aa612a78c1c05a9a1d3`。
- 重跑方式及数据库隔离要求见主报告“可重复执行”一节。
