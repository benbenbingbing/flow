# ADR-0012 在 Port 领域包内按类型归集配套契约

- 状态为已接受
- 日期为 2026-11-24
- 补充 [ADR-0011](0011-platform-port-namespace.md) 的配套契约目录规则。

## 背景

`process` 中的任务、实例和查询模型分别显示为 `task.model`、`instance.model` 和 `query.model`。开发者需要在领域第一层辨别多个相似目录，才能找到接口签名中的模型和异常。

## 决策

- 领域根包直接列出业务 Port。配套类型在领域包下先分为 `model`、`context` 和 `error`，再按业务能力建立子包。
- 同一领域共享的配套类型直接放在对应分类包中。例如通用命令回执在 `process.model`，任务命令在 `process.model.task`。
- 注解使用 `<domain>.<feature>.annotation`。扩展点使用 `workflow-spi` 的命名空间。

## 影响

迁移后的 Java 全限定名需要同步到调用方并重新编译。架构测试检查配套类型及 Port 接口的包位置。目录示例见 [workflow-port 包与模型归属](../../../workflow-server/workflow-port/README.md#包与模型归属)。
