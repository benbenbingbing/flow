# ADR-0011 平台接口直接放在领域包

- 状态为已接受
- 日期为 2026-11-23
- 补充 [ADR-0010](0010-group-platform-contracts-by-use.md) 的领域目录规则，更新 [ADR-0009](0009-flatten-platform-port-packages.md) 的包名。

> 配套模型、异常和上下文的目录规则由 [ADR-0012](0012-group-port-supporting-types-by-role.md) 更新。

## 背景

领域目录已经能帮助业务开发者选对查找范围。打开领域后还需要展开 `port`，才能看见具体接口。两个契约制品共用 Java 前缀，也让接口和扩展点的归属难以从包名判断。

## 决策

- `workflow-port` 使用 `com.workflow.port`。平台接口直接声明在对应的领域根包，例如 `com.workflow.port.entity.EntityRecordPort`。
- Port 按主表或业务对象命名，例如 `sys_user` 对应 `SysUserPort`。同一对象的读写通过方法名区分。
- 领域根包承载独立的 Port 接口和包文档。模型、异常、注解及上下文放在各自的功能子包。
- `workflow-spi` 沿用 `com.workflow.contracts`。契约制品间的依赖方向为 `workflow-spi -> workflow-port`。
- UI 数据源与列表查询的扩展协议常量归入 `workflow-spi`，沿用 Java 类名以保持内置 Provider 制品摘要。
- 架构测试检查制品命名空间、领域根包、端口索引以及跨制品依赖。

## 影响

平台 API 类型使用新全限定名。调用方和扩展需要同步更新 import 并重新编译，使用发布快照的扩展按现有流程核对制品摘要。业务操作沿用原授权、事务与数据协议。新的端口入口和方法见 [能力索引](../../../workflow-server/workflow-port/README.md#能力索引)。
