# 外部流程场景配置

外部系统不应把 Flow 的流程 Key、流程版本和变量结构散落在代码中。管理员为每个
接入应用配置一个场景，场景先保存为草稿，校验通过后发布；外部系统只提交
`scenarioKey` 和业务输入。Flow 在服务端解析已发布 revision、校验输入并固定到配置
中的流程定义版本。

## 配置模型

场景属于一个接入应用，场景 Key 在应用内唯一。创建或更新场景需要：

| 字段 | 约束 |
| --- | --- |
| `scenarioKey` | 稳定的公开标识，只允许字母开头、字母数字及 `._-` |
| `processKey` | 已授权给该应用的流程 Key |
| `processDefinitionVersion` | 可选；填写后启动严格使用该版本，避免流程发布后语义漂移 |
| `inputSchema` | JSON Schema 子集，限制对象大小、字段类型和必填字段 |
| `outcomeMapping` | 仅允许 `status`、`outcomeCode`、`actorId`、`decidedAt`、`opinion`、`evidence`、`reasonCode`、`failureCode` 等声明式字段 |
| `identityMapping` | 显式声明 `namespace` 和 `initiator`，身份值只能映射到 `variables.<field>` |
| `eventTypes` | 六个已发布的 CloudEvents V1 类型白名单 |

配置接口为管理端 API：

```text
GET    /api/integration-applications/{applicationId}/scenarios
POST   /api/integration-applications/{applicationId}/scenarios
POST   /api/integration-applications/{applicationId}/scenarios/validate
POST   /api/integration-applications/{applicationId}/scenarios/{scenarioKey}
POST   /api/integration-applications/{applicationId}/scenarios/{scenarioKey}/publish
POST   /api/integration-applications/{applicationId}/scenarios/{scenarioKey}/disable
```

更新、发布和停用必须携带 `expectedRevision`。更新只创建新的草稿 revision；发布只
切换 `published_revision` 指针，已发布 revision 不会被覆盖。Flow 在每个流程绑定上
保存场景 revision、配置摘要、输入快照和输入摘要，因此历史运行不会被后续配置修改
重新解释。

`/validate` 只执行授权、Schema、映射和事件白名单校验，返回配置摘要，不创建草稿；
调用方可在保存前把它作为确定性的配置门禁。

## 外部调用

场景模式下 `processKey` 可以省略，启动请求只需要稳定的幂等键、业务关联和变量：

```json
{
  "scenarioKey": "generic-approval",
  "businessReference": {
    "system": "sample-system",
    "type": "request",
    "id": "REQ-2026-0001",
    "version": "v3"
  },
  "variables": {
    "requesterId": "user-42",
    "amount": 100
  }
}
```

调用 `POST /api/open/v1/process-instances` 后，响应包含 `scenarioKey` 和
`scenarioRevision`。完成状态中的 `result` 只包含场景配置允许的映射字段；`status` 只
表示 Flow 生命周期，`outcomeCode` 才表示业务决定，不能把 `COMPLETED` 推断成批准；
完整业务数据仍需通过受保护的业务系统查询。未配置场景时，旧的 `processKey` 启动方式
保持兼容。场景模式下身份主体严格由 `identityMapping.initiator` 从变量解析，请求体中的
旧版 `initiator` 字段不会覆盖已发布场景配置。

## 生产部署要求

- 场景、授权、幂等键、绑定和 outbox 必须使用共享 MySQL；禁止把这些状态放在 Pod 本地内存。
- 多副本部署时所有副本使用同一数据库和消息基础设施，管理 API 与运行 API 可独立扩容。
- outbox 投递失败只影响异步事件发送，不回滚已经成功创建的流程；配置告警、重试上限和死信处理。
- readiness 必须检查数据库和迁移完成，liveness 只检查进程存活，避免依赖外部事件系统导致业务 Pod 重启。
- 变更场景前先在预生产验证输入 Schema、固定流程版本和事件白名单，再通过 revision 乐观锁发布。
- 监控 `PROCESS_START`、`PROCESS_CANCEL`、outbox 积压、Webhook 失败率、幂等冲突和
  `PROCESS_NOT_GRANTED`，日志中只记录应用 ID、场景 Key、revision、traceId 和结果码。

本模型是 Flow 的通用能力。任何外部系统都通过自己的应用授权和场景配置接入，不需要
修改 Flow 代码，也不要求 Flow 认识外部系统的项目、审批或发布流程名称。
