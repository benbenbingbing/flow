# ADR-0003：事件采用 CloudEvents 和至少一次 Webhook 投递

- 状态：已接受
- 日期：2026-07-29
- 决策范围：开放集成 V1 事件与投递

## 背景

现有 Outbox 能在多 Pod 下可靠认领内部事件，但只路由本进程处理器。外部系统需要稳定
事件格式、签名、失败恢复和可查询的投递记录。

网络超时场景无法可靠区分“接收方未处理”和“接收方已处理但响应丢失”，因此 HTTP
Webhook 无法承诺 exactly-once。

## 决策

1. 外部事件采用 CloudEvents 1.0 JSON 格式，事件类型和数据 Schema 独立版本化。
2. V1 使用 at-least-once 语义，接收方必须按 CloudEvent `id` 去重。
3. 业务状态变更与集成领域事件在同一数据库事务中写入现有 Outbox。
4. Outbox 本地处理器幂等物化 `webhook_event` 和 `webhook_delivery`。
5. Delivery Worker 使用数据库 lease、heartbeat 和 fencing token，禁止过期 Worker
   覆盖新结果。
6. 每个订阅使用独立 HMAC-SHA256 密钥，请求签名覆盖事件 ID、时间戳和原始请求体。
7. 只接受 HTTP `2xx` 成功；不跟随重定向；重试、死信和人工重放均保留审计。
8. 事件只包含稳定状态摘要和业务关联，不包含完整表单、审批意见、附件或内部变量。

## V1 事件类型

- `com.flow.process.started.v1`
- `com.flow.task.created.v1`
- `com.flow.task.completed.v1`
- `com.flow.process.completed.v1`
- `com.flow.process.terminated.v1`
- `com.flow.process.failed.v1`

## 影响

- 接收方必须实现签名校验、时间窗口校验和事件 ID 去重。
- 人工重放保留原事件 ID，只创建新的 delivery ID。
- Webhook 积压和死信成为正式运维对象，必须有指标、告警和处理手册。
