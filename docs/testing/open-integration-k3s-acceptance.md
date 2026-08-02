# 通用外部流程接入 k3s 验收

本文定义 Flow 与任意外部系统做开放集成时的最小可重复验收。参考系统只用于提供
可控的接收端，不是 Flow 的业务依赖，也不包含任何特定客户系统专用逻辑。

## 验收范围

`examples/open-integration/k3s/acceptance.sh` 按以下顺序验证：

1. Flow Client Credentials 令牌、场景启动和固定 revision 返回。
2. 相同 `Idempotency-Key` 重放、四路并发启动、实例查询和任务分页。
3. 幂等取消、缺少身份变量时的 422 拒绝。
4. 参考接收端的令牌、请求幂等、Webhook HMAC 校验和事件去重。
5. 可选的第二场景 revision 切换、接收端停止以及 Flow Server 滚动重启。接收端停止
   阶段只验证 Flow 核心接口，脚本会跳过需要接收端在线的参考系统契约。

数据库连接中断、令牌过期和接入 Secret 轮换必须在目标集群执行。脚本不会主动破坏
共享数据库或替换共享 Secret；这些场景应按发布审批执行并保存 Pod、应用日志、指标和
审计记录。

## 前置条件

- Kubernetes/k3s 上已部署 Flow，开放 API 已启用，且接入应用拥有：
  `process.instance.start`、`process.instance.read`、`process.instance.cancel`、
  `process.task.read`。
- 已发布一个带输入契约和身份映射的场景。`FLOW_SCENARIO_KEY` 必须是调用方有权访问的
  场景；需要版本切换时另提供 `FLOW_SCENARIO_KEY_V2`。
- 本机有 `kubectl`、`curl`、Node.js 22+；默认构建参考镜像还需要 Docker。使用 k3d
  导入镜像时还需要 `k3d`。
- 参考接收端只在验收命名空间监听，默认凭据仅用于本地测试，禁止复用到生产。

## 运行

```bash
FLOW_CLIENT_ID='...' \
FLOW_CLIENT_SECRET='...' \
FLOW_SCENARIO_KEY='generic-approval' \
FLOW_INPUT_JSON='{"requesterId":"reference-user","amount":10}' \
K3D_CLUSTER='crest-validation' \
./examples/open-integration/k3s/acceptance.sh
```

已有镜像时设置 `BUILD_REFERENCE_IMAGE=0`。Flow 或参考接收端不是本地集群时，分别用
`FLOW_BASE_URL`、`REFERENCE_BASE_URL` 指定服务根地址；未指定时脚本只在当前 kubectl
上下文创建端口转发。若 `FLOW_BASE_URL` 是外部地址且故障阶段会滚动重启 Flow，设置
`FLOW_RESTART_PORT_FORWARD=1` 让脚本在重启后重新建立本地端口转发；否则脚本要求外部
地址自行恢复并在失败时退出。`REFERENCE_TOKEN_PATH` 默认 `/oauth/token`，Flow 令牌
路径固定为 `/oauth2/token`。

Webhook 目标默认必须是 HTTPS，且必须命中 `application.httpAllowedHosts`。只有在确实
需要访问内部 HTTPS 服务、并已完成网络策略和证书信任配置的验收环境中，才显式设置
`application.httpAllowPrivateAddresses=true`（环境变量
`WORKFLOW_HTTP_ALLOW_PRIVATE_ADDRESSES=true`）。该开关默认关闭，不会绕过主机 allowlist、
TLS 校验或重定向限制；生产配置应优先使用域名、NetworkPolicy 和真实 CA。

参考接收端支持可控故障验收：`REFERENCE_WEBHOOK_FAIL_COUNT=N` 让前 N 次 Webhook
返回失败，`REFERENCE_WEBHOOK_FAIL_STATUS=400` 可验证不可重试错误进入死信；这些变量
仅用于测试，不得用于生产接收端。

## 通过标准与证据

每个场景必须保存命令退出码、开始/结束时间、脚本 JSON 输出、Flow Server/Worker Pod
状态、应用错误日志、HTTP 5xx、数据库连接池和 OpenTelemetry 导出失败计数。并发启动
必须全部返回 201，重放必须返回相同实例和 `Idempotent-Replay: true`；故障期间核心
接口不得出现未预期的 5xx。

| 场景 | 触发方式 | 通过条件 | 必留证据 |
| --- | --- | --- | --- |
| 正常闭环 | 默认运行 | 全部契约断言通过 | 脚本输出、Pod、HTTP 指标 |
| 参考端停止 | `RUN_FAULT_SCENARIOS=1` | Flow 核心 API 仍可用，恢复后可继续接收 | 停止窗口、重启次数、错误率 |
| Flow Server 滚动重启 | `RUN_FAULT_SCENARIOS=1` | 就绪副本始终满足发布策略，接口无非预期 5xx | rollout、PDB、请求指标 |
| 数据库连接中断 | 目标集群按 runbook 执行 | 有界超时、错误可重试，恢复后新请求成功 | 数据库事件、应用日志、连接池 |
| 令牌过期 | 提供 `FLOW_EXPIRED_ACCESS_TOKEN` | 返回 401，不泄漏业务数据 | 请求响应、审计日志 |
| Secret 轮换 | 按 `integration-secret-rotation` 执行 | 新凭据生效，旧凭据按窗口失效 | Secret 版本、Pod checksum、审计 |
| HTTPS Webhook | 配置真实 CA 或测试 truststore | 注册、验签、投递和重试成功；非 HTTPS 或未 allowlist 地址被拒绝 | endpoint、delivery、证书和审计 |
| 死信与人工重放 | 参考端返回 4xx 后恢复 | 不可重试事件进入 `DEAD`，重放成功且 eventId 不变 | 原始/重放 delivery、去重计数 |
| 乱序事件 | 参考端按不同 eventId 逆序提交事件 | 每个事件独立验签、接收并去重，不依赖到达顺序 | 事件 ID、类型、接收时间 |

本地参考接收端已验证镜像构建、非 root 运行、健康探针、Token、请求幂等、取消、
Webhook 签名和去重。Flow 端到端结果必须在具备真实场景、接入应用和授权的集群上
重新运行后填写到发布记录；不能以参考接收端自测代替 Flow 验收。

## 清理

验收结束后删除测试命名空间和端口转发进程。不要删除目标 Flow 命名空间、数据库卷或
生产 Secret。
