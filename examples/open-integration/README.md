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

本地运行默认监听 `127.0.0.1:9089`；容器运行时通过
`REFERENCE_EXTERNAL_HOST=0.0.0.0` 监听 Pod 网络。凭据可通过
`REFERENCE_CLIENT_ID`、`REFERENCE_CLIENT_SECRET` 和 `REFERENCE_WEBHOOK_SECRET` 覆盖。
设置 `REFERENCE_EXTERNAL_TLS_CERT_FILE` 和 `REFERENCE_EXTERNAL_TLS_KEY_FILE` 可启用
HTTPS；生产验收应使用真实 CA，测试 CA 必须加入 Flow 的 JVM 信任库。设置
`REFERENCE_WEBHOOK_FAIL_COUNT` 可让接收端前 N 次 Webhook 返回失败，默认 503；用
`REFERENCE_WEBHOOK_FAIL_STATUS=400` 可快速构造不可重试错误，仅用于验证重试、死信和
人工重放。不要把它暴露到公网，也不要把默认凭据用于生产环境。

## 本地 k3s 验收

`k3s/` 提供独立的 test-only receiver 清单，使用非 root 容器、健康探针和资源上限，
不会修改 Flow 的业务配置。构建并导入本地镜像后创建测试 Secret：

```bash
docker build -f examples/open-integration/Dockerfile.reference \
  -t flow-reference-external:local examples/open-integration
kubectl apply -k examples/open-integration/k3s
kubectl -n flow-open-integration create secret generic reference-external-credentials \
  --from-literal=client-id=reference-client \
  --from-literal=client-secret=reference-secret \
  --from-literal=webhook-secret=reference-webhook-secret \
  --dry-run=client -o yaml | kubectl apply -f -
```

在已配置场景、应用凭据和流程授权的 Flow 集群上运行闭环验收：

```bash
FLOW_CLIENT_ID='...' \
FLOW_CLIENT_SECRET='...' \
FLOW_SCENARIO_KEY='generic-approval' \
FLOW_INPUT_JSON='{"requesterId":"reference-user","amount":10}' \
K3D_CLUSTER='your-cluster' \
./examples/open-integration/k3s/acceptance.sh
```

完整前置条件、通过标准、故障矩阵和证据要求见
[`docs/testing/open-integration-k3s-acceptance.md`](../../docs/testing/open-integration-k3s-acceptance.md)。

验收脚本覆盖 Token、场景启动、重复启动、并发启动、查询、任务查询、幂等取消、
身份缺失拒绝，以及 receiver 的 Token、幂等、Webhook 签名与去重。设置
`FLOW_SCENARIO_KEY_V2` 可额外验证 revision 切换；设置 `RUN_FAULT_SCENARIOS=1` 会
在显式授权后验证 receiver 停止和 Flow Server 滚动重启期间核心接口仍可用。数据库
连接中断、Token 过期和 Secret 轮换需要在目标集群按同一发布流程执行，脚本不会擅自
破坏共享环境。
