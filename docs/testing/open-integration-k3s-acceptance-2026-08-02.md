# 外部流程接入 k3s 验收记录（2026-08-02）

## 环境

- Kubernetes context：`k3d-dev`
- 隔离命名空间：`flow-open-integration`
- Flow 镜像：`flow-hardening/server:external-p1-ef9d779`
- 数据库和对象存储：复用本地 `flow-hardening` 测试服务，仅用于本次验收
- 参考接收端：`flow-reference-external:local`，仅用于模拟外部系统

本次未修改 DevOps 代码，也未修改生产部署值文件。隔离 Helm release 使用 Open API、
Webhook worker、固定两副本，并关闭 HPA，避免单节点 k3s 的资源波动改变验收结果。

## 已执行结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `helm test flow-open` | 通过 | Helm test job `Completed` |
| 正常外部接入 | 通过 | Client Credentials、场景启动、revision、查询、任务分页、幂等重放、四路并发、幂等取消均通过 |
| 身份缺失 | 通过 | 未提供身份变量时返回 422 |
| 参考系统契约 | 通过 | Token、请求幂等、HMAC 签名和事件去重通过 |
| 参考端停止 | 通过 | 参考 Pod 缩容为 0 时 Flow 核心接口仍通过；恢复后接收端恢复 |
| Flow 双副本滚动重启 | 通过 | rollout 完成，重启后重新建立端口转发，核心接口再次通过 |
| 场景 revision 切换 | 通过 | 第二场景发布后返回 revision 2，备用场景返回 revision 1 |
| 令牌 Secret 轮换 | 通过 | 旧凭据轮换后 401，新凭据 200；无效/过期令牌 401 |
| 数据库连接中断恢复 | 通过 | 临时移除 `local-mysql` Service 端点并缩容/拉起 Flow：健康检查为非 200；恢复端点后两副本 Ready，健康检查 200 |

数据库中断使用 Service 端点切换完成，避免本地 MySQL 的临时 `emptyDir` 在 Pod 重建时
丢失测试数据。曾验证到该测试限制：直接重启 `local-mysql` 会清空临时数据并导致应用
凭据用户不存在；随后重新创建测试用户并执行 v023 迁移，Flow 恢复正常。这是测试装置的
存储限制，不代表生产部署可以使用 `emptyDir`。

## 仍需在目标集群执行

1. 使用真实 HTTPS 外部接收端完成 Flow Webhook 的注册、投递、重试和签名验收；本地
   参考接收端使用 HTTP，管理 API 按设计拒绝非 HTTPS 地址（返回 400）。
2. 在具备持久化数据库卷、真实 Ingress、TLS、Prometheus、Loki、Tempo/SkyWalking
   的目标 k3s/生产类集群执行长稳压测和数据库恢复演练。
3. 将本记录中的脚本 JSON、Pod、HTTP 指标、连接池、导出失败计数和审计日志归档到
   发布证据目录。

