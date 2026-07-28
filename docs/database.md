# 数据库说明

## 迁移入口

业务表和 Flowable 引擎表由独立制品
`workflow-db-migrator-1.0.0-exec.jar` 负责迁移。业务 Flyway 文件位于：

```text
workflow-server/workflow-db-migrator/src/main/resources/db/migration/
```

当前已发布版本为 `V001` 至 `V012`。新增迁移从 `V013__说明.sql` 开始；已经发布的
文件不得修改、重命名或调整顺序。

应用运行进程设置：

```text
SPRING_FLYWAY_ENABLED=false
FLOWABLE_SCHEMA_UPDATE=false
```

迁移阶段使用结构账号运行一次：

```bash
java -jar workflow-db-migrator/target/workflow-db-migrator-1.0.0-exec.jar
```

所需环境变量：

- `SCHEMA_DATASOURCE_URL`
- `SCHEMA_DB_USERNAME`
- `SCHEMA_DB_PASSWORD`

JDBC URL 必须包含 `nullCatalogMeansCurrent=true`，防止 Flowable 扫描同一 MySQL 实例
中的其他数据库。

## 数据库身份

生产和本地基线都使用两个不同身份：

| 身份 | 权限 | 使用方 |
| --- | --- | --- |
| 运行账号 | `SELECT`、`INSERT`、`UPDATE`、`DELETE` | server、bootstrap |
| 结构账号 | 业务库 DDL 和迁移历史表读写 | migration、schema worker |

账号创建和幂等授权逻辑见
[`../deploy/mysql-init/10-database-users.sh`](../deploy/mysql-init/10-database-users.sh)。
运行账号和结构账号不得相同。

动态实体发布不会把结构权限交给 server。server 将待执行 DDL 写入
`workflow_schema_change`，schema worker 通过数据库租约领取并执行；失败任务按退避
策略重试，达到上限后进入 `FAILED`。

## 表命名

| 范围 | 前缀 | 示例 |
| --- | --- | --- |
| 实体配置 | `entity_*` | `entity_definition` |
| 动态业务表 | `biz_*` | `biz_expense_application` |
| 平台流程 | `process_*` | `process_definition_config` |
| 运行关系 | `runtime_*` / `entity_process_*` | `runtime_entity_record` |
| 身份与权限 | `sys_*` | `sys_user` |
| 配置迁移 | `config_*` | `config_migration_asset` |
| UI 配置 | `ui_*` | `ui_config_release` |
| Flowable 引擎 | `ACT_*` | `ACT_RU_TASK` |

实体物理表名由发布流程登记，运行时代码必须通过统一解析器读取。不能根据用户输入直接
拼接表名、字段名或排序表达式。

## 初始管理员

`V001` 中保留了历史 bootstrap 哈希，`V002` 会在新装和升级时禁用仍使用该公开哈希
的 `admin`。系统没有可登录的默认密码。

首次初始化必须通过 `WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD` 激活管理员。Bootstrap 使用
条件更新，只处理仍处于待初始化状态的账号，不覆盖已经修改过的密码。

## 迁移与回滚

- 发布前执行 Flyway validate 和完整空库迁移验证。
- 迁移必须支持新旧应用短时间并存的滚动发布窗口。
- Helm 或应用版本回滚不会撤销数据库迁移。
- 破坏性变更采用扩展、迁移数据、切换读取、最后清理的多阶段方式。
- 上线前必须验证数据库备份可恢复；只有备份成功不等于具备恢复能力。

生产步骤和故障处理见
[`../deploy/runbooks/deployment.md`](../deploy/runbooks/deployment.md) 与
[`../deploy/runbooks/backup-restore.md`](../deploy/runbooks/backup-restore.md)。
