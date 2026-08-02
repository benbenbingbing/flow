# 外部流程通用能力完成定义审计

## 范围

- 仓库：Flow；功能分支：`feature/external-workflow-integration`
- DevOps 目录仅用于只读理解，没有修改、分支、提交或运行依赖。
- 本记录只判断通用外部流程能力，不把客户系统名称或客户领域逻辑写入 Flow。

## WP0-WP7 证据

| 批次 | 当前结论 | 主要证据 |
| --- | --- | --- |
| WP0 可靠基线 | 通过 | `mvn -B -ntp verify`；`npm test`；`npm run build`；生产 CORS 和架构门禁测试 |
| WP1 产品契约 | 通过 | ADR-0005、`docs/api/openapi-v1.yaml`、事件目录；`OpenIntegrationContractTest` 在 push/PR 工作流执行 |
| WP2 场景配置 | 通过 | Integration Application 下的 scenario 草稿、校验、发布、停用、不可变 revision；前端 `IntegrationScenarioPanel` |
| WP3 通用运行接口 | 通过 | scenarioKey 解析、binding 快照、业务引用版本、输入摘要、幂等启动/查询/任务/取消；旧 processKey 接口保留 |
| WP4 结果与事件 | 通过 | CloudEvents V1、事务 Outbox、lease/fencing、指数退避、死信、人工重放、状态与 outcomeCode 分离 |
| WP5 安全与身份 | 通过 | Client Credentials、最小 scope、应用授权、HMAC keyId/时间窗/重放防护、Resolver SPI、未解析身份 422、敏感日志门禁 |
| WP6 双 Pod 与可观测性 | 通过 | 双副本幂等/lease 测试；Prometheus/Loki/Tempo/OTel 故障矩阵；业务健康不依赖观测后端 |
| WP7 独立验收 | 通过 | 隔离 k3s HTTPS reference receiver；start/query/cancel、签名、重复/乱序、Receiver 故障重试、死信重放、Flow 重启、DB 连接中断、Secret 轮换、Token 过期、revision 切换、旧 Open API |

## 本轮补充

- Reference receiver 支持可选 HTTPS；证书和私钥必须成对配置。
- 内部 HTTPS 目标需要同时满足 `httpAllowedHosts` 和显式
  `httpAllowPrivateAddresses=true`，默认仍关闭。
- 参考接收端被 import 时不再自动监听端口，避免测试进程泄漏；直接执行入口行为不变。
- 观测故障矩阵将未安装的可选 SkyWalking 记录为 `skipped/component_not_installed`，不误报为业务失败。
- 隔离验收使用临时测试 CA/truststore；没有任何私钥、Token 或测试凭据写入仓库。

## 验证结果

- 本地：后端全量测试、前端全量测试与生产构建、Helm lint/template/kubeconform、Node/Shell 语法、参考接收端自测通过。
- GitHub Actions：开放集成契约工作流已通过；完整 CI 必须以当前最终提交对应的 run 为准，包含后端、前端、清单、镜像漏洞和 SBOM 门禁。
- k3s：隔离 namespace 已清理；测试过程中使用的 Flow/receiver/数据库均为 test-only。原开发集群当前受其他 namespace 的 CrashLoop/Pending 工作负载影响，Flow server 期望 2 副本但暂时无法调度；这不是代码验收失败，生产发布不得使用该资源状态。

## 目标集群剩余风险

1. 生产仍需使用真实 CA、Ingress、NetworkPolicy、持久化数据库和真实资源配额重跑验收。
2. 120 分钟以上长稳和容量基线不由本地单节点 k3s 结果替代；SkyWalking/完整观测栈需单独做容量与故障演练。
3. 发布前以功能分支最终提交对应的 CI、镜像摘要、SBOM 和目标集群验收记录作为放行依据；不得直接修改或推送 `main`。
