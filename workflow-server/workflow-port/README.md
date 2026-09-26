# workflow-port

平台跨模块能力契约。与 `workflow-spi` 共同替代原 `workflow-contracts`；Maven 制品拆分保留
`com.workflow.contracts` Java 命名空间，不为同一类型建立桥接接口、旧包别名或模型镜像。

## 职责与依赖

- `*Port` 表示平台提供的稳定调用能力，由平台/领域模块实现，其他模块调用。
- Port 的参数、结果及双方共用的基础模型放在本模块，只保留一份定义。
- Port 实现建议命名为 `*Adapter`，实现和 Spring 装配留在宿主，不得以 `Port` 结尾。
- 本模块不得依赖 `workflow-spi`、core、领域实现、Spring、MyBatis、Jackson 或 Servlet。
- `workflow-spi -> workflow-port` 是唯一允许的契约制品依赖方向。
- 普通调用方显式依赖 `workflow-port`；实现或调度 Provider 的模块同时显式依赖两个制品。
- 数据库方言继续由独立 `workflow-database` 维护，不迁入这里。

## 包与模型归属

沿用 `com.workflow.contracts.<domain>[.<feature>].<role>`：

| 角色 | 用途 |
| --- | --- |
| port | 平台能力接口，统一以 Port 结尾 |
| model | 请求、命令、结果、描述符和协议枚举，不依赖 Port/SPI |
| context | 调用上下文或生命周期作用域，宿主按明确范围使用 |
| annotation | 元数据声明，拦截、授权、审计实现由宿主提供 |
| error | 边界异常、错误码和错误投影 |

`feature` 表达业务能力，如 `entity.form`、`entity.mutation`、`process.action`。
不另建全局 common/util/dto/vo，也不以 request/response 等角色继续拆分模型目录。
不再使用顶层 action/ui 或 identity.resolver 旧包。

Port 签名涉及的模型及其依赖必须留在本模块；不能因为某个类型名称带 Context 就移入 SPI。
例如 `EntityFormRuntimePort` 使用的 `UiRuntimeResolutionContext` 留在这里。
`EntityMutationContext`、`EntityFormRuntimeContext` 是边界数据，仍放所属 model 包。
`UiInvocationContext` 是扩展输入，随 Provider 归入 workflow-spi；`EmbedBoundaryFailure`
是错误投影，保持 error 角色，不强制改成 Port。

## 能力入口

- 身份与组织：CurrentActorPort、CurrentAuthorizationPort、IdentityDirectoryPort、
  IdentityMembershipPort、OrganizationPositionDirectoryPort。
- 实体：EntityRecordQueryPort、EntityMutationPort、EntityFormRuntimePort 等。
- 流程：ProcessRuntimePort、FlowActionRuntimePort、FlowActionCatalogPort、
  PersonResolverRegistrationPort 等。
- 启动、迁移与审计：BootstrapJobPort、MigrationAssetPort、DictionaryMigrationPort、SystemAuditPort。
- Embed：embed.launch.port 与 embed.runtime.port。
- 异步事件发布：`outbox.port.OutboxPublishPort`；发布请求模型位于 outbox.model。
- 执行预算：`execution.port.ExecutionControlPort`；由平台实现并在单次调用内传给 Provider。

原 OutboxPublisher 迁为 OutboxPublishPort；原 ExecutionControl 迁为 ExecutionControlPort。
Outbox 的主题和幂等键、外层事务参与规则均保持不变，实际投递与重试仍由 workflow-outbox 负责。

## 验证与兼容性

`ContractModuleBoundaryTest` 按类型源码归属检查两制品无重复类型、Port 不依赖 SPI。
`ContractPackageArchitectureTest` 检查角色后缀、纯模型依赖以及不再引入旧包或废弃桥接类型。
变更后执行 `mvn -pl workflow-app -am clean test`，避免旧 class 掩盖迁移遗漏。
外部调用方需要替换 Maven 依赖并按新接口名称重新编译；HTTP JSON 和数据库结构不因拆分改变。
扩展接入、改名清单及发布版本注意事项见 [workflow-spi](../workflow-spi/README.md)。
