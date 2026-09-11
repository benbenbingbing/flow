# ADR-0006：共享契约按业务能力与角色分包

- 状态：已接受
- 日期：2026-09-05
- 决策范围：`workflow-contracts`

## 背景

`workflow-contracts` 同时承载跨模块单一能力、可发现的业务扩展、边界模型、运行时
上下文和注解。历史根包将 `Port`、`Provider`、`Command`、`Result` 混放，调用方无法
从包名判断实现责任；另一方面，项目中的 `api` 已稳定表示 Controller、Request、
Response 等传输层类型，将契约端口也放入 `api` 会形成两套语义。

初版分包继续使用了 `catalog.api`、`execution.api`、`trigger.spi` 等细粒度目录。这些
目录重复了 `FlowActionCatalogPort`、`FlowActionHandler` 等类名已经表达的信息，增加了
导航深度，却没有形成新的业务边界。

## 决策

1. 新契约采用 `com.workflow.contracts.<domain>[.<feature>].<role>`。`role` 使用
   `port`、`spi`、`model`，必要时使用 `annotation`、`context` 或 `error`。
2. `port` 表示平台或领域模块实现、其他模块调用的稳定能力；`spi` 表示业务或插件
   实现、由宿主发现并调用的扩展点。`model` 只承载边界数据，不依赖 `port` 或 `spi`。
3. 包层级只保留能够形成独立业务语义的 `feature`。类名已经能够区分职责时，不再增加
   `catalog`、`execution`、`trigger`、`target`、`step`、`person` 等中间目录。例如流程
   动作统一归入 `process.action.port` 与 `process.action.spi`。
4. `*Port` 用于单一能力边界；`*Provider`、`*Handler`、`*Resolver`、`*Connector`
   用于宿主发现的扩展角色。具体 Port 实现命名为 `*Adapter`。
5. `FlowAction` 归属 `process.action`；实体写入与 UI 契约分别归属
   `entity.mutation` 与 `entity.ui`；流程办理人解析归属 `process.assignment`；Embed
   Launch 与 Runtime 能力分别归属 `embed.launch` 与 `embed.runtime`。
6. 本次包结构尚未作为稳定外部版本发布，因此采用原子迁移：删除已被 canonical 类型
   替代的 deprecated 接口和兼容 shim，所有实现、继承关系、注入点和测试直接使用新
   FQCN，不建立 `legacy -> api -> port` 的多层桥接链。
7. 接口的嵌套 record、异常和生命周期 Scope 与接口一起迁移，避免新 Port 继续依赖已
   删除的旧类型。未被替换的边界模型仍保留在所属业务包，本次不做无关模型迁移。

## 目标结构示例

```text
com.workflow.contracts.process.port
com.workflow.contracts.process.action.port
com.workflow.contracts.process.action.spi
com.workflow.contracts.process.assignment.spi

com.workflow.contracts.embed.launch.port
com.workflow.contracts.embed.runtime.port

com.workflow.contracts.entity.port
com.workflow.contracts.entity.form.port
com.workflow.contracts.entity.list.spi
com.workflow.contracts.entity.mutation.port
com.workflow.contracts.entity.ui.port
com.workflow.contracts.entity.ui.spi
```

## 依赖规则

```text
port -> model
spi  -> model
spi  -> port（仅限宿主显式提供的能力）
model -X-> port / spi
port  -X-> spi
```

跨领域依赖默认只能指向对方的边界模型；例外必须在架构测试中说明。禁止新增全局
`common`、`util`、`dto`、`vo` 包，也禁止在 `workflow-contracts` 中放置 Controller
DTO、Spring Bean、持久化模型或业务实现。

## 实施约束

1. Spring Bean、Registry、集合注入和业务实现必须直接实现或依赖 canonical
   `port`/`spi`，不能通过旧接口间接获得可赋值关系。
2. 每个稳定 SPI 都必须存在明确的宿主 Registry 或集合消费者。尚未接入宿主的接口必须
   标为 incubating，并明确注册 Bean 不会自动生效。
3. 架构测试要求 `port` 和 `spi` 包只包含接口，并禁止 `port` 依赖 `spi`。
4. 若未来已发布的契约再次迁包，必须单独评估插件、反射、Spring 类型注入和序列化兼容，
   不能默认复制当前的直接删除策略。

## 后果

- `api` 继续专用于传输层，`port`/`spi` 专用于跨模块能力边界，含义不再冲突。
- 同一业务能力的接口聚合在一个 `port` 或 `spi` 包中，源码树更浅，导航成本更低。
- 旧 FQCN 不再可用；本次需要一次性迁移仓库内全部调用方和实现类。
- ADR-0007 退役开放流程后，`process.open.port`、`process.open.spi` 及其消费者均已删除，
  不再作为目标结构的一部分。
- 架构测试成为新增或调整公共契约时的必经门禁。
