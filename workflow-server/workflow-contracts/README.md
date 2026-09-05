# workflow-contracts

`workflow-contracts` 只放跨模块边界和受宿主治理的扩展协议；不得放 Controller DTO、
Mapper Record、Spring Bean、持久化模型或业务实现。

## 包结构

canonical 契约遵循：

```text
com.workflow.contracts.<domain>[.<feature>].port|spi
```

角色含义如下：

| 角色 | 用途 | 实现责任 |
| --- | --- | --- |
| `port` | 单一、稳定的跨模块调用能力 | 平台或领域模块实现，其他模块调用 |
| `spi` | 按编码、类型或集合发现的扩展点 | 业务/插件实现，宿主 Registry 调用 |
| `model` | 边界数据 | 不依赖 `port` 或 `spi` |
| `annotation` / `context` / `error` | 编译元数据、生命周期上下文或跨边界异常 | 按能力就近放置 |

`feature` 只保留具有独立业务语义的子领域，例如 `entity.form`、`entity.list`、
`entity.mutation`、`entity.ui`、`process.action`、`process.assignment` 与
`process.open`。不以 `catalog`、`execution`、`trigger`、`target`、`step`、
`datasource` 或 `hotfix` 等可由类名表达的技术角色继续分包。

例如：

- 流程动作：`process.action.port.FlowActionCatalogPort`、
  `process.action.spi.FlowActionHandler`
- 实体变更：`entity.mutation.port.EntityMutationPort`、
  `entity.mutation.spi.EntityMutationStepProvider`
- 实体 UI：`entity.ui.port.UiExtensionCatalogPort`、
  `entity.ui.spi.UiDataSourceProvider`
- 当前操作人：`identity.port.CurrentActorPort`

## 命名与依赖

- `*Port` 表示单一逻辑能力边界。
- `*Provider`、`*Handler`、`*Resolver`、`*Connector` 表示宿主可发现的扩展角色；
  只有真实 Registry/集合消费者存在时才能公开为稳定 SPI。
- 具体 Port 实现命名为 `*Adapter`，不能再以 `Port` 结尾。
- `port -> model`，`spi -> model`；SPI 仅可调用宿主明确暴露的 Port。
- 禁止新增全局 `common`、`util`、`dto`、`vo` 包。

## 发布边界

`entity.list.spi.EntityListActionProvider` 与
`entity.list.spi.DataScopePredicateProvider` 目前是 incubating 预留接口，不存在宿主
Registry 或自动路由；实现或注册 Bean 不会使其生效，不能作为稳定扩展承诺。

每个 canonical 类型只保留一个 FQCN，不提供 deprecated bridge、旧包别名或值镜像。
端口公开的嵌套 record、异常和 Scope 必须定义在该 canonical Port 内，避免以已删除
类型泄漏边界签名。
