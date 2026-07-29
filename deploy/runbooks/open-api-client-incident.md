# 开放 API 客户端故障处理

## 适用范围

本文处理 Client Credentials 签发和 `/api/open/v1` 调用异常。先记录时间窗口、应用 ID、
HTTP 状态、`errorCode` 和 `traceId`。不得收集 Client Secret、Access Token、完整
Authorization Header 或未经脱敏的业务变量。

## 401 与令牌签发失败

1. 区分 `/oauth2/token` 的 `invalid_client` 与业务 API 的
   `INVALID_ACCESS_TOKEN`，确认请求到达的环境和域名。
2. 检查应用是否启用、凭据是否已吊销或过期、服务端时间是否同步。
3. 检查 Client ID 和 Secret 是否从同一版本的受控 Secret 注入；不要通过日志打印验证。
4. 单个实例异常时比较各 Pod 的就绪状态和 JWT Key ID。所有副本必须使用同一签名密钥。
5. 必须轮换时先创建新凭据、更新客户端并验证，再吊销旧凭据。不要原地覆盖 Secret。

## 403

`INSUFFICIENT_SCOPE` 表示令牌缺少操作 Scope；`PROCESS_NOT_GRANTED` 表示应用没有对应流程
授权；`SOURCE_ADDRESS_NOT_ALLOWED` 表示来源网段不符。按具体错误修正最小授权，不要
扩大到通配 Scope、全部流程或全网 CIDR。Ingress 后的来源地址异常时核对可信代理配置，
不能盲目信任客户端提供的转发头。

## 409、422 与幂等

`IDEMPOTENCY_KEY_REUSED` 表示同一键被用于不同请求体；客户端应生成新的业务操作键，
不能自动改写后重放。`REQUEST_IN_PROGRESS` 只允许按响应建议短暂轮询。`422` 是变量契约
不匹配，应修复请求或流程契约，重试不会成功。

## 429、5xx 与延迟升高

1. 429 按 `Retry-After` 加随机抖动退避，限制总尝试次数和整体时长。
2. 查看应用级请求量、令牌端点限流、HTTP 延迟和数据库连接池等待。
3. 5xx 先用 `traceId` 定位服务端日志，再判断是否可重试。发起流程只能用原
   `Idempotency-Key` 重试，不能生成新键。
4. 单一应用造成资源压力时，先降低该应用配额或暂停应用，不影响其他接入方。
5. 恢复后运行 Java 和 JavaScript 契约示例，并观察错误率和 p95 至少 15 分钟。
