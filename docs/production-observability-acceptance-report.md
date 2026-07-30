# Flow 生产可观测性验收记录

> 分支：`feature/production-observability`
> 记录时间：2026-07-30 08:10 CST
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

## 本机 k3s 实测

当前本机 k3s 资源不足以安全承载完整观测栈。

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

- 已卸载本次安装的观测栈，避免观测系统继续影响业务。
- `install-local-observability.sh` 已增加资源预检。默认节点内存超过 `55%` 或
  CPU 超过 `70%` 时拒绝安装，除非显式设置 `OBSERVABILITY_FORCE_INSTALL=true`。

## 未完成

- 本机 k3s 完整观测栈稳定部署。
- 业务新镜像构建并使用本分支代码重新部署。
- 业务登录和核心流程重新验收。
- Prometheus 非零业务指标、Grafana 查询、Loki 日志检索和 Tempo Trace 真实数据验收。
- 告警规则受控触发。
- 自动化故障矩阵。
- 120 分钟稳定性观察。
- SkyWalking 本地安装和故障隔离验证。

## 当前结论

应用和配置层面的生产隔离能力已经开始落地，但本机 k3s 当前资源余量不足，不能把
完整可观测性后端作为验收环境直接安装。下一步应先释放本机 k3s 资源，或提供更大的
测试集群，再继续完整部署、故障矩阵和 120 分钟稳定性观察。
