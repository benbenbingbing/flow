# 开放集成客户端示例

这里的 Java 和 JavaScript 客户端都执行同一个最小契约：使用 Client Credentials 获取
短期访问令牌，再读取当前应用有权访问的流程定义。它们不会记录 Client Secret 或完整
Access Token。

准备一个已启用的接入应用，并至少授予 `process.definition.read` Scope。凭据只在创建
或轮换时展示一次，应通过本地环境变量或 CI Secret 注入：

```bash
export FLOW_BASE_URL=https://flow.example.com
export FLOW_CLIENT_ID=replace-me
export FLOW_CLIENT_SECRET=replace-me
./examples/open-integration/run-contract-tests.sh
```

`FLOW_BASE_URL` 必须是服务根地址，不包含 `/api`。脚本依次运行 Java 17+ 和
Node.js 22+ 示例；任一步拿不到令牌、收到非 2xx 响应或响应不符合 V1 包装结构时都会
非零退出。测试环境使用自签名证书时，应把 CA 加入 Java 和操作系统信任库，不要关闭
TLS 校验。

配置场景后，可用 Node.js 22+ 运行 `scenario-smoke.mjs` 验证场景模式、输入契约、
固定流程版本和幂等重放。通过 `FLOW_SCENARIO_KEY`、`FLOW_INPUT_JSON` 和可选的
`FLOW_BUSINESS_SYSTEM`、`FLOW_BUSINESS_TYPE`、`FLOW_BUSINESS_ID` 注入参数；脚本不依赖
任何特定外部系统名称。

## 通用参考外部系统

`reference-external-system.mjs` 是仅用于契约验收的本地参考实现，不是 Flow 的运行时
依赖，也不代表任何具体业务系统。它覆盖 Client Credentials、启动幂等、查询、取消以及
Flow Webhook 的 HMAC 校验和事件去重，便于在没有真实外部系统时验证接入边界：

```bash
REFERENCE_EXTERNAL_SELF_TEST=1 node examples/open-integration/reference-external-system.mjs
node examples/open-integration/reference-external-system.mjs
```

参考实现只监听 `127.0.0.1`，默认端口为 `9089`，凭据可通过
`REFERENCE_CLIENT_ID`、`REFERENCE_CLIENT_SECRET` 和 `REFERENCE_WEBHOOK_SECRET` 覆盖。
不要把它暴露到公网，也不要把默认凭据用于生产环境。
