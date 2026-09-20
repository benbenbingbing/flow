# 全局设置表设计与实现说明

日期：2026-09-14。状态：已按确认方案实现表迁移、读写接口、系统设置页面及实体设计字段类型面板折叠。迁移在隔离测试库验证，未对现有业务库执行变更。

## 1. 设计目标

新增一张 `sys_global_setting` 表，统一保存系统设置和用户个人偏好。每一行表示“某个作用域内的一项设置”，后续增加偏好时新增设置键及其业务处理即可，无需为每个偏好新增数据库字段。

首个使用场景是实体设计页左侧“字段类型”卡片的收起/展开：默认展开，用户切换后自动保存，重新登录或更换浏览器后读取同一账号的偏好。同一用户在不同实体设计页共用该状态，因此本次不增加 `entity_id`。

字段类型面板保留仅在非系统实体上显示的规则。折叠时保留窄栏及展开按钮，业务字段区域利用释放出的宽度。

## 2. 与现有项目的对应关系

- 项目使用 MySQL 8.4；表采用 InnoDB、`utf8mb4` 和 V074 统一后的 `utf8mb4_unicode_ci`。
- `setting_value` 使用普通文本列，JSON 仅作为应用层的序列化格式，不依赖数据库原生 JSON 类型、JSON 函数或 JSON 索引。后续适配其他数据库时，映射为目标数据库的文本大字段类型，序列化协议保持一致；本草案中的其他建表语法仍按当前 MySQL 方言编写。
- `sys_user.id` 是 `varchar(64)`；本表主键和用户标识采用同样的类型，主键沿用项目的应用侧 ID 分配方式。
- 初版建立通用设置表/API。2026-09-20 起，主侧栏折叠也接入账号设置；宽度仍保存在浏览器 `localStorage`。
- 实施前检查 SQL 与 Java 迁移，最高版本为 V089；本次新增 `V090__global_settings.sql`，创建设置表、首个系统默认值及系统管理菜单/权限。

参考文件：`README.md`、`workflow-web/src/views/EntityDesign.vue`、`workflow-web/src/utils/sidebarLayout.js`、`workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/user/infrastructure/persistence/record/SysUser.java`，以及 `workflow-server/workflow-db-migrator/src/main/resources/db/migration/` 下的 V074、V079 迁移。

## 3. 字段说明

表名：`sys_global_setting`；中文名：全局设置表。

| 字段 | 类型 | 可空 / 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 非空，无默认值 | 主键，由应用分配。 |
| `scope_type` | `varchar(16)` | 非空，无默认值 | 作用域：`SYSTEM` 系统级，`USER` 用户级。 |
| `owner_id` | `varchar(64)` | 非空，无默认值 | 系统级固定为字符串 `0`；用户级填写 `sys_user.id`。`0` 保留为系统归属标识。 |
| `setting_key` | `varchar(160)` | 非空，无默认值 | 稳定设置键，如 `ui.entity_design.field_types_collapsed`。统一采用小写英文、数字、下划线和点分段。 |
| `name` | `varchar(100)` | 非空，无默认值 | 设置名称，简要说明用途，如“实体设计字段类型面板收起状态”；用于界面展示和人工识别，不作为业务匹配标识。 |
| `setting_value_type` | `varchar(16)` | 非空，无默认值 | 值格式：`BOOLEAN` 布尔、`NUMBER` 数字、`STRING` 字符串、`JSON` 对象或数组。前端选择输入控件，后端独立校验。 |
| `setting_value` | `text` | 非空，无默认值 | 设置值文本，由应用统一按 JSON 格式序列化和解析；格式、业务值类型及禁止顶层 `null` 的规则由应用校验。 |
| `remark` | `varchar(500)` | 可空，`NULL` | 设置的详细逻辑说明，包括取值含义、默认行为、生效范围、继承规则和特殊处理等。 |
| `version` | `bigint` | 非空，`0` | 乐观锁版本；更新时校验旧版本并加一，避免并发写入静默覆盖。 |
| `created_by` | `varchar(64)` | 可空，`NULL` | 创建人用户 ID；迁移或后台任务创建且没有操作用户时为空。 |
| `updated_by` | `varchar(64)` | 可空，`NULL` | 最近修改人用户 ID；由服务端填写，后台任务没有操作用户时为空。 |
| `create_time` | `datetime(6)` | 非空，当前时间 | 创建时间。 |
| `update_time` | `datetime(6)` | 非空，当前时间，更新时自动刷新 | 最近修改时间。 |

`setting_value` 中实际保存的文本示例：`true`、`20`、`"zh-CN"`、`{"width":240}`；应用解析后分别得到布尔、整数、字符串和对象。字符串值在存储文本中保留 JSON 双引号，空字符串保存为 `""`；空白文本不是合法设置值。卡片状态解析后只接受布尔值，因此应保存文本 `true` / `false`，不接受表示字符串的文本 `"true"` 或表示数字的文本 `1`。

`name` 回答“这个设置是做什么的”，`remark` 说明“具体按什么逻辑生效”。同一 `setting_key` 应使用统一的名称和逻辑说明；个人偏好写入时由服务端填写这些描述字段，用户切换状态只修改设置值。

设置项的业务值类型、允许作用域、程序默认值及校验规则由服务端统一注册，并将类型写入 `setting_value_type`，首版无需另外建定义表。`name` 和 `remark` 用于展示与说明，不作为可执行规则；新增一个数据库键并不会自动产生新功能，仍需要相应的注册定义和使用方。

四种格式的输入约定如下；所有类型最终仍序列化为普通文本入库：

| `setting_value_type` | 输入方式 | 格式校验 / 存储文本示例 |
| --- | --- | --- |
| `BOOLEAN` | 开关 | 只允许布尔值；文本为 `true` 或 `false`，不把 `1` 或字符串 `"true"` 转为布尔。 |
| `NUMBER` | 数字输入 | 支持整数、小数、负数和科学计数法；拒绝非数字、NaN、Infinity。文本示例 `20`、`-12.5`。 |
| `STRING` | 文本输入 | 用户填写原文，应用自动编码；支持空字符串和换行。存储示例 `"zh-CN"`、`""`。 |
| `JSON` | 多行输入 | 仅允许 JSON 对象或数组；拒绝非法语法、顶层 null 和标量。存储示例 `{"width":240}`、`[1,2]`。 |

同一设置键的系统值与个人值必须使用注册定义指定的同一种格式。保存时服务端填写 `setting_value_type`；读取时检查记录类型与该定义一致，再按持久化类型验证文本。类型不匹配的记录按非法值处理并继续继承，避免手工更改类型绕过业务校验。普通偏好接口不能自由改变一个已有键的类型。

## 4. 索引、关联与约束

- 主键：`PRIMARY KEY (id)`。
- 唯一索引：`UNIQUE (scope_type, owner_id, setting_key)`，保证一个用户或系统对同一设置键只有一条记录。该索引也支持按用户批量读取设置，首版不另加重复索引。
- 系统行必须满足 `scope_type = 'SYSTEM' AND owner_id = '0'`；用户行必须满足 `scope_type = 'USER' AND owner_id` 为非空、非 `0` 的用户 ID。
- `USER` 行与 `sys_user.id` 是业务关联。由于同一列还承载系统归属标识，本版不设置物理外键，由服务端校验用户存在且有效。用户逻辑删除后停止读写其偏好；若以后物理清理用户，同时清理对应设置。
- 使用物理删除表达“恢复继承”，不设置 `deleted` 或启停字段。删除用户设置后继承系统值；删除系统设置后，未被个人值覆盖的设置回到程序默认值。系统设置修改和恢复默认应接入项目现有审计机制，操作人字段仅表示最近操作，不替代历史审计。
- 首版不增加租户、部门、角色、实体或设备作用域；`SYSTEM` 表示当前部署的系统范围。

## 5. 读取与写入规则

对于允许用户覆盖的偏好，读取顺序为：

```text
当前用户的 USER 设置 → SYSTEM 设置 → 程序内置默认值
```

应按记录是否存在选择来源。合法的 `false`、`0` 或空字符串不能被当作“未设置”。对象/数组按整项覆盖，不做隐式深度合并。

服务端注册定义需要区分两种用途：

| 用途 | 允许作用域 | 生效方式 |
| --- | --- | --- |
| 可个性化偏好，如卡片折叠、界面语言 | `SYSTEM`、`USER` | 用户值优先；系统值是尚未设置个人偏好的用户的默认值。 |
| 仅由管理员设置的系统选项 | `SYSTEM` | 只读取系统值或程序默认值；拒绝写入用户覆盖记录。 |

系统默认值调整后，已有个人设置继续生效；用户选择“恢复默认”时应删除个人记录，不能把当前系统值复制为个人记录，否则无法继续继承后续调整。

用户设置的 `owner_id` 必须从登录身份获取，普通用户只能读写自己的值。系统值仅允许具备相应管理权限的用户修改；普通页面仅获取已注册且允许对该页面公开的有效设置。

写入时先根据注册定义验证设置键、作用域和值类型/范围；`name` 去除首尾空白后不能为空，长度不超过 100 个字符，`remark` 可空且不超过 500 个字符。设置值由应用序列化为 JSON 格式文本，以普通字符串写入数据库；首版建议限制每项文本按 UTF-8 编码后不超过 16 KiB。读取时按普通字符串取出，再由应用解析并校验业务类型。数据库只保存文本，不执行 JSON 解析或校验。

应用拒绝无效 JSON 文本、空白文本和顶层 `null`。若读取到历史或手工写入的非法值，应记录异常并忽略该记录，继续按继承顺序读取下一层有效值；不能静默将非法内容转换成 `false`，也不能自动回写覆盖原记录。

已有记录更新或删除需在校验归属权限后按 `id + version` 匹配；删除后重建应分配新 ID，防止旧请求误修改版本号重新从 0 开始的新行。首次创建由唯一索引防止重复行，并在冲突时重新读取最新状态。更新不能修改记录的作用域、归属或设置键。卡片连续快速切换时，前端按同一设置键合并并串行保存最终状态。界面操作立即生效，偏好保存失败不回退、不阻止操作，也不弹提示打断用户；未保存的选择保留在当前账号会话内，不标记为已同步。

## 6. 字段类型卡片的首个设置项

| 属性 | 设计值 |
| --- | --- |
| 设置键 | `ui.entity_design.field_types_collapsed` |
| `name` | 实体设计字段类型面板收起状态 |
| `remark` | true 表示收起，false 表示展开，默认展开。同一账号在所有实体设计页共用；用户设置优先于系统设置，删除个人记录后恢复继承。切换状态自动保存，不影响实体未保存状态和发布。 |
| `setting_value_type` | `BOOLEAN`，由应用校验 |
| 数据库类型 / 存储文本 | `text` / `true` 或 `false` |
| `true` | 收起面板 |
| `false` | 展开面板 |
| 程序默认值 | `false`，保持现有页面初次进入时的展开行为 |
| 允许作用域 | `SYSTEM`、`USER` |
| 用户范围 | 当前账号的所有实体设计页 |
| 保存触发 | 点击面板收起/展开按钮后自动保存 |

示例数据，用户 ID 仅作说明，`setting_value` 列展示实际存储的文本内容：

| `scope_type` | `owner_id` | `setting_key` | `setting_value` | 含义 |
| --- | --- | --- | --- | --- |
| `SYSTEM` | `0` | `ui.entity_design.field_types_collapsed` | `false` | 系统默认展开。 |
| `USER` | `10001` | `ui.entity_design.field_types_collapsed` | `true` | 用户 10001 偏好收起。 |
| `USER` | `10002` | `ui.entity_design.field_types_collapsed` | `false` | 用户 10002 明确偏好展开。 |

其他没有个人记录的用户继承系统默认展开；即使之后系统默认改为收起，用户 10002 仍然保持展开。

V090 的初始系统行写入文本 `false`（SQL 字符串字面量为 `'false'`）；不为所有用户预建记录。个人记录在首次切换时创建，页面读取本身不写入个人设置。

折叠状态属于界面偏好，保存应独立于实体定义保存，不改变实体的“未保存”状态、不触发实体发布，也不进入实体配置的导出/版本快照。页面读取偏好失败时可暂用默认展开；这种回退不能自动覆盖服务器上已有的个人值。

## 7. 建表 SQL

以下为 V090 中的表定义；完整迁移还包含初始设置和菜单授权。表定义已在隔离 MySQL 8.0.27 测试库执行验证。

```sql
CREATE TABLE `sys_global_setting` (
  `id` varchar(64) NOT NULL COMMENT '主键ID，由应用分配',
  `scope_type` varchar(16) NOT NULL COMMENT '作用域：SYSTEM-系统 USER-用户',
  `owner_id` varchar(64) NOT NULL COMMENT '归属标识：SYSTEM固定为0，USER为用户ID',
  `setting_key` varchar(160) NOT NULL COMMENT '稳定设置键，采用小写英文点分命名',
  `name` varchar(100) NOT NULL COMMENT '设置名称，简要说明用途',
  `setting_value_type` varchar(16) NOT NULL COMMENT '值格式：BOOLEAN-布尔 NUMBER-数字 STRING-字符串 JSON-对象或数组',
  `setting_value` text NOT NULL COMMENT '设置值文本，JSON格式由应用序列化、解析及校验',
  `remark` varchar(500) DEFAULT NULL COMMENT '详细逻辑说明，包括取值含义、默认行为和生效规则',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次更新递增',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人用户ID',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '最近修改人用户ID',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '创建时间',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_global_setting_owner_key`
    (`scope_type`, `owner_id`, `setting_key`),
  CONSTRAINT `chk_sys_global_setting_scope_owner` CHECK (
    (`scope_type` = 'SYSTEM' AND `owner_id` = '0')
    OR (`scope_type` = 'USER' AND CHAR_LENGTH(TRIM(`owner_id`)) > 0
        AND `owner_id` <> '0')
  ),
  CONSTRAINT `chk_sys_global_setting_key` CHECK (
    CHAR_LENGTH(TRIM(`setting_key`)) > 0
  ),
  CONSTRAINT `chk_sys_global_setting_name` CHECK (
    CHAR_LENGTH(TRIM(`name`)) > 0
  ),
  CONSTRAINT `chk_sys_global_setting_value_type` CHECK (
    `setting_value_type` IN ('BOOLEAN', 'NUMBER', 'STRING', 'JSON')
  ),
  CONSTRAINT `chk_sys_global_setting_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='全局设置及用户个人偏好';

```

数据库约束保护行结构、唯一性和版本号非负等基本规则；键的规范命名、用户有效性、设置文本格式、业务值类型、允许作用域和并发版本递增由应用负责。持久化层将 `setting_value` 映射为普通字符串，不使用数据库专用 JSON 映射或 SQL JSON 函数。正式实施时须在项目使用的 MySQL 版本验证新迁移、文本读写以及应用层序列化和校验；适配其他数据库时验证对应的文本列映射和 DDL 方言。

## 8. 已实现的接口与页面

服务端包：`com.workflow.admin.setting`。数据库值映射为 `String`，由 `GlobalSettingRegistry` 校验和解析；初版注册 `ui.entity_design.field_types_collapsed`。注册定义同时维护 `name`、`remark`、业务类型、允许作用域和程序默认值；普通用户不能通过偏好接口更改说明或归属。

| 方法 | 路径 | 用途 / 权限 |
| --- | --- | --- |
| GET | `/api/system/settings/mine/{key}` | 登录用户读取自己的有效值及个人覆盖版本；仅返回已允许对页面公开的键。 |
| POST | `/api/system/settings/mine/{key}` | 当前有效用户保存个人覆盖；不接收用户 ID。 |
| POST | `/api/system/settings/mine/{key}/reset` | 按个人记录 ID 和版本删除覆盖，恢复继承。 |
| GET | `/api/system/settings` | 查看系统设置，要求 `system:setting:view` 或 `system:setting:manage`。 |
| POST | `/api/system/settings/{key}` | 修改系统默认值，要求 `system:setting:manage`。 |
| POST | `/api/system/settings/{key}/reset` | 删除系统覆盖恢复程序默认，要求 `system:setting:manage`。 |

保存请求示例：`{"settingValue":"true","expectedId":null,"expectedVersion":null}`。这里 `settingValue` 是字符串；首次创建时预期 ID/版本都为 null，更新时必须使用最近读取响应的 `override.id` 和 `override.version`。恢复默认请求仅包含这两个预期字段。响应中的 `settingValueType` 返回值格式，`value` 已由应用解析为业务类型，`source` 为 `USER`、`SYSTEM` 或 `DEFAULT`。

读取响应的 `override` 始终对应当前编辑作用域的记录，即使该记录内容非法也保留 ID/版本，允许用户修复或删除；它与有效值的来源不同。未设置个人覆盖时，不能拿继承的系统记录版本去提交个人设置。版本冲突统一返回 HTTP 409 和 `SETTING_VERSION_CONFLICT`。

系统管理新增 `/system/settings` 页面，展示名称、值格式、详细逻辑说明、当前默认值来源，支持修改及恢复程序默认。菜单和管理权限初始只授予超级管理员；个人偏好接口使用当前用户的对象级授权。

实体设计通过独立组件 `EntityFieldTypePanel.vue` 展示面板，保留点击/拖拽新增字段、键盘操作和窄屏布局。收起后保留展开入口；展开时可恢复继承。`fieldTypesPreference` store 在同一账号内跨页面复用保存队列，账号变化时立即隔离旧状态和待写意图，不使用浏览器本地存储作为持久化来源。快速切换串行提交，失败保留当前选择，下一次操作重新读取实际版本。后台刷新不能撤销当前会话内尚未保存的选择。

## 9. 初版验证结果与交付范围（2026-09-14）

- 后端 `workflow-admin` 模块 156 项测试通过，其中本次新增 41 项设置服务、授权及四种值格式校验测试。
- 前端四种值格式校验及 7 个偏好场景通过：连续切换、读取失败重试、禁止未读取写入、恢复默认、并发冲突、账号切换时丢弃读取响应及待写队列。
- 管理端生产构建通过。使用隔离模拟接口进行浏览器验收，确认展开/收起、刷新及跨实体页面保持、恢复继承、系统默认值调整、实体仍保持“已保存”及窄屏布局。
- 在临时 MySQL 8.0.27 中针对既有基础表夹具执行包含 `setting_value_type` 的最终 V090，文本列、名称/备注、四种类型约束、唯一性、作用域约束、菜单授权、删除后重新创建及迁移重复执行均通过。测试支持 Docker MySQL 8.4 或显式指定的空白回环测试库。
- 全历史迁移重放在既有 `V062__remove_entity_list_scope_inventory.sql` 处失败：本机 MySQL 报 `Can't reopen table: 'own_root'`，因此不能据本次结果宣称全新数据库完整重放已通过。未修改或修复任何历史迁移。
- 全量前端单测在未涉及本次修改的 `workflow-task-actions.spec.js:174` 断言失败（候选任务提交审批次数 0 与预期 1 不符）。仓库文件行数预算检查也报告多个已有文件超限；本次面板已拆为独立组件，实体设计主文件行数减少，没有提高预算或跳过检查。

正常部署时由独立 Flyway 迁移器按顺序执行 V090，再启动新版服务及前端。当前业务库未执行本次迁移，也未替换正在运行的应用进程。

迁移文件：新增 `workflow-server/workflow-db-migrator/src/main/resources/db/migration/V090__global_settings.sql`；修改 0 个、删除 0 个、重命名 0 个。未修改 `V001__business_schema.sql` 或任何其他主分支已有版本迁移。

## 10. 主菜单偏好扩展（2026-09-20）

新增设置 `ui.layout.sidebar_collapsed`，名称为“左侧主菜单收起状态”，类型为 `BOOLEAN`。`true` 收起，`false` 展开，程序与初始系统值均为 `false`。全局设置页面自动展示此注册项。

- 未操作过的用户继承系统默认值，页面读取不创建个人记录。
- 用户点击顶部主菜单开关立即切换界面，并尝试保存 `USER` 覆盖。已保存的个人配置优先于系统配置，同一账号跨页面、登录会话和浏览器读取同一值。
- 修改系统默认值后，当前页面重新读取有效设置，有个人配置时继续保持个人选择。
- 读取或保存失败都不禁用开关、不回退界面、不弹出错误提示。未保存的选择在当前账号会话内保留；后台读取不能撤销它，下一次手动操作再尝试保存。刷新整个页面或重新登录后读取最后成功保存的配置。
- 账号切换后丢弃旧账号状态和待写队列。旧 `workflow:sidebar-collapsed` 没有账号归属，不自动迁移，也不再读写；`workflow:sidebar-width` 继续控制设备上的宽度。
- 移动端导航抽屉仍按临时打开/关闭处理，不创建桌面主菜单的个人偏好。

本次设置后端 54 项测试、前端 9 个布尔偏好场景及值格式/侧栏宽度测试、管理端生产构建通过。V100 在隔离 MySQL 8.0.27 上通过迁移测试，验证默认值、类型、不会预建个人行、不覆盖已有配置及重复执行。浏览器使用独立模拟接口验证继承、个人覆盖、刷新保持和模拟保存失败时继续操作。

本次仅新增 `workflow-server/workflow-db-migrator/src/main/resources/db/migration/V100__sidebar_collapsed_setting.sql`；修改、删除、重命名历史迁移均为 0。业务库未执行本次迁移。部署时使用现有独立迁移器顺序执行 V100 后更新应用。

## 11. 顶部多标签页扩展（2026-09-20）

| 配置字段 | 取值 / 说明 |
| --- | --- |
| `setting_key` | `ui.layout.tabs_enabled` |
| `name` | 启用顶部多标签页 |
| `setting_value_type` | `BOOLEAN`，由应用验证 `true` / `false` |
| `setting_value` | 普通文本列保存，程序默认和初始系统默认均为 `false` |
| `scope_type` / `owner_id` | `SYSTEM` / `0` 提供默认；`USER` / 当前用户 ID 保存个人覆盖 |
| `remark` | 说明顶部展示位置、个人优先规则、页面状态保留、关闭确认、刷新后不恢复及偏好保存失败处理 |

管理员在“系统管理 → 全局设置”修改默认值。用户可在右上角用户菜单选择“开启多标签页”或“关闭多标签页”；只有主动切换才创建个人覆盖。读取顺序为个人配置、系统配置、程序默认值。偏好读取和保存失败不阻止模式切换、不回退界面，也不弹出错误消息；未成功保存的选择保留在当前账号会话内。

开启后，选项卡直接替换原顶部面包屑，与菜单开关、搜索框和用户菜单保持同一行；关闭后恢复面包屑。真实 URL、权限检查、浏览器前进/后退继续使用原路由。点击菜单或通过页面链接导航时创建/激活标签，同一页面再次打开复用已有标签；翻页、排序和页内定位参数不产生额外标签，不同实体等业务入口参数分别保留。

每个标签拥有独立组件实例、路由快照和滚动位置，切换时保留输入与筛选等页面状态。实体/列表/表单/流程设计等已有未保存检查接入统一关闭确认，实体数据及审批弹窗也登记未保存状态。关闭后台标签同样检查；关闭当前标签后优先切到右侧邻居，再到左侧，最后回到首页，首页标签不能关闭。关闭多标签模式仅移除其他标签并检查其未保存内容，当前页面实例继续保留。切换账号或退出时清理缓存，旧账号的异步关闭确认不得影响新账号。

本阶段按路由页面管理标签，业务记录表单继续以所属页面的弹窗打开。后台页面的实体/审批弹窗暂时隐藏，重新激活后恢复。标签目录、业务输入与页面缓存均仅存在内存中，不写入全局设置表或浏览器持久化存储；完整刷新页面后仅打开当前 URL，不恢复其他标签或未提交内容。窄屏下标签栏独立横向滚动，窗口/侧栏尺寸变化后保持当前标签可见。

验证包括：前端标签身份、缓存目录、关闭取消、导航失败、模式切换、账号隔离及后台弹窗保护测试，9 个布尔偏好场景及四种值格式校验；后端设置相关 56 项测试；管理端和 Embed 生产构建。隔离模拟接口的浏览器检查覆盖同组件多实例输入隔离、关闭未保存标签时取消、关闭模式保留当前输入、保存失败仍切换、关闭重开释放缓存、浏览器后退、独立滚动位置、键盘切换及窄屏布局。V101 在临时 MySQL 8.0.27 中通过默认值、类型、不预建用户行、不覆盖既有设置及重复迁移检查；此检查不代表全历史迁移重放。

本扩展仅新增 `workflow-server/workflow-db-migrator/src/main/resources/db/migration/V101__workspace_tabs_setting.sql`；修改、删除、重命名历史迁移均为 0。业务库未执行迁移。部署时由独立 Flyway 迁移器按顺序执行至 V101，再更新应用。
