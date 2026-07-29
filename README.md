# Flow

Flow 是一个面向企业内部业务的流程配置平台。它将实体建模、动态表单与列表、BPMN
流程、任务处理、权限控制和配置迁移放在同一套运行时中，适合承载审批类和流程驱动的
业务应用。

仓库包含前后端代码、数据库迁移、容器镜像、Helm Chart、CI 检查和生产运维基线。
环境相关的容量、网络、备份、密钥和可观测性配置仍需在部署时落实，不能只依赖默认值。

## 能力范围

- 实体定义、字段关系、状态、编码规则及动态业务表发布
- 表单、列表、联动规则、数据源和 UI 配置发布
- BPMN 流程设计、版本发布、节点配置和表单绑定
- 待办、审批、驳回、撤回、重提、加签、知会和流程进度
- 用户、角色、组织、用户组、菜单、权限码和数据范围
- 流程动作、异步执行、重试、死信、Outbox 和状态补偿
- 文件上传与访问控制，支持本地存储和 S3 兼容对象存储
- 带签名的 `.wfpack` 配置导入、差异分析、发布与回滚
- 系统操作审计、健康检查、Prometheus 指标和部署告警规则

`workflow-project` 提供了一组软件项目研发管理配置和少量跨实体扩展，用于验证平台在
真实业务流程中的组合能力。它不是平台内核的一部分。

## 技术基线

| 范围 | 当前实现 |
| --- | --- |
| 后端 | Spring Boot 3.5、Flowable 7.2、MyBatis-Plus、Flyway |
| 前端 | Vue 3、Vite 8、Element Plus、Pinia、bpmn-js |
| 数据库 | MySQL 8.4，字符集 `utf8mb4` |
| 文件存储 | 本地文件系统或 S3 兼容对象存储 |
| 构建环境 | JDK 21、Maven 3.9、Node.js 22、npm |
| Java 兼容级别 | Maven 当前编译目标为 Java 17；CI 和容器统一使用 JDK/JRE 21 |
| 部署 | Docker Compose 单机部署；Helm/Kubernetes 多副本部署 |

后端是模块化单体，业务模块最终聚合为一个 `workflow-server` 进程。生产部署不会把
数据库迁移、初始化和运行时写入混在同一生命周期中。

```text
Browser
  |
  v
flow-web (Nginx, stateless)
  |
  v
flow-server (2+ replicas, runtime database identity)
  |                         \
  v                          v
MySQL                     S3-compatible storage
  ^
  |
migration job / schema worker (schema database identity)
```

数据库迁移由一次性 Job 执行；系统目录和初始管理员由独立 Bootstrap Job 初始化；
运行时 Pod 使用只具备 DML 权限的数据库账号；动态实体发布产生的 DDL 由
`schema-worker` 串行化处理。

## 仓库结构

```text
workflow-server/       后端 Maven reactor
workflow-web/          Vue 前端
deploy/                Compose、Helm、k3s 验证配置和运维手册
docs/                  领域设计、数据库、测试和历史资料
.github/workflows/     CI 与生产发布流程
```

主要后端模块：

| 模块 | 职责 |
| --- | --- |
| `workflow-core` | 统一结果、异常、安全注解和无业务语义的基础能力 |
| `workflow-contracts` | 跨模块端口、事件和稳定契约 |
| `workflow-admin` | 认证、用户、角色、组织、菜单、权限和系统审计 |
| `workflow-storage` | 文件元数据、访问控制和存储策略 |
| `workflow-db-migrator` | Flyway、Flowable 建表迁移和 DDL 队列 Worker |
| `workflow-entity` | 实体、表单、列表、数据权限、发布和运行态数据 |
| `workflow-process` | 流程定义、部署、实例、任务、抄送和状态同步 |
| `workflow-project` | 软件项目研发管理配置和领域扩展 |
| `workflow-integration` | Outbox、受控 HTTP 调用和通知集成 |
| `workflow-migration` | 配置包、差异分析、环境映射和发布 |
| `workflow-devtools` | 仅用于测试和开发的辅助实现 |
| `workflow-app` | Spring Boot 入口和最终运行制品 |

模块间共享能力应通过 `workflow-contracts` 暴露，不能直接访问其他模块的内部 Mapper。
新数据库变更统一放在
`workflow-server/workflow-db-migrator/src/main/resources/db/migration/`。已经发布的迁移
文件不得修改或重排版本。

## 本地开发

### 前置条件

- JDK 21
- Maven 3.9+
- Node.js 22 和 npm
- Docker（使用脚本自动启动本地 MySQL 时需要）
- MySQL 8.4（也可使用已有实例）

后端需要两个不同的数据库身份：

- 运行账号：只授予业务库的 `SELECT`、`INSERT`、`UPDATE`、`DELETE`
- 结构账号：供 Flyway、Flowable 初始化和实体发布使用，具备所需 DDL 权限

授权逻辑可参考
[`deploy/mysql-init/10-database-users.sh`](deploy/mysql-init/10-database-users.sh)。
复制环境变量模板后，必须替换其中的示例密码和密钥：

```bash
cp .env.example .env
```

推荐使用根目录脚本启动。默认情况下，当 `DB_HOST` 为 `localhost` 时，脚本会通过
Compose 启动 MySQL、重放数据库授权、构建前后端、执行独立迁移器，并依次启动
schema worker、后端和 Vite：

```bash
./start.sh
./start.sh status
```

停止应用进程：

```bash
./start.sh stop
```

使用已有 MySQL 时，在 `.env` 中设置连接和账号，并配置：

```text
START_LOCAL_MYSQL=false
```

也可以将所有组件运行在容器中：

```bash
docker compose --env-file .env up -d --build --wait
docker compose --env-file .env ps
```

两种启动方式会占用相同的默认端口，不要同时运行。默认访问地址：

- 前端：`http://localhost:3000`
- 后端 API：`http://localhost:8080/api`
- 存活检查：`http://localhost:8080/livez`
- 就绪检查：`http://localhost:8080/healthz`
- 管理与指标端口：`http://localhost:9090`

手工运行 Vite 时，`/api` 默认代理到 `http://localhost:8080`。`start.sh` 会根据
`SERVER_PORT` 自动设置代理目标；需要代理到其他地址时可显式设置
`VITE_API_PROXY_TARGET`。

`start.sh` 只管理本地应用进程，不参与 CI，也不是生产部署入口。它会核对 PID 对应
的进程命令，发现端口属于其他程序时会中止启动并报错，不会直接终止无关进程。

### 初始管理员

系统不存在可用于生产的默认密码。新库中的 `admin` 账号初始处于禁用状态，必须通过
`WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD` 激活。密码长度为 14 到 72 个字符，且至少包含
大写字母、小写字母和数字；不能包含 `admin`、`password` 或模板占位词。

Helm 部署从外部 Secret 的 `bootstrap-admin-password` 键读取该值。Bootstrap 只负责
首次激活，不会覆盖已经修改过的管理员密码。

## 构建与验证

后端完整验证：

```bash
cd workflow-server
mvn verify
```

前端完整验证：

```bash
cd workflow-web
npm ci
npm test
npm run build
```

真实环境验收使用单独的环境变量，不会从本地文件读取业务对象标识。执行
`npm run test:acceptance:real` 前，除 `TEST_USERNAME`、`TEST_PASSWORD` 和
`WORKFLOW_WEB_BASE` 外，还必须提供已发布且可访问的
`VISUAL_ENTITY_ID`、`VISUAL_ENTITY_CODE`、`VISUAL_PROCESS_ID`、`VISUAL_FORM_ID` 和
`VISUAL_LIST_CONFIG_ID`。可选的 `VISUAL_LIST_KEY`、`VISUAL_ENTITY_NAME`、
`VISUAL_PROCESS_NAME`、`VISUAL_PROJECT_NAME` 只影响验收页面的预期文案。
验收账户和这些资源标识应由受控的 CI Secret 或测试环境注入，不能写入源码、日志或
验收产物。

部署清单验证：

```bash
./deploy/scripts/validate-manifests.sh
```

该脚本会检查生产和本地 Helm 渲染结果、Kubernetes Schema 以及生产 Compose 配置，
需要本机安装 Helm 和 Docker。GitHub Actions 还会执行依赖审计、镜像构建、Trivy
高危漏洞门禁并生成 CycloneDX SBOM。

## 生产部署

多 Pod 环境使用 [`deploy/helm/flow`](deploy/helm/flow)。生产值文件至少需要完成以下
配置：

- 使用镜像 digest，而不是可变标签
- 通过外部 Secret 提供数据库、JWT、配置包签名、管理员和对象存储凭据
- 分离运行数据库账号和结构数据库账号
- 使用持久化的 S3 兼容对象存储；多 Pod 不支持本地文件系统
- 配置真实的 Ingress 域名、TLS、CORS 来源和可信代理
- 按实际依赖网络填写 NetworkPolicy CIDR
- 根据数据库连接上限校准副本数和连接池
- 接入 ServiceMonitor、PrometheusRule、日志、告警和备份恢复流程

部署前先阅读：

- [生产部署说明](deploy/README.md)
- [发布与回滚](deploy/runbooks/deployment.md)
- [备份与恢复](deploy/runbooks/backup-restore.md)
- [容量与故障恢复](deploy/runbooks/capacity-resilience.md)
- [事件响应](deploy/runbooks/incident-response.md)

典型发布命令：

```bash
helm upgrade --install flow deploy/helm/flow \
  --namespace flow --create-namespace \
  --values values.production.yaml \
  --atomic --wait --timeout 15m

helm test flow --namespace flow
```

数据库迁移是前向操作。Helm 回滚只能回滚工作负载，不能撤销已经执行的数据库变更；
发布前必须确认备份可恢复，并保证新迁移对上一版本应用的回滚窗口兼容。

单机环境可使用 [`deploy/compose.prod.yml`](deploy/compose.prod.yml)，但它不提供
Kubernetes 的多副本、PodDisruptionBudget、HPA 和 NetworkPolicy 能力，不能作为多
Pod 生产拓扑的等价替代。

## CI/CD

每次 push 和 Pull Request 都会执行：

1. 后端 `mvn verify`
2. 前端依赖审计、完整测试和生产构建
3. Helm、Kubernetes 和 Compose 清单校验
4. server/web 镜像构建与高危漏洞扫描
5. CycloneDX SBOM 生成

生产部署可以在 Actions 中手动触发。仓库变量 `PRODUCTION_DEPLOY_ENABLED` 设置为
`true` 后，`main` 分支 CI 成功时也会自动部署。工作流构建并推送不可变镜像，再通过
SSH 在目标集群执行 Helm 发布、Rollout 检查和 Helm smoke test。

部署前必须配置 `DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_SSH_KEY` 和
`DEPLOY_KNOWN_HOSTS` 密钥；`DEPLOY_PORT` 可选，默认使用 `22`。
`DEPLOY_KNOWN_HOSTS` 必须保存预先核验的目标主机公钥记录，工作流不会在运行时信任
临时扫描到的主机密钥。部署路径、命名空间和 Helm release 可分别通过
`DEPLOY_PATH`、`K8S_NAMESPACE` 和 `HELM_RELEASE` 变量调整。审批规则由 GitHub
Environment `阿里云flow` 管理。

## 安全约束

- 不提供 `admin/admin` 或其他公开默认凭据
- JWT、数据库密码、配置包签名密钥和对象存储密钥不得提交到仓库
- 生产 CORS 不允许通配来源
- 只有明确标注访问策略的 API 才能通过架构测试
- 外部 HTTP 调用受协议、主机、私网地址、超时和响应大小限制
- 运行时不开放任意脚本执行接口
- 富文本、BPMN 可执行内容、动态 SQL 标识符和文件访问均在服务端校验
- 多副本任务通过数据库租约、幂等键或唯一约束协调，不能依赖进程内锁

安全漏洞的报告范围、私密联络方式和披露要求见
[安全策略](.github/SECURITY.md)。不要在公开 Issue 中提交漏洞细节或验证代码。

## 相关文档

- [开放集成 V1 接入指南](docs/api/open-integration-onboarding.md)
- [开放集成 OpenAPI 契约](docs/api/openapi-v1.yaml)
- [开放集成兼容与发布策略](docs/api/compatibility-policy.md)
- [开放集成客户端示例](examples/open-integration/README.md)
- [开放 API 故障处理](deploy/runbooks/open-api-client-incident.md)
- [开放集成密钥轮换](deploy/runbooks/integration-secret-rotation.md)
- [管理与审计模块](workflow-server/workflow-admin/README.md)
- [实体模块](workflow-server/workflow-entity/README.md)
- [流程模块](workflow-server/workflow-process/README.md)
- [软件项目研发管理配置](workflow-server/workflow-project/README.md)

`docs/` 中同时存在当前设计资料和历史验收记录；`docs/archive/` 明确为历史实现。
涉及启动、迁移和生产运维时，以代码、Helm Chart、`deploy/runbooks/` 和 CI 配置为准。
