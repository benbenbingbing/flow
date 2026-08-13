# 后端扩展契约魔法值盘点

> 盘点日期：2026-08-12  
> 盘点范围：`workflow-contracts` 及其在 `workflow-entity`、`workflow-process`、
> `workflow-admin`、`workflow-project`、`workflow-devtools`、
> `workflow-integration`、`workflow-open-api` 中的扩展契约消费者  
> 本阶段交付：仅文档，不修改代码

## 1. 结论摘要

本次盘点不建议对后端字符串做全局清扫，也不建议把所有字符串值改成枚举。
当前代码中的字符串大致分为四类：

| 类型 | 示例 | 结论 |
| --- | --- | --- |
| 平台固定字段名 | `stage`、`message`、`details`、`pageNum`、`pageSize` | 多处共享时可收口 |
| 平台标准值，但允许扩展 | UI `usage`、流程动作自定义触发时机 | 保持 `String`，只为已知标准值提供常量 |
| Provider 自有配置字段 | `recordIdPath`、`writeBack`、`userKeys` | 在实现附近收口，不上升为全局契约 |
| 业务动态数据 | 实体字段、流程变量、过滤条件、patch、connector payload | 保持 Map 和动态 key |

建议首批只处理以下低风险项目：

1. 收口流程动作执行轨迹的 `stage`、`message`、`details` 字段。
2. 为 UI 数据源的已知标准 `usage` 提供字符串常量，不改为枚举。
3. 收口列表 Provider 查询 Map 的 `pageNum`、`pageSize`、`filters` 字段。
4. 在各扩展实现内部收口 Schema 与执行阶段重复使用的配置 key。
5. 生产型流程动作在参数形状固定时复用现有 `TypedFlowActionHandler`。

以下事项不应在首批贸然修改：

- 删除或合并 `FlowActionContext.customParams` 与 `extraParams`。
- 把开放的 `usage`、`operation`、`scene`、Provider code 改成封闭枚举。
- 为所有 Map 建立通用 Schema 框架或批量增加泛型 SPI。
- 将业务实体字段、流程变量或 connector payload 提取为平台常量。
- 把不同领域中碰巧同名的值合并，例如实体写入模式与流程状态事件中的
  `PROCESS_END`。

## 2. 判定原则

### 2.1 建议收口

满足以下条件之一时，才建议治理：

- 同一字段名在 Schema 声明、校验和执行代码中重复填写。
- 同一平台字段由两个及以上模块生产和消费。
- 拼写错误不会立即失败，而会形成静默兼容问题。
- 已有类型化能力可以直接复用，不需要新建基础框架。
- 修改后可以保持现有 API、JSON、数据库配置和旧扩展实现兼容。

### 2.2 保持原样

以下情况保持现状：

- 值的完整取值范围无法确定。
- 项目、插件或第三方实现允许增加新值。
- key 来自实体定义、流程变量、用户配置或外部系统。
- 字符串只出现一次，且所在方法已经清楚表达其含义。
- Map 本身就是契约要求的开放业务载荷。

### 2.3 常量与枚举的边界

- 字段名固定、字段值开放：只收口字段 key。
- 平台已有标准值、但未来允许扩展：提供字符串常量，不使用枚举入参。
- 只有平台完全拥有且取值封闭的值才使用枚举。
- JSON Schema 的 `type`、`properties`、`required`、`title`、`default`
  属于通用格式词，不提取为项目全局常量。

## 3. 候选项总表

| 编号 | 候选项 | 当前用途与重复位置 | 值是否封闭 | 兼容风险 | 建议 | 首批 |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | 动作轨迹 `stage/message/details` | `FlowActionContext` 写入，`FlowActionExecutionService` 读取 | key 封闭，value 开放 | 低 | 契约包字段常量或只读轨迹对象 | 是 |
| A2 | `customParams/extraParams` | 上下文双字段、组装双写、消费者混用 | 不适用 | 高 | 先确定规范字段并保留别名 | 否 |
| A3 | 固定形状流程动作参数 | Schema 与执行代码重复 key | key 固定，value 多数开放 | 中低 | 复用 `TypedFlowActionHandler` | 部分 |
| A4 | 自定义触发时机 | 标准枚举与自定义 Provider 并存 | 不封闭 | 高 | 保持 `String` | 否 |
| P1 | 人员解析器 `userKeys` 等 | Schema 与解析逻辑重复 key | key 固定，用户值开放 | 低 | 实现内常量或参数对象 | 是 |
| U1 | UI 标准 `usage` | entity 模块多个服务和 project Provider 重复 | 标准集合可列举，但扩展边界开放 | 中 | 字符串常量类，接口仍用 `String` | 是 |
| U2 | UI Provider 固定输入字段 | `records/filters/field/value` 等跨调用链使用 | 部分固定 | 中 | 按调用场景分组，不建全局万能 Keys | 部分 |
| U3 | UI Provider 标准结果字段 | 选项、字段值、事件消息结构 | 部分固定 | 中高 | 先补契约测试，再决定 DTO | 否 |
| U4 | Provider 自有 configuration key | Schema 与执行分支重复 | key 固定，value 开放 | 低 | 各 Provider 内部私有常量 | 是 |
| L1 | 列表查询 `pageNum/pageSize/filters` | 平台组装，Provider 读取 | key 封闭 | 低 | 契约包字段常量，保留 Map SPI | 是 |
| L2 | 分页结果兼容别名 | `records/list/rows`、`pageNum/current`、`pageSize/size` | 兼容集合当前固定 | 中 | 保留在 normalizer 内部 | 否 |
| M1 | 实体变更 Provider configuration key | Schema 与实现读取重复 | Provider 内固定 | 低 | 实现内私有常量 | 是 |
| M2 | `EntityMutationSystemFields` | 已有常量且主要消费者已复用 | 封闭 | 低 | 保持现状 | 否 |
| M3 | 其他领域中的 `PROCESS_END` | 流程状态事件使用同名值 | 各领域独立 | 高 | 不与实体写入模式合并 | 否 |
| O1 | `INTERNAL_OUTCOME_VARIABLES` | process/open-api 多模块共享 | key 封闭 | 低 | 已正确收口，保持现状 | 否 |
| D1 | 动态业务 Map | 实体数据、流程变量、patch、filter、payload | 不封闭 | 高 | 保持原样 | 否 |

## 4. 详细盘点

### 4.1 `FlowActionContext`

#### A1. 执行轨迹固定字段

**当前契约与用途**

`FlowActionContext.executionTrace` 使用 `List<Map<String, Object>>` 保存处理器追加的
执行步骤。`addExecutionTrace` 固定写入：

- `stage`
- `message`
- `details`

**位置**

- `workflow-contracts/.../action/FlowActionContext.java:116-119`
- `workflow-contracts/.../action/FlowActionContext.java:249-273`
- `workflow-process/.../action/application/FlowActionExecutionService.java:175-182`

**重复情况**

写入方在 `FlowActionContext` 中写死三个 key；持久化方在
`FlowActionExecutionService` 中再次写死同样的三个 key，并提供默认值。
这是明确的跨模块固定协议。

**是否封闭**

- 字段名封闭。
- `stage` 和 `message` 的值不封闭。
- `details` 是开放对象。

因此只能收口字段名，不能枚举 `stage` 的业务值，也不能限制 `details` 的结构。

**建议**

首批可采用以下任一低风险方式，优先选择改动更小者：

1. 在 action 契约包增加轨迹字段常量，由生产者和消费者共同引用。
2. 增加只读轨迹 record，但继续将现有 Map 作为兼容序列化外形。

不建议首批直接把 `executionTrace` 字段类型改为 `List<FlowActionTraceEntry>`，
因为这会影响 Lombok 访问器、现有测试和执行日志序列化。

**首批结论：建议进入。**

#### A2. `customParams` 与 `extraParams` 重复语义

**当前契约与用途**

`FlowActionContext` 同时包含：

- `customParams`：注释为前端 `paramsJson` 解析后的业务参数。
- `extraParams`：注释为新契约统一命名，并明确与 `customParams` 双写。

**位置**

- `workflow-contracts/.../action/FlowActionContext.java:101-109`
- `workflow-process/.../action/application/FlowActionExecutor.java:165-168`

`FlowActionExecutor` 当前把同一个 Map 同时写入两个字段。消费者仍然混用：

- `FlowActionExecutionService` 持久化时读取 `customParams`。
- 新的 project 动作多读取 `extraParams`。
- `SendNotificationHandler`、devtools 示例仍读取 `customParams`。
- `CreateSystemAssetHandler` 和 `ProjectEntityMutationExecutor` 会合并读取两个字段。

**兼容风险**

高。删除任一字段都会影响已有 Handler、日志记录和外部扩展实现；简单合并读取还可能
改变 key 冲突时的覆盖顺序。

**建议**

- 文档层面明确 `extraParams` 是新代码的规范入口。
- `customParams` 作为兼容别名保留。
- 后续若治理，应先增加兼容测试，覆盖双字段 setter、序列化、持久化和手工构造上下文。
- 不在首批删除字段，不批量替换所有调用。

**首批结论：暂缓。**

### 4.2 流程动作参数

#### A3. 适合复用 `TypedFlowActionHandler` 的实现

现有 `TypedFlowActionHandler<T>` 已经通过
`FlowActionContext.convertExtraParams(Class<T>)` 和运行时 `ObjectMapper` 完成
Map 到 Java 类型的转换，无需新建通用 Schema 或泛型 Provider 框架。

**已有正确示例**

- `workflow-project/.../custom/ProjectCustomTypedFlowActionHandler.java:22-90`
- `workflow-devtools/.../action/DemoTypedActionHandler.java`

这些实现已经避免在执行阶段用字符串读取参数。它们的 `extraParamSchema()` 仍需显式
描述前端配置 Schema，但无需为了消除 Schema 标准词再建设生成框架。

**建议评估迁移的生产型 Handler**

| Handler | 重复 key | 建议 | 原因 |
| --- | --- | --- | --- |
| `ProjectLifecycleAuditHandler` | `auditCode`、`businessStage` | 类型化参数 | 两项均在 Schema、required、读取和结果中重复 |
| `RecordProjectMemberDecisionHandler` | `decision` | 类型化参数或局部常量 | 参数结构固定且只有一个字段 |
| `ProjectExtensionAcceptanceFlowActionHandler` | `stage`、`visibleMessage`、`writeBack` | 类型化参数 | 参数结构固定，默认值明确 |

**不建议迁移或需保留原样**

- `ProjectCustomFlowActionHandler` 是“无类型参数”示例，保留 Map 形态有教学价值；
  只需在本类内部收口 `scenario`、`message`。
- `DemoSimpleActionHandler` 和 `DemoFailingActionHandler` 是 devtools 示例，不应作为
  生产治理优先项。
- `SendNotificationHandler` 当前直接读取 `templateCode`、`notifyType`，但没有在本次
  盘点范围内发现对应 Schema 声明；应先确认通知参数契约再决定是否类型化。

**值的边界**

`auditCode`、`businessStage`、`decision`、`stage` 的可能值来自具体业务配置，
当前无法确认完整集合，均保持字符串，不创建枚举。

**首批结论：只迁移契约固定、测试充分的生产型 Handler；示例与通知 Handler 不动。**

#### A4. 流程动作触发时机

平台内置时机已经由 `FlowActionTriggerTiming` 枚举管理，但扩展契约
`FlowActionHandler.supportedTriggerTimings()` 返回 `Set<String>`，并且
`FlowActionTriggerProvider` 可以提供自定义时机，例如
`PROJECT_CUSTOM_MANUAL_EVENT`。

**位置**

- `workflow-contracts/.../action/FlowActionTriggerTiming.java:12-92`
- `workflow-contracts/.../action/FlowActionHandler.java:21-25`
- `workflow-project/.../custom/ProjectCustomFlowActionTriggerProvider.java:23-42`

**建议**

- 保持 SPI 中的 `String`。
- 内置实现继续使用 `FlowActionTriggerTiming.name()`。
- 自定义 Provider 自己持有稳定常量。
- 不把全部触发时机收紧为平台枚举，否则会破坏扩展能力。

**首批结论：保持原样。**

### 4.3 人员解析器

#### P1. `userKeys` 和回退配置

`ProjectCustomPersonResolver` 在 `PersonResolverDescriptor.extraParamSchema` 中声明：

- `userKeys`
- `fallbackToInitiator`

执行时再次使用相同字符串读取 `request.extraParams()`。

**位置**

- `workflow-project/.../custom/ProjectCustomPersonResolver.java:34-53`
- `workflow-project/.../custom/ProjectCustomPersonResolver.java:60-78`
- `workflow-contracts/.../identity/resolver/PersonResolveRequest.java:11-38`

旧知会兼容解析器还使用：

- `userKeys`
- `fallbackToOperator`

位置：`workflow-project/.../custom/ProjectCustomCcRecipientResolver.java:35-57`。

**是否封闭**

- 字段名由各解析器拥有，可视为固定。
- `userKeys` 的具体用户 ID 或用户名完全开放。
- 回退项是布尔值，不需要枚举。

**建议**

- `ProjectCustomPersonResolver` 在本类内部定义私有字段常量，供 Schema 和读取共用。
- `ProjectCustomCcRecipientResolver` 作为旧兼容实现，字段常量留在本类，不与新解析器
  强行共享 `fallback` 字段。
- 不为 `userKeys` 的值建立枚举或平台目录常量。
- 当前只有少量实现，不建议为所有 `PersonResolver` 新增泛型 SPI。

**首批结论：建议进入，限实现内部收口。**

### 4.4 UI 数据源 Provider

#### U1. 标准 `usage`

当前标准 usage 在多个服务中重复维护：

- `UiDataSourceService.USAGES`
- `UiAvailableOperationService.READ_BINDINGS/WRITE_BINDINGS`
- `UiEventBindingService.EVENTS`
- `UiEventRuntimeService.WRITE_EVENTS`
- 表单、列表相关服务的校验集合
- project 自定义 Provider 的 switch 和事件集合

**代表位置**

- `workflow-entity/.../ui/application/UiDataSourceService.java:64-78`
- `workflow-entity/.../ui/application/UiAvailableOperationService.java:33-43`
- `workflow-entity/.../ui/application/UiEventBindingService.java:49-65`
- `workflow-entity/.../ui/application/UiEventRuntimeService.java:41-45`
- `workflow-project/.../custom/ProjectCustomUiDataSourceProvider.java:100-136`

**是否封闭**

当前平台有明确的标准集合，但 Provider SPI 接收的是
`UiInvocationContext.usage()` 字符串，项目实现对未知 usage 也保留诊断分支。
未来扩展边界尚未完全关闭，因此不应改成只接受枚举。

**建议**

- 在 UI 契约包提供 `UiDataSourceUsages` 字符串常量。
- 各服务仍根据自身职责组合 READ、WRITE、EVENT 等集合，不创建一个承担全部策略的
  万能枚举。
- `UiInvocationContext.usage()` 保持返回 `String`。
- 未知或项目自定义 usage 继续允许按现有扩展策略处理。

**首批结论：建议进入，只增加标准字符串常量并替换明确的标准字面量。**

#### U2. 固定输入字段

UI Provider 的输入 Map 中存在以下常见字段：

- 列表：`records`、`filters`、`pageNum`、`pageSize`、`sorts`、
  `currentRow`、`selectedRows`。
- 字段：`field`、`fieldCode`、`value`。
- 通用配置：`operation`。

**代表位置**

- `workflow-entity/.../list/application/EntityListRuntimeService.java:296-320`
- `workflow-project/.../custom/ProjectCustomUiDataSourceProviderSupport.java:81-95`
- `workflow-project/.../custom/ProjectCustomUiDataSourceProviderSupport.java:198-217`
- `workflow-project/.../custom/ProjectCustomEntityUiDataSourceProvider.java:87-124`
- `workflow-project/.../custom/ProjectCustomListUiDataSourceProvider.java:95-128`

**建议**

- 只对明确属于同一调用场景的字段分组，例如列表查询输入字段。
- 不创建 `MapKeys`、`CommonFields` 之类跨领域全局常量类。
- `field` 对象、`record` 对象和实体数据内部字段继续保持动态。
- `recordValue(record, key)` 的 `key` 是调用方业务选择，不应枚举。

**首批结论：列表查询字段可进入；其他输入字段先保持。**

#### U3. 标准输出结构

项目示例中存在若干稳定外形：

- 字段值：`{"value": ...}`
- 选项：`{"label": ..., "value": ...}`
- 事件消息：`{"message": ..., "data": ...}`
- 字段事件 effects：`type/data/mappings/targetPath/overwrite`

**位置**

- `workflow-project/.../custom/ProjectCustomUiDataSourceProviderSupport.java:236-310`
- `workflow-project/.../custom/ProjectCustomEntityUiDataSourceProvider.java:146-188`

这些结构可能同时受到前端运行时、接口服务 output Schema 和历史配置影响。
仅凭当前示例不足以证明应统一为后端 DTO。

**建议**

- 先补输出 JSON 契约测试和前端消费者清单。
- 在证据完整前保持 Map。
- 若后续引入 DTO，必须保证序列化字段完全不变，并允许 `data` 等开放内容。

**首批结论：暂缓。**

#### U4. Provider 自有 configuration key

project 示例 Provider 在 `configurationSchema()` 与执行代码中重复使用各自配置 key：

- 通用 Provider：`scene`、`message`、`valuePrefix`、`targetField`、
  `defaultValue`。
- ENTITY Provider：`optionLabelPrefix`、`defaultValue`、
  `computedPrefix`、`targetField`。
- FORM Provider：`messagePrefix`、`targetField`、`defaultValue`、
  `optionLabelPrefix`。
- LIST Provider：`columnPrefix`、`messagePrefix`、`pageNum`、`pageSize`。

**位置**

- `workflow-project/.../custom/ProjectCustomUiDataSourceProvider.java:49-71, 80-136`
- `workflow-project/.../custom/ProjectCustomEntityUiDataSourceProvider.java:45-65, 87-159`
- `workflow-project/.../custom/ProjectCustomFormUiDataSourceProvider.java:54-74, 95-174`
- `workflow-project/.../custom/ProjectCustomListUiDataSourceProvider.java:55-75, 95-169`

**建议**

- 每个 Provider 在本类内部定义私有 key 常量。
- 含义相同但所有权不同的 key 不强行共享。例如多个 Provider 都有
  `defaultValue`，其默认语义和作用范围不同。
- 配置值继续保持字符串、数字或布尔值，不为业务前缀和目标字段枚举。

**首批结论：建议进入。**

### 4.5 列表 Provider

#### L1. 标准查询字段

`EntityListRuntimeService` 为 `EntityListDataProvider` 组装：

- `pageNum`
- `pageSize`
- `filters`

示例 Provider 再通过同名字符串读取。

**位置**

- `workflow-contracts/.../entity/list/EntityListDataProvider.java:24-35`
- `workflow-entity/.../list/application/EntityListRuntimeService.java:326-351`
- `workflow-project/.../custom/ProjectCustomEntityListDataProvider.java:42-54`

**是否封闭**

- 三个字段名属于平台调用约定，可视为固定。
- `filters` 内部字段由实体和查询配置决定，不封闭。

**建议**

- 在 entity/list 契约包提供小型查询字段常量。
- 保持 `EntityListDataProvider.query(..., Map<String,Object> query)` 不变，避免破坏
  已有 Provider。
- 不把 filters 转换为固定 DTO。
- 若后续发现 Provider 数量增加，再评估新增只读查询访问器；首批不需要。

**首批结论：建议进入。**

#### L2. 分页结果兼容别名

`EntityListPageResultNormalizer` 当前兼容：

- 数据集合：`records`、`list`、`rows`
- 页码：`pageNum`、`current`
- 页大小：`pageSize`、`size`
- 总数：`total`

**位置**

- `workflow-entity/.../list/application/EntityListPageResultNormalizer.java:45-73`

这些别名集中在一个 normalizer 内，没有在多个模块散落。它们属于兼容输入，而不是
新 Provider 应继续生产的并列规范。

**建议**

- 保持当前集中解析。
- 文档中明确新实现优先返回 `PageResult`，Map 结果使用
  `records/total/pageNum/pageSize`。
- 不把兼容别名暴露到公共契约常量，避免新代码继续依赖旧别名。

**首批结论：保持原样。**

### 4.6 实体变更 Provider

#### M1. configuration 中的重复 key

`ProjectCustomMutationStepProvider` Schema 声明 `scene`、`message`，当前执行代码只
记录 configuration keys，没有直接读取字段值。

`ProjectCustomChangeTargetResolver` Schema 声明并执行时读取：

- `recordIdPath`
- `useSourceRecordId`

**位置**

- `workflow-project/.../custom/ProjectCustomMutationStepProvider.java:45-102`
- `workflow-project/.../custom/ProjectCustomChangeTargetResolver.java:39-88`
- `workflow-contracts/.../entity/mutation/EntityMutationStepContext.java:8-13`
- `workflow-contracts/.../entity/mutation/EntityChangeTargetContext.java:8-14`

**建议**

- `recordIdPath` 和 `useSourceRecordId` 在 resolver 内定义私有常量。
- `scene`、`message` 若仍不被业务逻辑读取，可保持 Schema 原样；不要为了形式统一
  增加没有消费者的参数类型。
- configuration 值来自 Provider 自有业务，不上升为平台枚举。
- 不为所有 mutation Provider 增加泛型 SPI。

**首批结论：只收口真实重复读取的 key。**

#### M2. `EntityMutationSystemFields`

已有契约类定义：

- `_entityMutationMode`
- `PROCESS_END`
- `CURRENT_TASK`

主要消费者已经使用该常量：

- `EntityRecordMutationAdapter`
- `EntityAggregateWriter`

**位置**

- `workflow-contracts/.../entity/mutation/EntityMutationSystemFields.java:6-16`
- `workflow-entity/.../data/infrastructure/adapter/EntityRecordMutationAdapter.java`
- `workflow-entity/.../data/application/EntityAggregateWriter.java`

**结论**

这是已经完成收口的正确案例，不需要再次包装或改成新的通用枚举。

**首批结论：保持原样。**

#### M3. 其他领域中的 `PROCESS_END`

`workflow-process` 的状态同步发布器和 payload 也使用 `PROCESS_END`，但这里表达的是
流程状态同步事件类型，不是实体 mutation Map 中 `_entityMutationMode` 的字段值。

**代表位置**

- `workflow-process/.../status/application/ProcessStatusSyncPublisher.java`
- `workflow-process/.../status/application/ProcessStatusSyncPayload.java`
- `workflow-process/.../status/application/ProcessStatusSyncOutboxHandler.java`

**建议**

- 不因字符串相同就引用 `EntityMutationSystemFields.PROCESS_END`。
- 如果状态同步领域自身存在重复，可在该领域单独定义事件类型常量。
- 不建立跨领域的 `PROCESS_END` 全局常量。

**首批结论：不纳入本次契约治理。**

### 4.7 流程开放事件

#### O1. `OpenProcessEvent.INTERNAL_OUTCOME_VARIABLES`

`OpenProcessEvent` 已将内部 attributes key
`__flow_outcome_variables` 定义为公共契约常量。生产、转换、过滤和测试均引用该常量，
未发现其他代码重复写原始字面量。

**位置**

- `workflow-contracts/.../process/open/OpenProcessEvent.java:20-22`
- `workflow-process/.../open/application/OpenIntegrationProcessEventListener.java:185-188`
- `workflow-open-api/.../application/OpenProcessScenarioSupport.java:182-185`
- `workflow-open-api/.../webhook/application/WebhookDomainEventPublisher.java:114-121`
- `workflow-open-api/.../webhook/application/WebhookEventMaterializer.java:154-157`

**结论**

当前实现已经符合“公共固定 key 由契约所有者定义”的原则。attributes 中其余事件属性
仍可能按事件类型扩展，不应建立封闭字段枚举。

**首批结论：保持原样。**

### 4.8 明确保留的动态 Map

以下 Map 的 key 或 value 由业务、配置或外部系统决定，保持原样：

- `FlowActionContext.variablesSnapshot` 和运行时流程变量。
- `PersonResolveRequest.variables`、`entityData`。
- `IntegrationRequest.parameters`、`IntegrationResult.data`。
- 实体 record、working payload、mutation patch。
- 列表 filters、可信上下文解析结果。
- `UiDataSourceProvider` 的业务 input、configuration 中未声明的动态内容。
- `OpenProcessEvent.attributes` 中除平台内部保留 key 之外的事件属性。

对于这些数据，调用方可以在自己的业务模块中定义局部字段常量，但不应进入
`workflow-contracts` 的全局常量集合。

## 5. 建议首批修改

| 优先级 | 修改项 | 建议落点 | 兼容要求 |
| --- | --- | --- | --- |
| P1 | 动作轨迹字段 key | action 契约包 | `executionTrace` 的 Map/JSON 外形不变 |
| P1 | UI 标准 usage 字符串 | ui 契约包 | SPI 和上下文继续使用 `String`，允许未知值 |
| P1 | 列表查询字段 key | entity/list 契约包 | `query` 继续使用 Map，filters 保持动态 |
| P1 | PersonResolver 自有配置 key | 各实现内部 | Schema 和历史配置字段名不变 |
| P1 | UI Provider 自有配置 key | 各 Provider 内部 | 不共享不同 Provider 的业务默认值 |
| P1 | ChangeTargetResolver 配置 key | resolver 内部 | `recordIdPath/useSourceRecordId` 不改名 |
| P2 | 固定参数生产型流程动作 | 对应 Handler 的参数 record | 复用 `TypedFlowActionHandler`，Schema/JSON 不变 |

首批修改前必须先补或确认以下测试：

- 动作轨迹持久化仍读取 `stage/message/details`。
- UI 标准 usage 的目录、校验和执行行为不变。
- 旧 `EntityListDataProvider` 实现无需修改即可执行。
- 流程动作 Map 到参数 record 的默认值和缺失字段行为与当前一致。
- JSON Schema 输出字段和 required 列表不变。

## 6. 保持原样

| 项目 | 保持原因 |
| --- | --- |
| 自定义流程动作触发时机 | 插件可以增加新值，不能封闭枚举 |
| Provider code、operation、scene | 取值范围由项目或配置扩展 |
| 业务实体字段和流程变量 | 运行时动态定义 |
| filters、patch、payload、attributes | 开放业务载荷 |
| JSON Schema 标准词 | 通用格式词，提取后只增加跳转成本 |
| 分页兼容别名 | 已集中在 normalizer 内，不存在散落问题 |
| `EntityMutationSystemFields` | 已正确集中定义和复用 |
| `OpenProcessEvent.INTERNAL_OUTCOME_VARIABLES` | 已正确集中定义，无重复字面量 |
| 无类型流程动作示例 | 用于展示 Map 扩展模式 |

## 7. 暂缓处理

| 项目 | 暂缓原因 | 继续前需要的证据 |
| --- | --- | --- |
| 删除 `customParams` | 存在旧消费者和持久化依赖 | 全部读写点、序列化和兼容周期 |
| 把 executionTrace 改成强类型列表 | 会改变公开字段类型 | JSON 兼容测试和外部扩展影响 |
| UI 标准输出 DTO | 前后端和 output Schema 共同依赖 | 前端消费者清单与快照测试 |
| 全部 SPI 泛型化 | 当前实现数量少，收益不足 | 至少多个真实实现和重复转换证据 |
| 通用 Schema 生成框架 | 会增加新的抽象和维护成本 | 多扩展点共享需求和明确规则 |
| UI usage 枚举化 | 自定义值和未来扩展边界未关闭 | 明确的版本化扩展策略 |
| 通知动作参数类型化 | 当前缺少完整 Schema 证据 | 通知配置来源、默认值和历史数据 |

## 8. 后续实施约束

后续若根据本盘点执行代码修改，应遵循：

1. 一次只处理一个契约所有者，避免顺带重构无关模块。
2. 新增常量必须有至少一个生产者和一个消费者，或同一实现中存在 Schema/读取重复。
3. 不因字符串相同就跨领域共享常量。
4. 不确定、可扩展的值继续保持 `String`。
5. 旧 Map SPI、JSON 字段名和历史配置保持兼容。
6. 每项改动单独验证，不进行全仓库机械替换。
7. 提交前检查 Git 差异，确认没有无关文件和历史迁移变化。

## 9. 本阶段变更声明

- 新增文档：
  `docs/backend-extension-contract-magic-value-audit_2026-08-12.md`
- Java 代码修改：0。
- 配置文件修改：0。
- 测试代码修改：0。
- 前端代码修改：0。
- 数据库结构修改：0。
- Flyway 迁移新增：0。
- Flyway 迁移修改：0。
- Flyway 迁移删除：0。
- 当前工作区在本任务开始前已有的修改未纳入本次盘点交付，未覆盖、未回退。
