# GitHub Actions 生产发布

当前发布链路以 `.github/workflows/ci.yml` 和 `.github/workflows/deploy.yml` 为准：

```text
push main
  -> CI: test, audit, manifest validation, image scan, SBOM
  -> build server/web images
  -> push immutable images to GHCR
  -> upload Helm Chart to the deployment host over SSH
  -> run Helm upgrade against the target Kubernetes cluster
  -> rollout checks and Helm smoke test
```

发布主机只是受控执行入口；业务工作负载运行在它所配置的 Kubernetes 集群中，不使用
旧版 Docker Compose 发布流程。

## 目标环境准备

部署用户所在主机需要：

- Helm 3
- kubectl
- 可访问目标 Kubernetes API 的 kubeconfig
- 访问 GHCR 镜像的集群凭据
- 可写的部署目录，默认 `/opt/flow`

创建目录：

```bash
sudo mkdir -p /opt/flow
sudo chown DEPLOY_USER:DEPLOY_USER /opt/flow
```

在该目录维护生产值文件：

```text
/opt/flow/values.production.yaml
```

值文件不能提交环境密钥。数据库、JWT、配置包签名、初始管理员和对象存储凭据应由
External Secrets、Sealed Secrets 或平台 Secret 管理器创建为 Helm
`global.existingSecret` 指向的 Kubernetes Secret。

生产值至少需要配置：

- 数据库 URL，以及独立的运行账号和结构账号
- server/web 镜像仓库占位值；工作流发布时会覆盖仓库和 digest
- S3 兼容对象存储
- Ingress、TLS、CORS 和可信代理
- NetworkPolicy 的数据库、对象存储和外部 HTTPS CIDR
- HPA、PDB、资源请求与限制
- ServiceMonitor、PrometheusRule 和告警标签

## SSH 凭据

生成专用部署密钥：

```bash
ssh-keygen -t ed25519 \
  -C "github-actions-flow-deploy" \
  -f ~/.ssh/flow_github_actions
```

将公钥加入部署用户的 `~/.ssh/authorized_keys`。私钥全文保存为 GitHub Environment
Secret `DEPLOY_SSH_KEY`。

从可信网络获取并人工核对 SSH host key：

```bash
ssh-keyscan -H DEPLOY_HOST
```

自定义端口：

```bash
ssh-keyscan -p DEPLOY_PORT -H DEPLOY_HOST
```

核对后的完整输出保存为 `DEPLOY_KNOWN_HOSTS`。工作流不会关闭 host key 校验。

## GitHub Environment

创建名为 `阿里云flow` 的 GitHub Environment。生产环境建议启用 Required reviewers。

Environment secrets：

| 名称 | 必填 | 用途 |
| --- | --- | --- |
| `DEPLOY_HOST` | 是 | SSH 主机名或地址 |
| `DEPLOY_USER` | 是 | 部署用户 |
| `DEPLOY_SSH_KEY` | 是 | ed25519 私钥全文 |
| `DEPLOY_KNOWN_HOSTS` | 是 | 已核对的 SSH host key |
| `DEPLOY_PORT` | 否 | SSH 端口，默认 `22` |

Environment 或 Repository variables：

| 名称 | 默认值 | 用途 |
| --- | --- | --- |
| `DEPLOY_PATH` | `/opt/flow` | 远端部署目录 |
| `K8S_NAMESPACE` | `flow-production` | Kubernetes Namespace |
| `HELM_RELEASE` | `flow` | Helm release 名称 |

GitHub Actions 使用内置 `GITHUB_TOKEN` 推送 GHCR。若仓库或 Package 为私有，目标集群
还必须配置可拉取这些 Package 的 `imagePullSecrets`。

## 发布过程

推送到 `main` 后，只有该提交的 CI 全部成功，生产工作流才会执行。工作流会：

1. 检出通过测试的准确提交 SHA。
2. 构建并推送 server/web 镜像。
3. 对最终镜像再次执行高危漏洞扫描并生成 SBOM。
4. 将 Helm Chart 上传到部署目录。
5. 用镜像 digest 覆盖生产值，执行 `helm upgrade --install --atomic`。
6. 等待 server/web Rollout，并执行 `helm test`。

`--atomic` 会在工作负载发布失败时回滚 Helm release，但不会撤销已经执行的数据库迁移。

## 发布后检查

```bash
kubectl -n flow-production get pods,pdb,hpa
kubectl -n flow-production get jobs
helm -n flow-production status flow
helm -n flow-production test flow --logs
```

还需要从集群外执行真实域名的登录、权限、文件上传下载和核心流程验收，并确认指标抓取和
告警投递正常。

## 回滚

查看历史：

```bash
helm -n flow-production history flow
```

确认目标版本与当前数据库兼容后回滚：

```bash
helm -n flow-production rollback flow REVISION --wait --timeout 15m
```

数据库恢复只用于已评审的灾难恢复场景，不能把常规 Helm 回滚和数据库时间点恢复混为
同一个操作。具体决策见
[`../deploy/runbooks/deployment.md`](../deploy/runbooks/deployment.md)。
