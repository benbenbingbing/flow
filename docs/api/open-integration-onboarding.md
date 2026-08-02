# 开放集成 V1 接入指南

## 适用范围

开放集成 API 用于外部系统以机器身份发起和查询获授权的流程。管理端内部 API、页面
接口和数据库表不属于外部契约。正式契约以
[`openapi-v1.yaml`](openapi-v1.yaml) 和 [`events/catalog.md`](events/catalog.md)
为准。

## 接入前准备

接入双方先确认业务系统负责人、Flow 流程负责人、业务关联键、需要的 Scope、允许访问
的流程 Key、来源网段和 Webhook 出口。生产应用应只授予实际使用的能力：

通用场景配置、固定流程版本和输入契约见
[`external-workflow-scenarios.md`](external-workflow-scenarios.md)。推荐外部系统使用场景
模式，避免把流程版本和变量规则固化在调用方代码中。

| 操作 | Scope |
| --- | --- |
| 读取流程目录 | `process.definition.read` |
| 发起流程 | `process.instance.start` |
| 查询流程状态 | `process.instance.read` |
| 查询活动任务 | `process.task.read` |
| 关联消息 | `process.message.correlate` |
| 取消流程 | `process.instance.cancel` |

管理员在“系统管理 > 开放集成”创建应用。Client Secret 只展示一次；接入方应立即写入
受控 Secret 管理系统，不得放入源码、镜像、工单或聊天记录。来源网段应使用实际出口
CIDR，不能为了联调配置全网段。

## 获取访问令牌

令牌端点使用 HTTP Basic 传递 Client ID 和 Client Secret，正文固定为
`grant_type=client_credentials`。只申请本次调用需要的 Scope：

```bash
curl --fail-with-body --silent --show-error \
  --user "$FLOW_CLIENT_ID:$FLOW_CLIENT_SECRET" \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode scope=process.definition.read \
  "$FLOW_BASE_URL/oauth2/token"
```

Access Token 最长 30 分钟有效，没有 Refresh Token。客户端应在内存中按过期时间缓存，
提前 60 秒刷新；遇到 `401` 只允许重新取令牌后重试一次，不能无限重试。

## 调用约定

- 所有生产流量使用 HTTPS，设置连接、请求和整体超时。
- 发起流程必须使用稳定且唯一的 `Idempotency-Key`；同一键不能配不同请求体。
- 保存响应中的 `traceId`，故障沟通时提供它，不要提供令牌或业务敏感正文。
- 按 `429` 的 `Retry-After` 有界退避；`400`、`403`、`404`、`409`、`422` 不自动重试。
- 客户端必须忽略未知 JSON 字段，并为未知枚举值保留兜底分支。
- 不根据错误文案编写逻辑，只使用 HTTP 状态和 `errorCode`。

Java 和 JavaScript 的可执行示例位于
[`examples/open-integration`](../../examples/open-integration/README.md)，Postman 集合
位于 [`flow-open-integration-v1.postman_collection.json`](postman/flow-open-integration-v1.postman_collection.json)。

## Webhook 接收

接收端必须读取原始请求字节后再解析 JSON，并使用端点签名密钥校验
`Flow-Webhook-Signature`、`Flow-Webhook-Timestamp` 和 `Flow-Webhook-Id`。时间戳超出
允许窗口或签名不匹配时直接拒绝；校验通过后按事件 ID 去重，再异步处理业务。

端点上线前从管理页面发送验证事件。该事件类型为
`com.flow.webhook.validation.v1`，只包含验证 ID 和发送时间，不代表真实业务发生。
生产处理器应忽略未订阅的未知事件类型。

## 上线检查

1. 在预生产环境执行 Java、JavaScript 契约示例和 Postman 集合。
2. 验证同一幂等键重复提交返回原结果，同键异体返回冲突。
3. 验证来源地址不在允许网段时被拒绝。
4. 验证 Webhook 签名、去重、超时和 5xx 重试。
5. 轮换一次 Client Secret 和 Webhook 密钥，确认新旧窗口符合预期。
6. 配置应用级调用量、错误率和 Webhook 死信告警后再放量。
