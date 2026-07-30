# Flow 生产可观测性验收记录

> 分支：`feature/production-observability`
> 记录时间：2026-07-30 08:46 CST
> 状态：实施中，未完成最终验收

## 已完成

- 应用侧统一 `X-Trace-Id` 与 `X-Request-Id` 关联上下文。
- Open API 请求保护和系统审计过滤器复用同一业务 trace，不再分别生成。
- 日志 MDC 增加 `business_trace_id`、`request_id`，保留兼容字段 `traceId`。
- Spring Boot Actuator 指标增加 `service`、`environment`、`version` 公共标签。
- HTTP 指标 Histogram 改为 SLO 固定桶，避免默认高成本桶。
- 引入 Micrometer OTel bridge 和 OTLP exporter，默认关闭 Trace 导出。
- Helm values 增加可关闭的 OTLP Trace 配置，业务 Pod 不依赖 Collector 探针。
- 本地 k3s 可观测栈脚本与固定 chart 版本已补齐：
  - kube-prometheus-stack `87.21.0`
  - Loki `7.2.0`
  - Promtail `6.17.1`
  - Tempo `1.24.4`
  - OpenTelemetry Collector `0.165.0`
  - SkyWalking `4.3.0`
- `deploy/scripts/validate-manifests.sh` 已覆盖 Flow Chart 与观测栈 Helm 渲染。
- 增加本地轻量观测栈，避免在资源紧张的 k3s 上安装完整 kube-prometheus-stack：
  - Prometheus `prom/prometheus:v3.13.1`
  - Grafana `grafana/grafana:12.3.1`
  - Loki `grafana/loki:3.6.11`
  - Promtail `grafana/promtail:3.5.1`
  - Tempo `grafana/tempo:2.9.0`
  - OpenTelemetry Collector `otel/opentelemetry-collector-contrib:0.156.0`
- Helm NetworkPolicy 增加 `flow-telemetry-egress`。开启 Trace 时仅允许 server
  访问观测 namespace 或显式 Collector CIDR 的 OTLP `4317/4318` 端口。
- 本地 k3s values 将监控抓取和 OTLP 导出 namespace 明确指向 `flow-observability`。
- Promtail 本地轻量配置已修复：
  - 补齐 `namespaces` RBAC。
  - 显式限定发现 `flow-hardening` namespace。
  - 注入 `HOSTNAME=spec.nodeName`，避免把 Promtail Pod 名误当节点名。
  - 使用 k3s/containerd 的真实 `/var/log/pods/<namespace>_<pod>_<uid>/<container>/*.log`
    路径。
- 增加 `verify-lite-observability.sh`，断言 Prometheus、Loki、Tempo、告警均有真实数据。
- 增加 `fault-test-lite-observability.sh`，逐个验证观测组件故障不影响业务健康请求。

## 已验证

- `mvn -pl workflow-core,workflow-admin,workflow-open-api,workflow-app -am test -DskipITs`
  - 结果：通过
  - 覆盖：16 个 Maven 模块，641 个测试，1 个跳过
- `mvn -pl workflow-admin -am -Dtest=AuditTraceFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：通过
- `mvn -pl workflow-open-api -am -Dtest=OpenApiRequestGuardFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：通过
- `deploy/scripts/validate-manifests.sh`
  - 结果：通过
  - 覆盖：生产、本地、Open API、Webhook、Connector、PrometheusRule、
    ServiceMonitor、Prometheus/Grafana、Loki、Promtail、Tempo、OTel Collector、
    SkyWalking Helm 渲染和 kubeconform 校验
- `git diff --check`
  - 结果：通过
- `docker build -t flow-hardening/server:observability-cf12824 workflow-server`
  - 结果：通过
  - 镜像：`flow-hardening/server:observability-cf12824`
- `k3d image import flow-hardening/server:observability-cf12824 -c dev`
  - 结果：通过
  - 说明：仅导入业务镜像到 k3d containerd，未重启 Docker 或 k3s。
- `helm upgrade flow-local deploy/helm/flow --namespace flow-hardening ... --wait`
  - 结果：通过
  - 业务镜像：`flow-hardening/server:observability-cf12824`
  - 本地关闭 Prometheus Operator CRD 依赖：
    `monitoring.serviceMonitor.enabled=false`，
    `monitoring.prometheusRule.enabled=false`
- `deploy/observability/verify-lite-observability.sh`
  - 结果：通过
  - 覆盖：
    - Prometheus `up{job="flow-server"} == 1`
    - JVM 指标 `jvm_memory_used_bytes` 非零
    - 受控测试告警 `FlowControlledTestAlert` firing
    - Loki `flow-hardening` namespace 日志可查询
    - Tempo `/ready` 正常，`/api/search` 存在 `workflow-server` traces
- `deploy/observability/fault-test-lite-observability.sh`
  - 结果：通过
  - 覆盖组件：
    - `flow-otel-collector`
    - `flow-prometheus`
    - `flow-loki`
    - `flow-tempo`
    - `flow-grafana`
  - 每项检查：
    - 故障期间 `flow-server`、`flow-web`、`flow-schema-worker` 均 Available
    - `flow-web` 到 `flow-server /healthz` 请求成功
    - 组件恢复后业务请求仍成功

## 本机 k3s 实测

当前本机 k3s 资源不足以安全承载完整观测栈，但轻量观测栈已经通过真实部署和故障隔离验收。

证据：

- `kubectl top node` 显示节点内存约 `68%`。
- 第一次安装 kube-prometheus-stack、Loki、Promtail、Tempo 和 OTel Collector 期间，
  k3s 节点出现 `NodeNotReady`，业务 `server`、`web` 和 `minio` 发生探针超时或重启。
- 第二次改为 lite 配置，仅安装 kube-prometheus-stack 和 Loki 后，业务
  `flow-local-flow-server` 仍短暂降至不可用。
- 卸载 `flow-observability` namespace 后，业务 Deployment 恢复：
  `flow-local-flow-server`、`flow-local-flow-web`、`flow-local-flow-schema-worker`
  均回到 `Available`。

处理：

- 已卸载完整 Helm 观测栈，避免重型组件继续影响业务。
- `install-local-observability.sh` 已增加资源预检。默认节点内存超过 `55%` 或
  CPU 超过 `70%` 时拒绝安装，除非显式设置 `OBSERVABILITY_FORCE_INSTALL=true`。
- 已安装本地轻量观测栈，节点内存约 `70%~71%` 时业务仍保持 Ready。
- 轻量栈恢复后状态：
  - `flow-local-flow-server`：`2/2`
  - `flow-local-flow-web`：`2/2`
  - `flow-local-flow-schema-worker`：`2/2`
  - `flow-prometheus`、`flow-loki`、`flow-tempo`、`flow-otel-collector`、`flow-grafana`：`1/1`
  - `flow-promtail`：`1/1`

已确认的问题：

- 本地历史 MinIO Deployment 不是当前 Helm 或 k3s prerequisite 管理对象，存在明文账号、
  无 resources、BestEffort QoS，并在节点压力期间多次 OOMKilled。当前恢复为 `1/1`，
  但它不是生产级对象，不能作为生产交付依据。

## 未完成

- 本机 k3s 完整观测栈稳定部署；当前以轻量栈完成核心能力验收。
- 业务登录和核心前端流程重新验收。
- Grafana 页面级人工验收截图。
- 120 分钟稳定性观察。
- SkyWalking 本地安装和故障隔离验证。
- 依赖、镜像、Secret、Kubernetes 安全扫描的最终复跑和问题闭环。

## 当前结论

应用侧 Trace/指标/日志关联和本地轻量可观测后端已经打通。Prometheus、Loki、Tempo、
Grafana 和 OTel Collector 任一组件单独不可用时，业务 Deployment 和健康请求不受影响。

完整 kube-prometheus-stack、SkyWalking 等生产级重型组件仍不适合当前本机 k3s 资源余量。
后续要完成最终 Goal，还需要补齐业务登录/核心流程、最终安全扫描和至少 120 分钟稳定性观察。
