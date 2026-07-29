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
