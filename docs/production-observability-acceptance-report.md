# Flow 生产可观测性验收记录

> 分支：`main`
> 记录时间：2026-07-30
> 状态：核心链路、故障隔离、业务压测与静态门禁已通过本地 k3s 验收；120 分钟观察按验收方要求提前结束，不作为通过证据。

## 本次修复范围

- 应用日志、指标、Trace 关联链路已经打通：
  - 统一 `X-Trace-Id`、`X-Request-Id`、`business_trace_id`。
  - Actuator 指标带 `service`、`environment`、`version` 公共标签。
  - HTTP 指标使用 SLO 固定桶，避免默认高成本桶。
  - OTLP Trace 导出可开关、可配置超时，业务探针不依赖 Collector。
- Helm 和本地 k3s 部署配置补齐：
  - 本地 server 镜像更新为 `flow-hardening/server:observability-local`。
  - 本地 CORS 显式允许 `127.0.0.1:18081`、`127.0.0.1:18083`、`127.0.0.1:3000`、`localhost:3000`。
  - 本地 MinIO 改为声明式 prerequisite，账号从 `flow-local-secrets` 注入，补齐 probes、resources 和 security context。
  - 本地 server 资源调整为 request `250m/768Mi`、limit `2 CPU/2Gi`；schema-worker 保持 request `128Mi`、limit `512Mi`。本地双副本关闭 HPA，并使用 `maxUnavailable: 1`、`maxSurge: 0`，避免单节点滚动更新时瞬时资源超配。
  - 本地 MySQL 将 buffer pool 调整为 `256M`，最大连接数限制为 80，资源调整为 request `250m/512Mi`、limit `2 CPU/1536Mi`。混合读写压测表明原 `768Mi` 限制会在本机资源竞争下触发 OOM。
- 轻量可观测栈加固：
  - Prometheus、Loki、Tempo、Grafana 增加只读根文件系统所需的 `/tmp` emptyDir。
  - Promtail 显式设置运行用户和 `RuntimeDefault` seccomp。
  - Prometheus/Promtail RBAC 移除不必要的 `nodes/proxy`。
  - Prometheus 开启 exemplar storage，Grafana Prometheus datasource 配置 `trace_id` 到 Tempo 的跳转。
  - Loki datasource 配置 Trace derived field，Tempo datasource 配置 traces-to-logs，支持按 trace id 回查日志。
  - 稳定性预跑期间发现 Tempo 在 compaction 时触发 `OOMKilled`。已将轻量栈 Tempo 调整为 request `250m/512Mi`、limit `2 CPU/2Gi`，设置 `GOMEMLIMIT=1536MiB`、`GOGC=75`；liveness probe 从 `/ready` 改为 `/status/version`，避免将短暂不可查询误判为进程失活。
  - Grafana 使用 `subPath` 挂载 provisioning 文件，ConfigMap 更新不会自动进入已运行容器；`install-lite-observability.sh` 已在应用 Grafana 清单后显式滚动 `flow-grafana`，确保 datasource 和 dashboard 配置真实加载。
- 验收脚本加固：
  - `validate-manifests.sh` 支持临时代理转发到 Docker 容器，自动把 `127.0.0.1/localhost` 代理改写为 `host.docker.internal`，不写入仓库固定代理。
  - kubeconform 增加 schema 缓存、串行下载和重试，降低 GitHub raw schema 偶发 `unexpected EOF` 对本地/CI 的影响。
  - `run-fault-matrix.sh` 改为 CSV 结果，逐项恢复组件，覆盖 OTLP 拒绝连接、超时和 HTTP 错误，并给 kubectl 调用增加 request timeout。
  - `observe-lite-observability.sh` 补齐 Deployment selector 解析、磁盘水位、OTel Collector 队列/失败计数、Prometheus rule/target 同步错误、exemplar storage 断言，以及 Prometheus、Loki、Tempo、OTel Collector、Grafana 全部观测组件的 restart 稳定断言。
  - UI 验收脚本改为真实调用 `/api/auth/login` 和 `/api/auth/permissions` 后注入当前会话，再检查核心页面，避免依赖脆弱的模拟点击。
- 后端异常处理：
  - `AsyncRequestNotUsableException` 单独处理为 204，避免客户端断开或 Prometheus 抓取中断时被全局异常包装成 JSON，污染 OpenMetrics 响应。
- 用户组列表性能：
  - 列表查询不再逐组加载完整用户对象，改为每批 500 个用户组批量查询成员 ID。
  - 1200 个用户组的单元测试验证仅执行 3 次成员关系查询，并保持列表页需要的 `userIds` 返回契约。
- 主线同步后的生产门禁修复：
  - 开放集成管理接口统一为 GET/POST 契约，前后端路由同步，避免浏览器端与服务端方法不一致。
  - 新增的 UI 事件、实体版本和变更目录接口补齐登录、对象级授权和管理权限；自定义写按钮默认至少要求实体更新权限，标准按钮继续校验对应的新增、删除、导出或审批权限。
  - 移除未被引用且绕过实体变更端口的旧运行时服务，保留架构边界门禁。
  - 生产 Compose 恢复独立迁移、schema worker、bootstrap、最小权限数据库账号和 S3 存储；生产部署恢复不可变镜像摘要、漏洞扫描、SBOM、Helm atomic 回滚与部署后测试。
  - 手动生产部署强制限定 `main`，SSH 主机指纹必须由 Secret 提供，不再使用代码内默认主机或账号。
  - 前端大文件按既有模块边界拆出验证规则和列表配置纯函数，并补齐单元测试和跨模块页面审计。

## 本地 k3s 部署结果

- Helm release：`flow-local`，namespace：`flow-hardening`，revision：`30`。
- 业务组件：
  - `flow-local-flow-server`：`2/2`，镜像 `flow-hardening/server:observability-local`。
  - `flow-local-flow-web`：`2/2`。
  - `flow-local-flow-schema-worker`：`2/2`。
  - `local-mysql`、`minio`：均 `1/1`。
- 轻量观测组件：
  - `flow-prometheus`、`flow-loki`、`flow-tempo`、`flow-otel-collector`、`flow-grafana`、`flow-promtail` 均 Ready。
- 说明：
  - server、schema-worker、MySQL 当前存在压测和资源调优阶段留下的历史重启计数。MySQL 扩容后的最终业务压测期间，MySQL 和 server 重启计数均未增长。
  - namespace 中仍有历史手工 Pod `mysql` 处于 OOMKilled，非当前 Helm/prerequisite 管理对象，未纳入本次交付。
  - 本机 k3s 是单节点资源受限环境，不适合同时承载完整 kube-prometheus-stack、SkyWalking 和业务双副本的长期压力观察。

## 已通过的验收

### 后端

- `mvn -B verify`
  - 结果：通过
  - 覆盖：17 个 Maven 模块，944 个测试（Surefire 943、Failsafe 1），0 失败、0 错误、1 个跳过；包含 MySQL 8.4 Testcontainers 上的 17 个 Flyway 迁移版本和生产制品打包验证。
- `mvn -B -pl workflow-app -am -Dtest=UiEventRuntimeServiceTest,ProductionArtifactSecurityTest,ApiAccessPolicyCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：通过，11 个定向测试，0 失败。
  - 覆盖：UI 写事件权限降级保护、API 访问策略覆盖、生产部署与制品安全约束。
- `mvn -B -pl workflow-app -am -Dtest=GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：通过
  - 覆盖：`AsyncRequestNotUsableException` 不再破坏指标响应。

### 前端

- `npm --prefix workflow-web run test`
  - 结果：通过。
- `npm --prefix workflow-web run build`
  - 结果：通过。
- `npm --prefix workflow-web run test:observability-ui:real`
  - 结果：通过。
  - 入口：`http://127.0.0.1:18083`，通过 `svc/flow-local-flow-web` port-forward。
  - 覆盖：管理员真实登录、权限拉取、`/home`、`/entity`、`/process`、`/system/user`、`/system/audit-logs`。
  - 截图：`workflow-web/.codex-artifacts/production-observability/ui/`，该目录被 git 忽略。

### Kubernetes 与部署清单

- `deploy/scripts/validate-manifests.sh`
  - 结果：通过。
  - 覆盖：生产、本地、Open API、Webhook、Connector、监控对象、完整观测 Helm chart、轻量观测 YAML 的 Helm lint、Helm template 和 kubeconform strict 校验。
  - 代理验证：在大小写代理变量不一致时仍优先使用显式 `HTTP_PROXY/HTTPS_PROXY`，容器内自动转换为 Docker 可访问的本机代理地址；不提交固定代理端口。
- `rhysd/actionlint:1.7.7`
  - 结果：通过，全部 GitHub Actions 工作流语法和表达式检查无错误。
- `helm upgrade --install flow-local deploy/helm/flow --namespace flow-hardening --values deploy/k3s/values.yaml --wait --timeout 10m`
  - 结果：通过。
- `helm test flow-local --namespace flow-hardening --timeout 5m`
  - 结果：通过。
- `deploy/observability/verify-lite-observability.sh`
  - 结果：通过。
  - 覆盖：Prometheus `up` 和 JVM 指标、受控告警、Loki 日志查询、Tempo ready/search。
- Prometheus exemplar storage
  - 结果：通过。
  - 覆盖：Prometheus 启动参数包含 `--enable-feature=exemplar-storage`，`prometheus_tsdb_exemplar_max_exemplars` 大于 0，`query_exemplars` 可返回带 `trace_id`/`span_id` 的 HTTP 指标 exemplar。
- Grafana API 配置验收
  - 结果：通过。
  - 覆盖：Grafana 已加载 `prometheus`、`loki`、`tempo` 三个 datasource；Prometheus datasource 包含 exemplar trace 跳转；Loki datasource 包含 Trace derived field；Tempo datasource 启用 `tracesToLogsV2.filterByTraceID`；`flow-observability-lite` dashboard 已加载。

### 故障隔离

- `deploy/observability/run-fault-matrix.sh`
  - 结果：通过，结果文件：`/tmp/flow-observability-fault-matrix.csv`。
  - 覆盖：
    - `flow-otel-collector` down/recover。
    - `flow-prometheus` down/recover。
    - `flow-loki` down/recover。
    - `flow-tempo` down/recover。
    - `flow-grafana` down/recover。
    - SkyWalking 未安装时，按 `component_not_installed` 场景验证业务仍可用；如果后续安装 `flow-skywalking-oap`，脚本会按组件下线/恢复流程演练。
    - OTLP endpoint refused：`127.0.0.1:9`。
    - OTLP endpoint timeout：`10.255.255.1:4318`。
    - OTLP endpoint HTTP error：Prometheus `/v1/traces`。
  - 每项断言：故障期间 `flow-web` 到 `flow-server /healthz` 成功，业务 Deployment 保持可用。

### 业务压测

- 最终业务压测结果：`/tmp/flow-loadtest-business-only-after-mysql-resize.json`。
- 持续约 8 分 15 秒，共 6040 次业务请求，6036 次成功、4 次客户端超时，成功率 99.9338%。服务端 5xx 为 0。
- 总体延迟：p50 14ms、p95 79ms、p99 438ms；混合业务阶段 2343/2343 成功，短时突发阶段 2439/2439 成功。
- 覆盖当前用户、权限、用户组、字典查询，以及用户组和字典创建、状态变更；压测创建的 42 个用户组和 42 个字典已清理。
- `/healthz` 包含数据库检查，定位为 readiness/低频外部健康检查；高频存活探测应使用 `/livez`，不应把数据库健康端点当作业务压测接口。

### 安全与依赖

- GitHub Dependabot
  - 结果：已逐项处理 3 个 Jackson Databind 中危告警；后端依赖管理和独立 Java 接入示例均升级到 `2.21.5`，依赖树复核未残留 `2.21.4`。
  - 验证：升级后重新执行后端 17 模块全量测试，共 944 个测试，0 失败、0 错误、1 个跳过；独立 Java 示例编译及依赖解析通过。
- GitHub CodeQL
  - 结果：逐项修复 17 个开放告警，覆盖 1 个 GET 请求副作用、14 个日志注入、1 个正则表达式拒绝服务和 1 个测试弱密钥问题。
  - 业务处理：详情 GET 接口保持纯读取；需要执行 `DETAIL_LOAD` 事件链的操作迁移到 POST。日志字段统一清洗控制字符，保存接口不再记录完整业务数据。邮件脱敏正则消除回溯歧义，弱密钥负向测试改为模拟密钥属性，不再生成弱密钥材料。
  - 验收标准：CodeQL Java、JavaScript 扫描任务成功，仓库开放告警为 0。
- `gitleaks detect --source . --no-git --config .gitleaks.toml --redact`
  - 结果：通过，当前工作树未发现泄露；`gitleaks git` 复核 240 个提交历史同样未发现泄露。
- `npm --prefix workflow-web audit --audit-level=high`
  - 结果：通过，0 个漏洞。
  - 说明：本机默认 npm registry 指向的镜像站不支持 audit 接口；本次使用临时 `npm_config_registry=https://registry.npmjs.org/` 和本机代理重跑，未修改项目配置。
- Trivy filesystem scan
  - 结果：通过，19 个 Maven/npm 依赖清单的 HIGH/CRITICAL 漏洞为 0，20 个 Helm、Kubernetes 和 Dockerfile 配置的 HIGH/CRITICAL 错误配置为 0，未发现敏感信息。
  - 说明：固定使用 `aquasec/trivy:0.72.0` 和 `values.security-scan.yaml`。在线解析遇到 Maven Central 429 后，使用全量 Maven 测试已经填充的只读本机依赖缓存执行离线复扫；漏洞数据库仍为本次下载的最新数据库，不修改项目依赖或仓库配置。
- Trivy image scan
  - 结果：此前 server/web 镜像 HIGH/CRITICAL 均为空。
  - 说明：生产 Actions 会对本次构建并推送的不可变镜像摘要再次执行 HIGH/CRITICAL 门禁，并保存 CycloneDX SBOM。

## 限制与后续生产验收

- 120 分钟稳定性观察已启动，但按验收方要求提前终止，因此不宣称长稳通过。仓库已提供采集业务健康、Deployment/StatefulSet 可用性、HTTP 错误率与延迟、JVM 内存、OTel 队列与失败计数、Prometheus 规则/target 错误、exemplar、重启次数和磁盘水位的脚本，可在生产同规格预发环境复跑。
- SkyWalking 未在本机安装。当前轻量栈已覆盖 Prometheus、Loki、Tempo、Grafana、OTel Collector 的核心闭环。
- 完整 kube-prometheus-stack 未作为本机最终交付。当前仓库已补齐完整栈 values 的 exemplar 和 trace/log 关联配置，并通过 Helm 渲染；本机最终验收仍以轻量栈为准，避免在资源受限单节点上把业务稳定性和重型观测栈压力混在一起判断。
- 本机 k3s 是单节点开发环境，验证结果证明功能闭环和故障解耦，不等价于生产容量结论。生产发布仍需使用实际存储、网络和资源配额完成容量基线与长稳验收。

## 结论

本次修复后的核心生产可观测链路已经可用：指标、日志、Trace、Grafana 数据源、业务登录/UI、Kubernetes 清单、观测组件故障隔离、业务压测和主要安全扫描均已通过本地 k3s 验证。观测后端不可用时不阻断业务请求的目标已由 14 项故障矩阵验证。

对生产环境仍建议保留两条硬要求：

1. 在资源充足的预发或生产同规格环境跑 120 分钟以上稳定性观察。
2. 如果要启用 SkyWalking 或完整 kube-prometheus-stack，应先完成容量评估和独立故障演练，不能直接按本机 k3s 结果外推。
