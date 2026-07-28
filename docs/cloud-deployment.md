# GitHub 推送后自动部署到云服务器

当前部署链路：

```text
push main
  -> GitHub Actions CI
  -> 构建 server/web Docker 镜像
  -> 通过 SSH 将镜像包上传到云服务器
  -> Docker 加载指定提交 SHA 的镜像
  -> Docker Compose 更新服务
```

## 1. 云服务器准备

服务器需要：

- Linux x86_64
- Docker Engine 24+
- Docker Compose v2.20+
- 安全组放行 SSH 端口和 `80` 端口

创建部署目录，并让部署用户拥有它：

```bash
sudo mkdir -p /opt/flow
sudo chown "$USER":"$USER" /opt/flow
```

将生产环境变量写入服务器：

```bash
cd /opt/flow
vi .env
```

内容参考 `deploy/.env.example`。密钥可以这样生成：

```bash
openssl rand -base64 36
openssl rand -hex 64
```

分别生成两次第一条命令，作为 `DB_ROOT_PASSWORD` 和 `DB_PASSWORD`；
第二条命令的结果可作为 `JWT_SECRET`。

## 2. 配置 SSH 部署密钥

生成一把只用于部署的密钥：

```bash
ssh-keygen -t ed25519 \
  -C "github-actions-flow-deploy" \
  -f ~/.ssh/flow_github_actions
```

将公钥内容追加到云服务器部署用户的：

```text
~/.ssh/authorized_keys
```

私钥 `~/.ssh/flow_github_actions` 的完整内容稍后放到 GitHub Secret
`DEPLOY_SSH_KEY`。

获取服务器 SSH host key，并先核对服务器上的公钥指纹：

```bash
ssh-keyscan -H YOUR_SERVER_HOST
```

自定义 SSH 端口时使用：

```bash
ssh-keyscan -p YOUR_SSH_PORT -H YOUR_SERVER_HOST
```

输出内容可以放到 GitHub Secret `DEPLOY_KNOWN_HOSTS`。当前工作流已经固定
校验服务器 `8.145.54.70` 的 ed25519 host key，因此该 Secret 对当前服务器
是可选项。不要关闭 `StrictHostKeyChecking`。

## 3. 配置 GitHub

在仓库 `Settings -> Environments` 中创建或使用 `阿里云flow` 环境。
生产环境可按需要启用人工审批。

在 `阿里云flow` Environment secrets 中配置：

| 名称 | 内容 |
| --- | --- |
| `DEPLOY_SSH_KEY` | 必填，上一步生成的私钥全文 |
| `DEPLOY_HOST` | 可选，当前默认 `8.145.54.70` |
| `DEPLOY_PORT` | 可选，当前默认 `22` |
| `DEPLOY_USER` | 可选，当前默认 `root` |
| `DEPLOY_KNOWN_HOSTS` | 可选，已核验的 `ssh-keyscan` 输出 |

可选 Repository variable：

| 名称 | 默认值 | 用途 |
| --- | --- | --- |
| `DEPLOY_PATH` | `/opt/flow` | 服务器部署目录 |

镜像由 GitHub Actions 构建后直接通过 SSH 上传，不需要 GHCR、PAT 或
服务器端 `docker login`。

## 4. 首次部署

把这些文件提交并推送到 `main`。现有 `CI` 工作流通过后，
`Build and Deploy` 会自动：

1. 构建 `server`、`web` 两个镜像。
2. 使用 Git commit SHA 作为不可变镜像标签。
3. 打包镜像并通过 SSH 上传到服务器。
4. 加载镜像，上传生产 Compose 和部署脚本。
5. 等待 MySQL、后端和前端健康检查通过。

部署完成后访问：

```text
http://YOUR_SERVER_HOST/
```

查看服务器状态和日志：

```bash
cd /opt/flow
docker compose --env-file .env -f compose.prod.yml ps
docker compose --env-file .env -f compose.prod.yml logs -f server web
```

## 5. 回滚

只要服务器上仍保留上一个版本的镜像，就可以将镜像名和 `IMAGE_TAG`
指向上一个成功部署的 Git commit SHA：

```bash
cd /opt/flow
IMAGE_TAG=PREVIOUS_COMMIT_SHA \
SERVER_IMAGE=flow-server:PREVIOUS_COMMIT_SHA \
WEB_IMAGE=flow-web:PREVIOUS_COMMIT_SHA \
./deploy.sh
```

最近一次成功部署的 SHA 会记录在：

```text
/opt/flow/.deployed-image-tag
```

应用回滚前先确认数据库迁移向后兼容。数据库卷和上传文件卷不会在
普通部署或回滚时删除。

## 6. HTTPS

当前 Compose 暴露 HTTP `80`。正式域名建议在它前面使用云负载均衡、
Caddy 或宿主机 Nginx 终止 HTTPS，并只开放 `80/443` 和受限来源的
SSH 端口。
