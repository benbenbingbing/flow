# ADR-0001：开放集成保持模块化单体边界

- 状态：已接受
- 日期：2026-07-29
- 决策范围：开放集成 V1

## 背景

Flow 当前以模块化单体部署，跨模块能力通过 `workflow-contracts` 暴露。开放集成需要增加
机器认证、公共流程 API、Webhook 和连接器，但当前没有证据表明这些能力需要独立的发布
节奏、存储或故障域。

直接拆分网关、认证服务和投递服务会提前引入服务发现、跨服务鉴权、消息一致性和更多
运维依赖，不能改善 V1 最关键的外部契约稳定性。

## 决策

V1 保持单个 `workflow-server` 运行制品，新增两个聚合模块：

- `workflow-open-api`：接入应用、机器认证、Scope、幂等和版本化公共 API。
- `workflow-webhook`：事件物化、订阅、可靠投递、死信和重放。

依赖规则：

1. 开放 DTO 和事件 DTO 属于独立契约，不复用现有 Web Controller DTO、Mapper Record
   或 Flowable 类型。
2. `workflow-open-api` 只能通过 `workflow-contracts` 调用流程和实体能力。
3. 业务模块通过 `IntegrationDomainEventPublisher` 发布事件，不依赖 Webhook 实现。
4. 新模块不得直接访问其他业务模块的 Mapper。
5. 只有真实的容量、发布节奏或故障隔离证据出现后，才评估拆分进程。

## 首发参考系统

在真实接入方确定前，使用 `project-management-system` 作为契约参考系统：

- 业务键：`system + type + id`
- 主要场景：幂等发起项目审批、查询状态、接收任务和流程结果事件
- 人工审批仍在 Flow 中完成
- 外部系统不能调用设计态、系统管理或任意动态实体 CRUD

参考系统只用于固定 V1 契约和测试数据，不在运行代码中硬编码。

## 影响

- V1 可以复用现有事务、Outbox、审计、指标和多 Pod 租约基础。
- 对外契约必须先于 Controller 实现并接受兼容性检查。
- 后续拆分时，新模块边界可以成为独立服务的候选边界。
