# Flow 生产可观测性验收记录

> 分支：`feature/production-observability`
> 记录时间：2026-07-30 09:31 CST
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
- 增加 `observe-lite-observability.sh`，用于最终 120 分钟稳定性观察，按 JSONL 记录：
  - 业务健康请求结果和耗时。
  - 业务 Deployment 可用性与 Pod 重启数。
  - 可观测组件可用性与关键组件重启数。
  - Prometheus `up`、请求量、5xx、P50/P95/P99、JVM 内存。
  - `kubectl top pod` 的业务和可观测 Pod CPU/内存快照。

## 已验证

- `mvn -pl workflow-core,workflow-admin,workflow-open-api,workflow-app -am test -DskipITs`
  - 结果：通过
  - 覆盖：16 个 Maven 模块，641 个测试，1 个跳过
  - 最近一次复跑时间：2026-07-30 08:55 CST
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
- `gitleaks git --config .gitleaks.toml --redact`
  - 结果：通过
  - 覆盖：222 个提交，约 64.49 MB 内容，未发现泄漏
- `npm audit --audit-level=high`
  - 结果：通过
  - 覆盖：`workflow-web`
- `npm test`
  - 结果：通过
  - 覆盖：前端单元、集成、功能、页面配置、UI 配置、术语和可维护性预算
- `npm run build`
  - 结果：通过
  - 覆盖：前端 Vite 生产构建
- 本地工具可用性：
  - 已运行：`gitleaks`、`npm`、`mvn`
  - 本机未安装：`trivy`、`syft`、`grype`、`shellcheck`、`actionlint`
  - 对应严格检查仍需要通过 GitHub Actions 或安装固定版本工具后复跑
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
  - 最近一次复跑时间：2026-07-30 09:20 CST
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
- 业务 API 回归，访问入口：`http://127.0.0.1:18081/api`
  - 结果：通过
  - 账号：`admin`
  - 密码：来自 `flow-hardening/flow-local-secrets` 的
    `bootstrap-admin-password`，未写入验收记录或仓库文件。
  - 覆盖接口：
    - `POST /auth/login`：HTTP 200，业务码 200，返回管理员用户和 1 个角色。
    - `GET /auth/current`：HTTP 200，业务码 200，当前用户为 `admin`。
    - `GET /auth/permissions`：HTTP 200，业务码 200，返回 40 个权限码。
    - `GET /system/menu/tree`：HTTP 200，业务码 200，返回 30 个菜单节点。
    - `GET /entity?pageNum=1&pageSize=10`：HTTP 200，业务码 200，返回 10 条实体记录，
      总数 10。
    - `GET /process?pageNum=1&pageSize=10`：HTTP 200，业务码 200，当前本地数据为 0 条流程。
    - `GET /system/user/page?pageNum=1&pageSize=10`：HTTP 200，业务码 200，返回 1 个用户。
    - `GET /system/audit-logs?pageNum=1&pageSize=10`：HTTP 200，业务码 200，返回 10 条审计记录，
      总数 60。
- 内置浏览器未登录路由保护回归
  - 结果：通过
  - 覆盖：直接访问 `/home`、`/entity`、`/process`、`/system/user`、
    `/system/audit-logs` 时，前端均回到 `/login`。
  - 说明：未登录场景下控制台出现菜单和统计接口 401，符合路由保护预期，不作为业务页面通过证据。
- Grafana API 与仪表盘查询回归，访问入口：`http://127.0.0.1:13000`
  - 结果：通过
  - 凭据：来自 `flow-observability/flow-grafana-admin` Secret，未写入验收记录或仓库文件。
  - 覆盖：
    - `GET /api/health`：数据库状态 `ok`，Grafana 版本 `12.3.1`。
    - `GET /api/datasources`：存在 `prometheus`、`loki`、`tempo` 三个 datasource。
    - `GET /api/search?query=Flow Production Observability`：返回 1 个仪表盘。
    - `GET /api/dashboards/uid/flow-observability-lite`：标题为
      `Flow Production Observability`，包含 5 个面板。
    - `POST /api/ds/query` 通过 Grafana Prometheus datasource 查询
      `up{job="flow-server"}`，返回 1 个数据帧。
- `OBSERVABILITY_OBSERVE_SECONDS=5 OBSERVABILITY_OBSERVE_INTERVAL_SECONDS=5
  OBSERVABILITY_OBSERVE_RESULT_FILE=/tmp/flow-observability-smoke.jsonl
  deploy/observability/observe-lite-observability.sh`
  - 结果：通过
  - 覆盖：2 个采样点，业务健康请求均成功，业务和可观测组件均 Available，
    `flow_up=1`，5 分钟 5xx 为 0，采集到 P50/P95/P99、请求量、JVM 内存和 Pod 资源。
  - 说明：这是脚本 smoke，不替代最终 120 分钟稳定性观察。

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
- 轻量栈 2026-07-30 09:20 CST 状态：
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
- 业务 API 登录和核心接口已经通过；内置浏览器登录后的核心页面覆盖仍需补齐。
- Grafana API、datasource 和 dashboard 查询已通过；页面级截图仍需补齐。
- 120 分钟稳定性观察。
- SkyWalking 本地安装和故障隔离验证。
- 镜像漏洞、Kubernetes misconfig、ShellCheck 和 Actionlint 的最终复跑。
  当前本机缺少 `trivy`、`shellcheck`、`actionlint`，仓库 Actions 已覆盖这些检查。

## 当前结论

应用侧 Trace/指标/日志关联和本地轻量可观测后端已经打通。Prometheus、Loki、Tempo、
Grafana 和 OTel Collector 任一组件单独不可用时，业务 Deployment 和健康请求不受影响。

完整 kube-prometheus-stack、SkyWalking 等生产级重型组件仍不适合当前本机 k3s 资源余量。
后续要完成最终 Goal，还需要补齐业务登录/核心流程、最终安全扫描和至少 120 分钟稳定性观察。
