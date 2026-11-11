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

## 2026-11-10 同步 V129 组织乱序接入

已核对近期 V124—V129 的 SQL 和 Java 迁移，七份初始化脚本补齐 V129 后均为 **200 张表、2,796 个字段**，包含 Flowable 表，不包含 Flyway 历史表。V124—V128 的结构、系统种子数据及示例表清理结果已包含在脚本中。

| 对象 | 空库最终状态及用途 |
| --- | --- |
| `sys_organization.hierarchy_ready` | 非空，默认 `1`。来源父链未完整时由应用写入 `0`，组织树据此隐藏节点及其子树，祖先补齐后重新关联并计算路径和层级。 |
| `sys_identity_source_binding.parent_external_id` | 可空，长度 255。保存同来源父组织的外部 ID，根组织及用户映射保留 NULL，供追溯及后到父级关联使用。 |
| `sys_identity_source_binding.parent_source_key` | 可空，长度 64。保存同来源父组织的精确 SHA-256 来源键，供父级到达后查询待关联子级。 |
| `idx_identity_parent` | `parent_source_key` 上的普通索引，支持待关联子级查询。 |

MySQL、OceanBase MySQL、PostgreSQL 和人大金仓使用 `smallint` 表示父链状态，Oracle、达梦及 OceanBase Oracle 沿用脚本的 `number(5)` 映射。两个父级引用字段分别使用对应方言的 `varchar` 或 `varchar2`，保持长度和可空性。组织的 `parent_id`、`level`、`path` 注释同步说明父链缺失时的 NULL 语义。V129 的历史父子引用回填用于已有来源映射，空库没有来源映射记录需要转换。

### 本次验证

- 七份脚本的 200 个表名及 2,796 个字段清单一致，新增字段的类型、长度、默认值、可空性、用途注释和索引已核对，相关字段文档与脚本一致。
- 原有种子数据 INSERT 保持一致，七份脚本均包含 103 条菜单/权限、103 条超级管理员授权及固定 `directory` 锁行，V128 退役的三张项目身份扩展示例表已移除。
- `mysql.sql` 和 `obmysql.sql` 均通过 MySQL 8.0.27 独立临时空库完整导入，`pg.sql` 通过 PostgreSQL 15.12 独立临时空库完整导入。导入后验证了表数、字段数、新增字段约束、索引、普通组织的就绪默认值，以及待关联组织的空路径和父级引用存储。
- Oracle、达梦、人大金仓及两种 OceanBase 模式完成静态检查，对应数据库实例的完整导入验证尚未执行。`obmysql.sql` 的执行结果来自 MySQL 实例。

本次 Flyway 迁移文件新增、修改、删除或重命名均为 **无**。下文保留各历史版本的同步记录。

## 2026-11-04 同步 V128 身份来源写入

七份初始化脚本已对齐 V128 的最终结构，均为 **200 张表、2,793 个字段**，包含 Flowable 表，不包含 Flyway 历史表。本次相对 V125 新增两张表、15 个字段，并同步用户密码的可空性及实体字段元数据：

| 对象 | 空库最终状态及用途 |
| --- | --- |
| `sys_user.authentication_mode` | `varchar(32)`，非空，默认 `LOCAL_PASSWORD`；外部账号使用 `EXTERNAL_ONLY`，禁止本地密码登录和重置。 |
| `sys_user.password` | 允许 `NULL`，保留长度 100；对应 `entity_field` 的 `is_required` 初始化为 `0`，允许创建无本地口令的外部账号。 |
| `sys_identity_write_lock` | 以 `lock_key` 为主键，初始化固定 `directory` 行，供身份写入事务串行化组织树校验和首次来源绑定。 |
| `sys_identity_source_binding` | 保存用户及组织的稳定来源键、版本、快照摘要和启用状态；`source_key` 为主键，`(object_type, local_id)` 唯一，来源与本地许可默认启用，绑定状态默认 `ACTIVE`。 |

MySQL 与 OceanBase MySQL 的来源键、快照摘要使用 `char(64) NOT NULL`，继承表的默认字符集和排序规则，不单独声明 `CHARACTER SET` 或 `COLLATE`。应用通过 `HexFormat.of().formatHex(...)` 统一生成固定 64 位小写十六进制摘要，无需依赖摘要列区分大小写。来源键按原始 ID 的精确 UTF-8 值计算，原始 ID 的大小写差异在计算摘要时已保留。其余方言按既有规则转换字符串、整数及秒级时间类型。绑定更新时间由应用维护，不增加数据库自动更新时间逻辑。

移除摘要列的独立排序规则后，已在临时 MySQL 实例复验完整导入，并执行两份 MySQL 系脚本对应表的 DDL，确认两列继承表默认值、大小写不同的原始 ID 可按各自摘要正确查找、相同摘要由主键去重；这不替代 OceanBase 实库导入验证。

V127 曾创建的 `biz_project_user_profile`、`biz_project_directory_user` 和 `biz_project_org_profile` 已由 V128 移除，因此空库不创建这些示例表。菜单/权限和超级管理员授权均保持 **103 条**。

验证已完成：七份脚本的表名、字段清单一致，V126 涉及的字段类型、长度、可空性、默认值、主键、唯一索引及核心种子数据已核对；MySQL 8.0.27 和 PostgreSQL 15.12 的独立临时空库完整导入通过，并验证外部账号空密码、认证策略默认值、目录锁初始行、大小写不同来源 ID 的存储和本地对象绑定唯一约束。其余五种方言完成静态检查，未连接对应数据库进行完整导入；未连接或修改业务数据库。

本次新增、修改、删除或重命名的 Flyway 迁移文件均为 **无**，已有 V001—V128 迁移保持不变。

## 2026-10-29 同步 V125 档案查看入口合并

七份初始化脚本已对齐 V125，仍为 **198 张表、2,778 个字段**。迁入流程档案融入实体“查看”的“流程图”和“审批历史”页签，读取复用实体查看权限及当前数据范围；不再初始化 `process_archive_read` / `process:archive:read` 独立权限及其超级管理员授权。导入管理和执行权限继续保留。

当前共 **103 条菜单/权限记录（41 条目录或页面、62 条按钮权限）和 103 条超级管理员授权**。存量库执行新增的 `V125__merge_process_archive_into_entity_view.sql`，先清理角色授权再删除独立权限节点，保留全部业务数据和流程档案；V124 保持不变。下文 V124 的 104 条记录为该版本的历史同步记录。

七份脚本已通过菜单及授权 INSERT 执行校验，V125 升级后的种子数据与新的空库脚本一致，表结构保持不变。新增迁移通过 H2 外键顺序和数据保留校验；本次未连接实际业务数据库执行迁移，也未进行七种数据库完整导入。

## 2026-10-29 同步 V124 历史数据导入

七份脚本已同步 `V124__historical_entity_and_process_import.sql`，当前均为 **198 张表、2,778 个字段**，包含 Flowable 表，不包含 Flyway 历史表。本次新增四张表、41 个字段，字段类型按各数据库方言转换，保留原迁移的长度、可空性、默认值、主键及五个索引，并补充用途注释：

| 表 | 用途及关键约束 |
| --- | --- |
| `history_import_batch` | 保存导入方案、上传输入和预检快照；按所属用户及创建时间索引查询批次。 |
| `history_import_group` | 保存同事务提交组的执行状态、结果和失败原因；`(batch_id, group_hash)` 唯一，重试复用已提交结果。 |
| `entity_import_source_link` | 保存旧记录到当前实体记录的映射及只读策略；来源摘要为主键，`(entity_code, entity_record_id)` 唯一。 |
| `process_archive_instance` | 独立于引擎运行态保存已结束流程及完整审批事件文档；来源摘要唯一，按关联实体、记录及创建时间索引查询。 |

菜单同步新增系统管理下的“历史数据导入”（顺序 17，`history-import:read`）、“执行历史导入”（`history-import:execute`）及“查看迁入历史”（`process:archive:read`）。沿用独立空库脚本的授权规则，为超级管理员显式授权三条资源。当前共 **104 条菜单/权限记录（41 条目录或页面、63 条按钮权限）和 104 条超级管理员授权**。

本次验证已逐项对照 V124 的字段、默认值、可空性、主键及索引，并通过 SQLite 内存执行七份脚本的菜单、角色和授权 INSERT，核对跨方言数据、菜单树、路由及授权完整性。除新增结构和种子数据外，原有 SQL 保持不变；本次未执行目标数据库的完整空库导入，后文的 MySQL/PostgreSQL 导入结果属于此前 V123 验证记录。

本次新增、修改、删除或重命名的 Flyway 迁移文件均为 **无**，`V124` 及其他已有迁移保持不变。

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
