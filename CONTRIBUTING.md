# 贡献指南

## 基本原则

- 一个提交只解决一个明确问题，不夹带无关格式化或重构。
- 先遵循现有模块和包边界；新增抽象必须能够消除实际重复或依赖问题。
- 跨模块调用通过 `workflow-contracts` 中的端口完成，不直接访问其他模块的 Mapper。
- 动态 SQL 的表名、字段名和排序字段必须来自服务端白名单。
- 运行时不新增任意脚本执行、宽泛反序列化或不受控外部 HTTP 能力。
- 敏感信息不得写入代码、测试数据、日志或 Git 历史。

## 开发环境

本地基线与 CI 保持一致：

- JDK 21
- Maven 3.9+
- Node.js 22
- MySQL 8.4
- Docker（本地依赖和部署清单验证需要）

Node.js 使用 nvm 版本管理工具

准备环境变量：

```bash
cp .env.example .env
```

替换全部 `replace-with-*` 占位值后运行：

```bash
./start.sh
./start.sh status
```

停止本地应用进程：

```bash
./start.sh stop
```

完整容器方式：

```bash
docker compose --env-file .env up -d --build --wait
docker compose --env-file .env ps
```

`start.sh` 和完整 Compose 会使用相同端口，不要同时启动。

## 提交前检查

后端：

```bash
cd workflow-server
mvn verify
```

前端：

```bash
cd workflow-web
npm ci
npm test
npm run build
```

部署清单：

```bash
./deploy/scripts/validate-manifests.sh
```

至少检查一次 `git diff --check`，并确认提交中没有 `.env`、密钥、运行日志、构建产物或
本地验收数据。

## 数据库迁移

业务迁移目录：

```text
workflow-server/workflow-db-migrator/src/main/resources/db/migration/
```

当前已发布版本为 `V001` 至 `V012`，下一个迁移从 `V013__说明.sql` 开始。

- 已发布迁移不得修改、重命名或重排版本。
- 数据库结构变化必须通过 Flyway 迁移提交。
- Flowable Schema 由 `workflow-db-migrator` 的独立迁移阶段维护。
- 应用运行进程必须关闭 Flyway 和 Flowable 自动建表。
- 动态实体 DDL 由 schema worker 使用独立结构账号执行。
- 迁移需要支持滚动发布窗口；不能假设应用和数据库会同时回滚。

## 访问控制

新增 Controller API 时必须声明 `@PublicApi`、`@AuthenticatedApi` 或
`@RequiresPermission` 之一。权限码要同步到迁移和前端可见性控制，并确保
`ApiAccessPolicyCoverageTest` 通过。

高风险写操作需要考虑审计、幂等、并发、多 Pod 租约和失败补偿，不能只依赖 JVM
进程内锁。

## 运行时文件

以下内容只用于本地运行，不提交到仓库：

- `.env` 和 `.env.*.local`
- `logs/`、`uploads/`、`*.log`、`*.pid`
- `workflow-server/**/target/`
- `workflow-web/dist/`
- `workflow-web/node_modules/`
- `.local/` 和 `.codex-artifacts/`
