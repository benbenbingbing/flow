# 部署说明

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 22+
- MySQL 8.4
- Docker 24+（可选）

## 本地启动

后端：

```bash
cd workflow-server
mvn spring-boot:run
```

前端：

```bash
cd workflow-web
npm ci
npm run dev
```

默认地址：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- 账号：`admin`
- 密码：`admin`

## Docker Compose

```bash
cp .env.example .env
docker compose up -d --build
```

查看日志：

```bash
docker compose logs -f server
docker compose logs -f web
```

停止服务：

```bash
docker compose down
```

保留数据时不要删除 volume。需要清空环境时再执行：

```bash
docker compose down -v
```

## 验证命令

后端：

```bash
cd workflow-server
mvn test
```

前端：

```bash
cd workflow-web
npm run test:unit
npm run build
```

## 迁移说明

数据库结构变更通过 `workflow-server/src/main/resources/db/migration` 管理。

当前公开版本从 `V001` 业务基线开始。Flowable 引擎表由 Flowable 自动维护，业务表由 Flyway 维护。

新增迁移按 `V003__xxx.sql` 递增。不要修改已发布迁移。
