# 数据库说明

## 版本规则

业务迁移文件位于
`workflow-server/workflow-app/src/main/resources/db/migration`。

迁移版本保持三位数字递增。数据库命名隔离由
`V017__isolate_database_table_names.sql` 完成。

## 初始化职责

- Flowable 引擎表：由 Flowable 自动维护。
- 业务表：由 Flyway 维护。
- 内置数据：由 Flyway 写入，可重复构建干净环境。

JDBC 连接必须包含 `nullCatalogMeansCurrent=true`，避免 Flowable 在同一 MySQL 实例中误扫其它库的引擎表。

## 表命名边界

| 职责 | 前缀 | 示例 |
| --- | --- | --- |
| 实体配置和元数据 | `entity_*` | `entity_definition` |
| 自动生成实体业务表 | `biz_*` | `biz_expense_application` |
| 跨实体运行记录 | `runtime_*` | `runtime_entity_record` |
| 平台流程表 | `process_*` | `process_action` |
| 系统和身份 | `sys_*` | `sys_user` |
| 配置迁移 | `config_*` | `config_migration_asset` |
| Flowable 引擎 | `ACT_*` | `ACT_RU_TASK` |

- 新实体物理表名写入 `entity_definition.table_name`。
- 运行时统一通过 `EntityPhysicalTableResolver` 获取表名。
- 自动生成表不允许使用系统配置表前缀。
- Flowable `ACT_*` 表不重命名。

## V017 最终表名

| 原基线表 | 最终表 |
| --- | --- |
| `entity_data` | `runtime_entity_record` |
| `node_config` | `process_node_config` |
| `assignee_config` | `process_node_assignee` |
| `form_config` | `process_form_config` |
| `form_field_config` | `process_form_field_config` |
| `flow_action` | `process_action` |
| `flow_action_definition` | `process_action_definition` |
| `flow_action_execution` | `process_action_execution` |
| `entity_flow_status_mapping` | `process_entity_status_mapping` |

历史动态表由启动迁移任务从 `entity_data_{code}` 原子重命名为
`biz_{code}`，迁移结果记录在 `entity_table_migration_log`。
没有对应有效实体定义的孤立旧表也会保留全部数据并重命名为
`biz_{suffix}`；目标表已存在时记录 `CONFLICT`，绝不覆盖或合并。
尚未创建物理表的草稿实体登记为 `PENDING`，发布实体缺表才视为
`MISSING` 并阻止该实体运行。

项目尚未正式上线，因此代码、前端和 API 只使用新命名，不提供旧表名或旧接口兼容层。

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
mvn -pl workflow-app -am spring-boot:run
```
