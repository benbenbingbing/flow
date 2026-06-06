# Flow

Flow 是一个低代码流程平台，包含流程设计、实体建模、表单配置、菜单权限和任务处理能力。

## 技术栈

- 后端：Spring Boot 3、Flowable、MyBatis-Plus、Flyway、MySQL
- 前端：Vue 3、Vite、Element Plus
- 部署：Docker Compose、Nginx

## 目录

```text
workflow-server/   后端服务
workflow-web/      前端应用
docs/              架构、部署、测试和历史资料
```

历史文档和旧 SQL 已归档到 `docs/archive`，运行时不依赖这些文件。

## 快速启动

推荐使用 Docker Compose：

```bash
cp .env.example .env
docker compose up -d --build
```

默认地址：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- 账号：`admin`
- 密码：`admin`

本地开发：

```bash
cd workflow-server
mvn spring-boot:run
```

```bash
cd workflow-web
npm ci
npm run dev
```

## 数据库

数据库由 Flyway 初始化：

- `V001__business_schema.sql`：业务表结构
- `V002__seed_builtin_data.sql`：内置账号、菜单、组织、角色、工作台和测试流程

Flowable 引擎表由 Flowable 管理，不放入业务迁移。详见 [docs/database.md](/Users/chuncheng/Downloads/code/flow/docs/database.md)。

## 验证

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
