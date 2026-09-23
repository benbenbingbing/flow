# workflow-contracts

`workflow-contracts` 只放跨模块边界和受宿主治理的扩展协议；不得放 Controller DTO、
Mapper Record、Spring Bean、持久化模型或业务实现。

## 包结构

canonical 契约遵循：

```text
com.workflow.contracts.<domain>[.<feature>].<role>
```

角色含义如下：

| 角色 | 用途 | 实现责任 |
| --- | --- | --- |
| `port` | 单一、稳定的跨模块调用能力 | 平台或领域模块实现，其他模块调用 |
| `spi` | 按编码、类型或集合发现的扩展点 | 业务/插件实现，宿主 Registry 调用 |
| `model` | 边界数据 | 不依赖 `port` 或 `spi` |
| `annotation` | 编译或运行时元数据声明 | 拦截、授权或审计实现由宿主提供 |
| `context` | 调用上下文或生命周期作用域 | 宿主构造并在明确的调用范围内使用 |
| `error` | 跨边界异常、失败投影与失败码 | 调用方按稳定失败语义处理 |

`feature` 只保留具有独立业务语义的子领域，例如 `entity.form`、`entity.list`、
`entity.mutation`、`entity.ui`、`process.action` 与 `process.assignment`。
不以 `catalog`、`execution`、`trigger`、`target`、`step`、
`datasource` 或 `hotfix` 等可由类名表达的技术角色继续分包。

例如：

- 流程动作：`process.action.port.FlowActionCatalogPort`、
  `process.action.spi.FlowActionHandler`
- 实体写入：`entity.mutation.port.EntityMutationPort`
- 实体 UI：`entity.ui.port.UiExtensionCatalogPort`、
  `entity.ui.spi.UiDataSourceProvider`
- 当前操作人：`identity.port.CurrentActorPort`

当前能力与角色对应如下；只创建已有类型需要的包，不为每种能力预建全套目录：

| 能力 | 包角色 |
| --- | --- |
| `audit` | `port`、`model`、`annotation`、`context` |
| `bootstrap` | `port` |
| `entity` | `port` |
| `entity.form` | `port`、`model` |
| `entity.list` | `spi`、`model` |
| `entity.mutation` | `port`、`model`、`error` |
| `entity.ui` | `port`、`spi`、`model`、`context` |
| `process` | `port`、`model` |
| `process.action` | `port`、`spi`、`model`、`context` |
| `process.assignment` | `spi`、`model`、`error` |
| `identity` | `port`、`model` |
| `identity.position` | `port`、`model`、`error` |
| `embed.launch` | `port`、`model` |
| `embed.runtime` | `port`、`model`、`annotation`、`context` |
| `embed` | `error` |
| `migration` | `port`、`model` |

配套模型跟随契约所表达的能力：流程动作归入 `process.action`，人员解析归入
`process.assignment`，实体 UI 的目录、热修复和发布模型归入 `entity.ui`。
不再使用顶层 `action`、`ui` 或 `identity.resolver` 存放这些类型。

请求、命令、结果、描述符和协议枚举在同一能力的 `model` 中聚合，不继续拆出
`request`、`response`、`command`、`result` 等单类目录。`EntityMutationContext` 和
`EntityFormRuntimeContext` 是随命令或查询返回的数据模型，保留在相应 `model` 包；
`FlowActionContext` 带有宿主运行时访问能力，归入 `process.action.context`。

两个小型支持类保留在所属能力根包：`audit.AuditEventIds` 负责稳定事件 ID，
`entity.ui.UiProviderArtifactIdentity` 继续提供现有制品摘要默认算法；后者本轮只迁包，
不调整宿主职责或摘要算法。共用扩展归属枚举保留为 `extension.ExtensionImplementationOrigin`。

## 命名与依赖

- `*Port` 表示单一逻辑能力边界。
- `*Provider`、`*Handler`、`*Resolver` 表示宿主可发现的扩展角色；
  只有真实 Registry/集合消费者存在时才能公开为稳定 SPI。
- 具体 Port 实现命名为 `*Adapter`，不能再以 `Port` 结尾。
- `port -> model`，`spi -> model`；SPI 仅可调用宿主明确暴露的 Port。
- `model` 不依赖 `port` 或 `spi`；带运行时访问能力的 `context` 不按纯模型约束。
- 禁止新增全局 `common`、`util`、`dto`、`vo` 包。

启动协调、迁移资产登记和流程动作运行时访问分别命名为 `BootstrapJobPort`、
`MigrationAssetPort`、`FlowActionRuntimePort`。这些是单一能力端口，不因原类名包含
`Coordinator`、`Handler` 或 `Access` 而归入 SPI。

## 发布边界

`entity.list.spi.EntityListActionProvider` 与
`entity.list.spi.DataScopePredicateProvider` 目前是 incubating 预留接口，不存在宿主
Registry 或自动路由；实现或注册 Bean 不会使其生效，不能作为稳定扩展承诺。

每个 canonical 类型只保留一个 FQCN，不提供 deprecated bridge、旧包别名或值镜像。
端口公开的嵌套 record、异常和 Scope 必须定义在该 canonical Port 内，避免以已删除
类型泄漏边界签名。

迁包时必须同步仓库内实现、调用方、示例与测试，并执行 clean 编译，防止旧 class
掩盖遗漏。外部扩展需要随契约重新编译；依赖 class 字节码计算默认摘要的 UI Provider
还需核对已有发布快照固定的摘要，不能仅凭 Java 编译成功判断发布兼容性。
