# 数据库说明

## 版本规则

业务迁移文件位于 `workflow-server/src/main/resources/db/migration`。

当前迁移已重置为连续版本：

- `V001__business_schema.sql`：业务表结构基线
- `V002__seed_builtin_data.sql`：内置数据

后续新增迁移按 `V003__xxx.sql` 递增，不再使用 `V1.1`、`V10_1` 这类混合编号。

## 初始化职责

- Flowable 引擎表：由 Flowable 自动维护。
- 业务表：由 Flyway 维护。
- 内置数据：由 Flyway 写入，可重复构建干净环境。

JDBC 连接必须包含 `nullCatalogMeansCurrent=true`，避免 Flowable 在同一 MySQL 实例中误扫其它库的引擎表。

## 内置数据

默认账号：

| 用户名 | 密码 | 说明 |
| --- | --- | --- |
| `admin` | `admin` | 系统管理员 |
| `zhangsan` | `admin` | 部门经理示例 |
| `lisi` | `admin` | 普通员工示例 |

默认数据包含：

- 系统菜单
- 系统角色、用户组、组织
- 管理员菜单授权
- 默认工作台
- 报表和服务分类
- 全流程测试流程、节点配置、节点表单、审批配置、演示实体和默认表单

`uploads/` 是运行时文件目录，不纳入 Git。

## 本地空库验证

```bash
mysql -uroot -e "DROP DATABASE IF EXISTS workflow_bootstrap_clean; CREATE DATABASE workflow_bootstrap_clean CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

cd workflow-server
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/workflow_bootstrap_clean?serverTimezone=Asia/Shanghai&allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true' \
SERVER_PORT=18081 \
mvn clean spring-boot:run
```
