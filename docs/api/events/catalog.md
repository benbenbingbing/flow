# 开放集成事件目录

Flow 开放集成 V1 使用 CloudEvents 1.0 JSON 格式。Schema 位于当前目录，示例位于
`examples/`。

| 事件类型 | Schema | 业务含义 |
| --- | --- | --- |
| `com.flow.process.started.v1` | `process-started-v1.schema.json` | 流程实例成功创建 |
| `com.flow.task.created.v1` | `task-created-v1.schema.json` | 人工任务进入可处理状态 |
| `com.flow.task.completed.v1` | `task-completed-v1.schema.json` | 人工任务完成 |
| `com.flow.process.completed.v1` | `process-completed-v1.schema.json` | 流程正常结束 |
| `com.flow.process.terminated.v1` | `process-terminated-v1.schema.json` | 流程被终止 |
| `com.flow.process.failed.v1` | `process-failed-v1.schema.json` | 流程进入需要人工处理的失败状态 |

兼容规则：

- 同一事件版本可以新增可选字段，不能删除必填字段或改变字段语义。
- 接收方必须忽略未知字段，并按事件 `id` 去重。
- `data` 只携带状态摘要和业务关联；详情通过授权的开放 API 查询。
- 破坏性变更必须发布新的事件类型版本。

签名原文：

```text
<event-id>.<unix-timestamp>.<raw-request-body>
```

请求头：

```text
Flow-Webhook-Id: <event-id>
Flow-Webhook-Timestamp: <unix-timestamp>
Flow-Webhook-Signature: v1=<base64-hmac-sha256>
Content-Type: application/cloudevents+json
```

只有 HTTP `2xx` 表示投递成功。Flow 不跟随重定向；`408`、`409`、`425`、
`429` 和 `5xx` 会按有界退避策略重试，其他 `4xx` 直接进入死信。默认最多投递
8 次。

投递语义为 at-least-once。接收方可能在成功处理后因网络中断导致 Flow 重试，因此必须：

- 使用常量时间比较校验签名；
- 只接受本地时钟前后 5 分钟内的时间戳；
- 按 CloudEvent `id` 持久化去重，不能只在内存中去重；
- 返回 `2xx` 前完成业务事务和去重记录提交。

签名密钥轮换后，新投递立即使用新版本。已创建的投递固定使用创建时的密钥版本，避免
重试期间签名漂移。旧密钥保留 48 小时仅用于接收方平滑切换，之后由系统清理。
