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

## 2026-10-28 菜单初始化补齐

七份脚本在原有 V123 结构范围内统一补齐菜单种子数据，初始化后包含以下平台导航及其全部子菜单：

| 顶级入口 | 子菜单 |
| --- | --- |
| 首页 | 待办、已办与我发起的流程入口 |
| 配置管理 | 实体配置、流程配置、流程用户组、列表列模板 |
| 系统管理 | 用户管理、组织部门管理、职务管理、角色管理、菜单管理、字典设置、工作日历、SLA管理（SLA策略、SLA监控）、外部系统、全局设置、空办理人事件、人员交接、系统日志、配置迁移 |
| 定制开发 | 开发手册（列表字段扩展、列表字段扩展2、自定义列表组件、自定义表单组件、流程动作）、扩展管理 |
| 用户手册 | 快速开始、实体配置、流程管理、集成应用与 Embed、嵌入集成 |
| 数据版本 | 数据版本配置、发布与比较入口 |
| 开放集成 | 接入应用与凭据管理入口 |
| 嵌入集成 | 嵌入视图及授权管理入口 |

本次新增 8 个页面入口及对应的超级管理员授权；恢复 21 个被错误标记为已删除的基础功能权限，并将实体、流程、用户、组织、角色、菜单、字典权限挂到对应页面下。页面入口与 F 类型功能权限分别授权，新增页面不重复声明已有权限码。内置角色统一为 `超级管理员`（`role_code = super_admin`、`id = 1`）。

七份脚本均包含 **101 条菜单/权限记录（40 条目录或页面、61 条按钮权限）**，并为超级管理员显式初始化全部 101 条授权。已通过 SQLite 内存执行菜单及授权 INSERT，核对七种方言的数据一致性、ID 和授权唯一性、菜单树完整性及前端路由；本次未进行七种数据库的完整空库导入。

本次仅修改独立初始化脚本及本文档，Flyway 迁移文件新增、修改、删除或重命名均为 **无**。

## OceanBase MySQL 导入前设置签名密钥

`obmysql.sql` 的 `config.migration.signing_key` 种子数据使用 `JSON_QUOTE(@INIT_SIGNING_KEY)`。未设置变量时，表达式返回 SQL `NULL`，因此会报 `Column 'setting_value' cannot be null`。`ui.user_preferences` 的 JSON 文本本身不是空值，不应通过删除 `setting_value` 的非空约束解决此错误。

先在终端生成本次新库专用的随机密钥：

```sh
openssl rand -hex 32
```

然后在**执行导入的同一个数据库连接**中设置变量，并检查结果为 `1`（将下方占位内容替换为刚生成的 64 位十六进制随机串）：

```sql
SET @INIT_SIGNING_KEY = '粘贴刚生成的64位十六进制随机串';
SELECT IFNULL(
    CHAR_LENGTH(@INIT_SIGNING_KEY) = 64
    AND @INIT_SIGNING_KEY REGEXP '^[0-9A-Fa-f]{64}$',
    0
) AS init_signing_key_valid;
```

确认结果为 `1` 后，继续在此连接执行 `obmysql.sql`。使用 `mysql` / `obclient` 交互客户端时，可执行 `SOURCE /实际路径/obmysql.sql;`。图形客户端必须让变量设置和脚本执行使用同一连接；另开窗口、重新连接或使用单独的“导入”连接，可能丢失变量。不要将实际密钥提交到版本库，也不要为所有数据库设置固定默认密钥。

若已经执行了一部分初始化脚本，先确认实际失败语句和导入状态，再补执行缺失部分；本脚本不是幂等脚本，不要直接在已有表的数据库中全量重跑。若确认失败的是签名密钥 INSERT，且该记录尚未写入，可在设置上述变量后单独执行该 INSERT，并按客户端的中断位置继续执行后续语句。若单独执行 `ui.user_preferences` 仍报非空错误，需要检查目标库的实际表结构与触发器，不能只按此变量问题处理。

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
