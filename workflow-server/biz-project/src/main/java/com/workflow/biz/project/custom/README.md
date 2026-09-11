# Project custom backend extensions

本目录集中放置 `com.workflow.biz.project` 模块提供的后端扩展示例。清单覆盖平台
明确按 Bean 名称、稳定编码、类型或 Spring 集合发现的扩展契约。示例统一使用
`projectCustom`、`PROJECT_CUSTOM_` 或 `PROJECT:` 命名空间，避免与内置实现冲突。

## 契约分包

新建扩展应从所属能力的 canonical `spi` 包导入接口，例如流程动作使用
`com.workflow.contracts.process.action.spi`，实体 UI 数据源使用
`com.workflow.contracts.entity.ui.spi`。跨模块单一能力从对应 `port` 包导入。

本目录中的示例直接实现 canonical `port`/`spi`，不通过旧接口建立间接继承关系。
已钉定的 UI Provider 不得仅为换包而重新编译或改名：其默认制品摘要包含实现 class
字节。需要调整已发布实现时，应新增版本并保留历史版本可执行。

## 可直接从现有目录配置

| 类 | 接口 | 前端/接口入口 | 稳定标识 |
| --- | --- | --- | --- |
| `ProjectCustomFlowActionHandler` | `FlowActionHandler` | 定制开发 -> 扩展管理 -> 流程动作 | Bean `projectCustomFlowActionHandler` |
| `ProjectCustomTypedFlowActionHandler` | `TypedFlowActionHandler` | 定制开发 -> 扩展管理 -> 流程动作 | Bean `projectCustomTypedFlowActionHandler` |
| `ProjectExtensionAcceptanceFlowActionHandler` | `FlowActionHandler` | 项目扩展验收流程的流程/节点/连线动作 | Bean `projectExtensionAcceptanceFlowActionHandler` |
| `ProjectCustomFlowActionTriggerProvider` | `FlowActionTriggerProvider` | 流程动作触发时机 | `PROJECT_CUSTOM_MANUAL_EVENT` |
| `ProjectCustomPersonResolver` | `PersonResolver` | 定制开发 -> 扩展管理 -> 人员接口 | `projectCustomPersonResolver` |
| `ProjectCustomUiDataSourceProvider` | `UiDataSourceProvider` | 接口服务，ENTITY 作用范围/复合上下文 | `PROJECT_CUSTOM_UI_DATA_SOURCE` |
| `ProjectCustomEntityUiDataSourceProvider` | `UiDataSourceProvider` | 接口服务，ENTITY 作用范围/字段数据 | `PROJECT_CUSTOM_UI_ENTITY` |
| `ProjectCustomFormUiDataSourceProvider` | `UiDataSourceProvider` | 接口服务，FORM 作用范围 | `PROJECT_CUSTOM_UI_FORM` |
| `ProjectCustomListUiDataSourceProvider` | `UiDataSourceProvider` | 接口服务，LIST 作用范围 | `PROJECT_CUSTOM_UI_LIST` |
| `ProjectCustomListFieldDataProvider` | `ListFieldDataProvider` | 列表字段高级配置 -> 数据源 | `PROJECT_CUSTOM_FIELD` |
| `ProjectCustomPermissionOptionProvider` | `EntityPermissionOptionProvider` | 实体列表按钮权限选择器 | `entity:{entity}:custom:project-review` |

## 运行时按编码引用

| 类 | 接口 | 稳定标识 | 当前行为 |
| --- | --- | --- | --- |
| `ProjectCustomEntityListDataProvider` | `EntityListDataProvider` | `PROJECT_CUSTOM_LIST_QUERY` | 遵守数据范围计划，第 1 页返回一条不落库的验收记录 |
| `ProjectCustomEntityListSchemaProvider` | `EntityListSchemaProvider` | `PROJECT_CUSTOM_LIST_SCHEMA` | 在 `viewConfig` 增加 `projectCustomSchema` 标记 |
| `ProjectCustomEntityListContextResolver` | `EntityListContextResolver` | `projectCustomRelation` | 不信任客户端参数，返回空可信条件 |
| `ProjectCustomActionRuleConditionProvider` | `EntityActionRuleConditionProvider` | `PROJECT:CUSTOM_CONDITION` | 支持 EQ、NE、IN 的行条件 |
| `ProjectCustomDataPermissionMatchProvider` | `EntityDataPermissionMatchProvider` | `PROJECT:CUSTOM_MATCH` | 按用户 ID 或用户名匹配 |
| `ProjectCustomDataPermissionFilterProvider` | `EntityDataPermissionFilterProvider` | `PROJECT:CUSTOM_FILTER` | 固定编译为 `1=0` |
| `ProjectCustomCcRecipientResolver` | `CcRecipientResolver` | `projectCustomCcRecipient` | 兼容旧知会解析器配置 |
| `ProjectCustomCcNotificationChannel` | `CcNotificationChannel` | `PROJECT_LOG` | 只打印通知元数据 |
| `ProjectCustomFileStorageStrategy` | `FileStorageStrategy` | `PROJECT_LOG_ONLY` | 打印后抛出未实现异常，不伪造成功 |
| `ProjectCustomOutboxEventHandler` | `OutboxEventHandler` | `PROJECT_CUSTOM_OUTBOX` | 只打印事件元数据 |

`PROJECT_LOG` 渠道当前尚未出现在知会前端配置中，内置发布器默认仍使用
`IN_APP`。`PROJECT_CUSTOM_OUTBOX` 需要业务代码通过 `OutboxPublisher` 发布同名
主题。文件存储可用 `file.storage.type=PROJECT_LOG_ONLY` 验证调用，但不能用于
保存真实文件。

## 统一数据源 Provider

统一数据源的作用范围和执行位置是两层配置：

| 配置层 | 可选值 | 作用 |
| --- | --- | --- |
| 接口服务作用范围 | `GLOBAL / ENTITY / FORM / LIST` | 限制服务允许绑定到哪些发布配置 |
| 操作上下文 | `FORM / LIST / ENTITY` | 决定 Provider 可读取的强类型运行上下文 |

Provider 接口只收到已经过平台授权的 `UiInvocationContext`、`DataScopePlan`、
服务配置和调用输入，不会直接收到接口服务定义的 `scopeType`。因此以下
`RECOMMENDED_SCOPE` 是配置建议；真正的 scopeId 匹配、发布版本校验和权限校验
由 `UiDataSourceExecutionAccessService` 在调用 Provider 前完成。

| Provider | 推荐 scopeType | 主要 usage | 示例返回 |
| --- | --- | --- | --- |
| `PROJECT_CUSTOM_UI_DATA_SOURCE` | `ENTITY` | 列表、表单、字段、按钮通用联调 | 列值映射、字段补丁、选项或诊断对象 |
| `PROJECT_CUSTOM_UI_ENTITY` | `ENTITY` | `FIELD_OPTIONS / FIELD_DEFAULT / FIELD_COMPUTE / FIELD_CHANGE / ENTITY_SELECTED / FIELD_BUTTON_CLICK` | 字段选项、`value` 对象、字段事件消息与映射 |
| `PROJECT_CUSTOM_UI_FORM` | `FORM` | `FORM_INIT / AFTER_LOAD / BEFORE_SUBMIT / FIELD_* / SUBFORM_ROWS / FORM_BUTTON_CLICK` | 表单字段补丁、空子表集合或事件消息 |
| `PROJECT_CUSTOM_UI_LIST` | `LIST` | `LIST_QUERY / LIST_COLUMN / LIST_LOAD / LIST_EXPORT / TOOLBAR_BUTTON_CLICK / ROW_BUTTON_CLICK` | 一条不落库的验收记录、记录 ID 到列值映射或事件消息 |

配置时在“系统管理 -> 接口服务”新增服务，类型选择
`REGISTERED_PROVIDER`，再选择上表中的 Provider。FORM 和 LIST 范围的
`scopeId` 必须填写真实表单 ID 或列表配置 ID；ENTITY 范围需要选择实体 ID。
保存接口服务后，还需在对应表单、字段、列表列或按钮事件中绑定，并重新发布
表单或列表。所有示例日志都以“项目统一数据源”开头，可同时按
`providerCode`、`recommendedScope` 和 `usage` 检索。

Provider 的 `getCode() + getVersion()` 必须唯一且不可变；修改实现语义时应提升
`getVersion()`，不要覆盖已被发布配置引用的版本。平台会在发布时固定实现版本和
制品摘要，运行时找不到精确版本或摘要不一致会拒绝执行。实现还依赖模型、规则包等
外部制品时，应覆盖 `getArtifactDigest()`，把这些制品的版本摘要一并纳入固定身份。

表单初始化、加载后处理、提交前处理和字段事件的 `targetField` 默认留空，此时
只记录日志或返回事件消息，不修改表单数据。需要观察回填效果时，应填写当前
实体中真实存在且允许修改的字段编码。

## 已定义但平台暂未消费

以下契约已在后端公开，但当前代码中还没有运行时路由或前端目录。示例仍注册为
Spring Bean，方便后续补入口时直接验证：

| 类 | 接口 | 稳定标识 | 当前行为 |
| --- | --- | --- | --- |
| `ProjectCustomEntityListActionProvider` | `EntityListActionProvider` | `PROJECT_CUSTOM_LIST_ACTION` | 打印动作和 payload 键 |
| `ProjectCustomDataScopePredicateProvider` | `DataScopePredicateProvider` | `PROJECT_CUSTOM_SCOPE` | 固定生成空数据条件 `1 = 0` |

## 不自动注册的替换示例

以下接口在当前应用中只有一个平台实现。示例类没有 `@Component`，避免出现多个
候选 Bean。启用时应在独立配置类中显式替换平台实现，并先关闭或排除原实现。

| 类 | 接口 | 用途 |
| --- | --- | --- |
| `ProjectCustomMigrationAssetHandler` | `MigrationAssetHandler` | 发布时登记配置迁移资产 |
| `ProjectCustomBootstrapJobCoordinator` | `BootstrapJobCoordinator` | 多实例启动任务互斥 |
| `ProjectCustomUiExtensionCatalogAdapter` | `UiExtensionCatalogPort` | 替换 UI 扩展目录读取来源 |

## 范围说明

本清单不包含 MyBatis Mapper、普通 Service 接口，以及
`EntityMutationPort`、`ProcessRuntimePort`、`IdentityDirectoryPort` 等跨模块
架构端口。这些类型用于隔离模块和基础设施，不按编码/类型向业务配置开放；伪造
空实现会改变平台核心语义，不属于本次“可配置后端扩展”。

所有没有真实业务场景的实现都会打印结构化 `log.info`，并返回空结果、默认拒绝
或安全演示数据；不会访问外部网络，也不会写入真实业务数据。
