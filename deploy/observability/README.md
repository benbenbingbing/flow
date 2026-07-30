# Flow 可观测性部署说明

本目录维护 Flow 的可观测性部署配置、故障演练脚本和本地验收脚本。当前仓库同时保留两套配置：

- `lite/`：本地 k3s 验收使用的轻量栈，资源占用较低，包含 Prometheus、Grafana、Loki、Tempo、Promtail 和 OpenTelemetry Collector。
- Helm values：生产或资源充足环境使用的 chart values，包含 kube-prometheus-stack、Loki、Tempo、Promtail、OpenTelemetry Collector 和可选 SkyWalking。

可观测平台必须保持旁路关系。Prometheus、Grafana、Loki、Tempo、SkyWalking、OpenTelemetry Collector 或日志采集器故障时，业务 Pod 仍应正常启动、通过 readiness，并继续处理核心请求。遥测数据允许短时间丢弃，不允许反向阻塞业务链路。

## 固定版本

轻量栈直接使用固定镜像：

| 组件 | 镜像 |
| --- | --- |
| Prometheus | `prom/prometheus:v3.13.1` |
| Grafana | `grafana/grafana:12.3.1` |
| Loki | `grafana/loki:3.6.11` |
| Tempo | `grafana/tempo:2.9.0` |
| Promtail | `grafana/promtail:3.5.1` |
| OpenTelemetry Collector | `otel/opentelemetry-collector-contrib:0.156.0` |

完整 Helm 栈版本维护在 `versions.env`。生产变更时只调整固定版本，不使用 `latest`。

## Namespace 与服务

默认 namespace：

- 业务：`flow-hardening`
- 可观测：`flow-observability`
- 本地 Helm release：`flow-local`

主要服务：

| 服务 | 端口 | 用途 |
| --- | ---: | --- |
| `flow-prometheus` | 9090 | 指标查询和告警规则 |
| `flow-grafana` | 3000 | 仪表盘 |
| `flow-loki` | 3100 | 日志查询 |
| `flow-tempo` | 3200 / 4317 / 4318 | Trace 查询与 OTLP 接收 |
| `flow-otel-collector` | 4317 / 4318 / 8888 / 13133 | OTLP 接收、metrics、health |

## 本地安装

轻量栈：

```sh
deploy/observability/install-lite-observability.sh
deploy/observability/verify-lite-observability.sh
```

安装脚本在应用 Grafana provisioning 配置后会滚动 `flow-grafana`。这是为了确保 datasource 和 dashboard 配置进入容器；Grafana 重启不应影响业务 Deployment。

完整 Helm 栈：

```sh
deploy/observability/install-local-observability.sh
deploy/observability/verify-local-observability.sh
```

本地单节点 k3s 资源有限，默认建议使用轻量栈完成业务联调、故障矩阵和稳定性观察。完整栈适合在资源充足的预发或生产同规格环境复跑。

## 访问与凭据

Grafana 本地访问：

```sh
kubectl -n flow-observability port-forward svc/flow-grafana 3000:3000
```

账号：

```text
admin
```

密码通过 Kubernetes Secret 获取，不提交到仓库：

```sh
kubectl -n flow-observability get secret flow-grafana-admin \
  -o jsonpath='{.data.admin-password}' | base64 -d
```

Prometheus 本地访问：

```sh
kubectl -n flow-observability port-forward svc/flow-prometheus 9090:9090
```

Tempo 和 Loki 通常通过 Grafana 查询。需要直接排查时可临时 port-forward 对应服务。

启用 SkyWalking OTLP Trace 后，链路会按 Zipkin Trace 存储。使用 SkyWalking UI
的 `/zipkin/` 页面查询，不应使用原生 Trace GraphQL 页面判断是否有数据：

```sh
kubectl -n flow-observability port-forward \
  svc/flow-skywalking-skywalking-helm-ui 8080:80
# 浏览器访问 http://127.0.0.1:8080/zipkin/
```

## 验收脚本

基础联通：

```sh
deploy/observability/verify-lite-observability.sh
```

完整 Helm 栈（安装 SkyWalking 时会同时验证 Zipkin UI、服务和 Trace）：

```sh
deploy/observability/verify-local-observability.sh
```

自动化故障矩阵：

```sh
deploy/observability/run-fault-matrix.sh
```

120 分钟稳定性观察：

```sh
OBSERVABILITY_OBSERVE_SECONDS=7200 \
OBSERVABILITY_OBSERVE_INTERVAL_SECONDS=60 \
OBSERVABILITY_OBSERVE_RESULT_FILE=/tmp/flow-observability-120m.jsonl \
deploy/observability/observe-lite-observability.sh
```

观察脚本每分钟记录业务健康、Deployment 可用性、Pod 重启次数、HTTP 错误率、P50/P95/P99、JVM 内存、容器工作集相对内存限额的比例、MySQL 连接和 InnoDB 等待、OTel Collector 队列和失败计数、Prometheus 规则/target 同步错误、exemplar storage 和关键组件磁盘水位。业务或观测容器的内存比例超过 `OBSERVABILITY_MAX_MEMORY_LIMIT_RATIO`（默认 `0.90`）、数据库连接超过上限的 80%、行锁等待超过每分钟 60 次或出现 InnoDB 日志等待时验收失败。最终结果以 JSONL 汇总为准。

## 卸载

轻量栈：

```sh
deploy/observability/uninstall-lite-observability.sh
```

完整 Helm 栈：

```sh
deploy/observability/uninstall-local-observability.sh
```

卸载只删除可观测 namespace，不处理业务 namespace。

## 生产接入检查项

生产上线前需要补齐以下环境信息：

- 域名、TLS、Ingress、NetworkPolicy 和访问控制。
- 持久化存储等级、容量、保留周期和备份策略。
- Grafana 登录方式、团队权限、审计和告警通知渠道。
- Prometheus、Loki、Tempo 和 SkyWalking 的容量模型。
- OTel Collector 副本数、队列容量、丢弃策略和资源限制。
- 告警路由、值班人、升级策略和演练记录。
- 生产 Secret 来源，禁止在 values、镜像或 Git 中出现明文凭据。

如果启用 SkyWalking，必须以独立组件接入，不得作为业务 Pod 的 init container、sidecar 强依赖或 startup/readiness 依赖。

## 已知边界

- 本地轻量栈用于验证 Flow 的观测模型和故障隔离，不等同于生产容量结论。
- 可观测组件不可用时，业务仍必须优先可用；Trace、日志或指标丢失应通过告警和容量治理处理，不能反向影响业务请求。
- 数据库迁移、业务回滚和备份恢复不由本目录脚本处理，相关流程以 `deploy/runbooks/` 为准。
