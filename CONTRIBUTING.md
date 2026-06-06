# 贡献指南

## 开发原则

- 优先保持实现简单，避免提前抽象。
- 每次改动聚焦一个明确目标，不夹带无关重构。
- 修改既有代码时遵循当前模块风格。
- 数据库结构变更必须通过 Flyway 迁移提交。
- 启动期不得自动修改业务表结构。
- 涉及动态 SQL 时必须经过表名、字段名和排序字段白名单校验。

## 本地开发

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

Docker Compose：

```bash
cp .env.example .env
docker compose up -d --build
```

## 提交前检查

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

Compose 配置：

```bash
docker compose config --quiet
```

## 数据库迁移

- 迁移目录：`workflow-server/src/main/resources/db/migration`。
- 当前基线：`V001__business_schema.sql`、`V002__seed_builtin_data.sql`。
- 新增迁移从 `V003__xxx.sql` 开始递增。
- 已发布迁移不得修改。
- Flowable 引擎表由 Flowable 维护，不写入业务迁移。

## 运行时文件

以下目录只用于本地运行，不提交到版本库：

- `logs/`
- `uploads/`
- `.claude/`
- `workflow-server/target/`
- `workflow-web/dist/`
- `workflow-web/node_modules/`
