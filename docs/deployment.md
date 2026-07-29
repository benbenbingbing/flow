# 部署说明

## 本地开发

环境要求：

- JDK 21
- Maven 3.9+
- Node.js 22
- Docker

准备配置并启动：

```bash
cp .env.example .env
# 替换所有 replace-with-* 占位值
./start.sh
./start.sh status
```

默认情况下，脚本通过根目录 Compose 启动 MySQL，在宿主机运行迁移器、schema worker、
Spring Boot 和 Vite。使用已有数据库时设置 `START_LOCAL_MYSQL=false`。

停止应用进程：

```bash
./start.sh stop
```

MySQL 数据卷不会被该命令删除。

## 本地容器环境

所有组件运行在容器中：

```bash
docker compose --env-file .env up -d --build --wait
docker compose --env-file .env ps
```

查看日志：

```bash
docker compose --env-file .env logs -f migration bootstrap server web
```

停止并保留数据：

```bash
docker compose --env-file .env down
```

只有确认不再需要本地数据时才删除卷：

```bash
docker compose --env-file .env down -v
```

## 生产环境

生产 Kubernetes 拓扑使用 [`../deploy/helm/flow`](../deploy/helm/flow)，包含独立的：

- migration Job
- bootstrap Job
- schema worker Deployment
- server Deployment
- web Deployment
- Service、Ingress、HPA、PDB 和 NetworkPolicy

部署值和 Secret 必须由目标环境维护，不能直接使用 Chart 默认值。完整要求见：

- [生产部署说明](../deploy/README.md)
- [发布与回滚](../deploy/runbooks/deployment.md)
- [备份与恢复](../deploy/runbooks/backup-restore.md)
- [容量与故障恢复](../deploy/runbooks/capacity-resilience.md)
- [事件响应](../deploy/runbooks/incident-response.md)

发布前验证：

```bash
./deploy/scripts/validate-manifests.sh

helm lint deploy/helm/flow --strict -f values.production.yaml
helm template flow deploy/helm/flow -f values.production.yaml |
  kubectl apply --dry-run=server -f -
```

发布：

```bash
helm upgrade --install flow deploy/helm/flow \
  --namespace flow --create-namespace \
  --values values.production.yaml \
  --atomic --wait --timeout 15m

helm test flow --namespace flow
```

数据库迁移为前向操作，Helm 回滚不回滚数据库。回滚应用前必须确认迁移与上一版本兼容。

GitHub Actions 自动发布的环境准备见
[`cloud-deployment.md`](cloud-deployment.md)。
