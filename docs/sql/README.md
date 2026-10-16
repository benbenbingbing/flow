# 数据库空库初始化脚本

本目录脚本用于预先创建的空数据库或空 schema，包含平台表、Flowable 引擎表和系统种子数据，不包含 `biz_*` 动态业务表或业务记录。按目标数据库选择文件，并遵守文件头中的字符集、签名密钥和管理员 Bootstrap 要求。

这些文件是独立空库导入脚本，不是 Flyway `B` 基线，也不是存量库升级脚本。采用 Flyway 管理的新库按版本执行全部迁移；存量库继续执行尚未应用的迁移，不能重复导入本目录脚本。

| 数据库 | 脚本 |
| --- | --- |
| MySQL | [mysql.sql](mysql.sql) |
| PostgreSQL | [pg.sql](pg.sql) |
| Oracle | [oracle.sql](oracle.sql) |
| 达梦 | [dameng.sql](dameng.sql) |
| 人大金仓 | [kingbase.sql](kingbase.sql) |
| OceanBase MySQL 模式 | [obmysql.sql](obmysql.sql) |
| OceanBase Oracle 模式 | [oboracle.sql](oboracle.sql) |

## 2026-10-16 同步范围

七份脚本均为 **194 张表、2,737 个字段**，包含 Flowable 表，不包含 Flyway 历史表。已对照 SQL 和 Java 迁移同步至 V123。

| 来源 | 空库最终状态 |
| --- | --- |
| V110 / V111 | 补齐人员交接菜单及超级管理员授权；加签人员 `(add_sign_id, user_id)` 使用普通索引，`generated_task_id` 继续唯一。 |
| V112 / V113 | 建立 `entity_form_published`、`entity_form_current_default`、`entity_form_publication_lock`，初始化固定发布锁行 `lock_id = 1`；空库没有历史表单数据需要转换。 |
| V114 | 不再创建三张 `ui_config_hotfix_*` / `ui_hotfix_observation_metric` 表及其索引、外键、触发器，也不初始化已退役热修复权限；列表发布历史和发布审计继续保留。 |
| V115 | 建立 `entity_excel_settings`、`entity_excel_scheme`、`entity_excel_scheme_published`、`entity_excel_job`、`entity_excel_job_row`，保留方案编码、任务请求和原始行号的唯一约束。 |
| V116 | 建立 `sys_database_lock`；仅 OceanBase MySQL 方言使用未提交唯一键插入仲裁互斥，释放时必须回滚，其他方言不使用。 |
| V117 | 移除 `entity_form_node.component_name`，组件身份统一使用 `props_document.componentType`，保留锁定版本及快照版本字段。 |
| V118 | 补齐 `ui_extension_definition.scope_entity_ids_document`，支持接口按实体集合限定适用范围。 |
| V119 | 补齐 `sys_organization.create_by`；内置 `entityUserReferenceField` 解析器升级到实现版本 2，其配置 Schema 同时接受旧字段引用和新关系路径。 |
| V120 / V121 | 仅清理既有配置文档；空库没有关联内容草稿或发布快照种子数据，不需要执行文档转换。 |
| V122 | 建立 `process_command_receipt`，保存与引擎变更同事务提交的命令回执，包含实例及创建时间索引；身份字段保留大小写区分语义，时间精度为微秒。 |
| V123 | 空办理人事件菜单归入系统管理，顺序为 16；内置超级管理员已有系统管理目录授权。 |

表单和列表发布字段、组件版本等注释已同步当前运行语义。签名密钥仍按每次新库导入独立生成，管理员仍初始禁用。

## 验证

- 七份脚本的表名、字段清单一致；对照 V112、V115、V116、V118、V119、V122 检查新增结构，并检查退役字段、表及关联对象已移除。
- MySQL 8.0.27 和 PostgreSQL 15 的独立临时空库完整导入通过，并核对新增表、关键索引、发布锁行、菜单授权及解析器配置。
- Oracle、达梦、人大金仓和两种 OceanBase 模式完成静态结构及方言检查，本次未连接对应数据库做完整导入验证。

本次新增、修改、删除或重命名的 Flyway 迁移文件均为 **无**；已有 `V001`—`V123` 迁移保持不变。
