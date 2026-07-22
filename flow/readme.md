# 后端模块与实体列表运行时

## 后端模块

- `workflow-common`：结果对象、异常和无业务语义工具。
- `workflow-contracts`：跨模块端口、事件、DTO 与扩展 SPI。
- `workflow-system`：认证、用户、角色、组织、菜单、字典。
- `workflow-storage`：文件上传与存储策略。
- `workflow-entity`：实体、字段、表单、动态列表、数据范围和动态业务数据。
- `workflow-process`：流程设计、发布、实例、任务、节点表单。
- `workflow-action`：流程动作配置、执行、Outbox、重试和执行日志。
- `workflow-integration`：通知、HTTP、Webhook 和第三方连接器。
- `workflow-migration`：配置快照、导出、导入、对比和发布。
- `workflow-devtools`：Demo、脚本测试和开发工具。
- `workflow-app`：启动类、Flyway、全局配置和最终 JAR。

## 实体分类与系统表目录

实体定义使用两个互相独立的维度：

| 维度 | 编码 | 含义 |
|---|---|---|
| 生命周期 | `STANDALONE` | 独立业务实体，不绑定流程，可作为基础资料、明细或其他流程的数据来源 |
| 生命周期 | `WORKFLOW` | 流程实体，可绑定一个主流程并发起流程实例 |
| 存储模式 | `DYNAMIC` | 设计器创建和维护的动态实体，物理表统一使用 `biz_*` |
| 存储模式 | `SYSTEM` | 平台已有的 `sys_*` 系统表，只登记目录和字段结构 |

- 新实体默认 `STANDALONE + DYNAMIC`。
- `STANDALONE` 可以升级为 `WORKFLOW`，升级后不能降级。
- 流程实体可以先发布实体结构；只有绑定流程状态为 `ACTIVE` 时才显示发起入口。
- 切换或解除流程绑定只检查真实的 `process_instance_id`，普通未流转业务数据不会阻止切换。
- `SYSTEM` 实体由启动目录同步器扫描 `sys_*` 表生成，字段来自 `information_schema`。
- 系统实体不能使用动态实体接口建表、改表、删表、配置表单列表、数据范围、编码规则或绑定流程。
- 系统实体不生成通用实体 CRUD 权限，继续使用用户、组织、角色、字典等系统模块自己的 API 和权限。
- 系统实体不会进入配置迁移包，避免跨环境覆盖身份、组织和权限基础表。

流程绑定接口：

```text
PUT    /api/entity/{entityId}/workflow-binding
DELETE /api/entity/{entityId}/workflow-binding
PUT    /api/entity/{entityId}/lifecycle-mode
```

实体 DTO 返回 `lifecycleMode`、`storageMode` 和 `workflowBindingStatus`。后者支持
`NOT_APPLICABLE / UNBOUND / DRAFT / ACTIVE / DISABLED / MISSING`。

## 实体列表定位

所有动态列表统一使用 `entityCode + listKey`：

```text
GET  /api/entity-lists/{entityCode}/{listKey}/schema
POST /api/entity-lists/{entityCode}/{listKey}/query
POST /api/entity-lists/{entityCode}/{listKey}/scope-simulation
```

菜单路由使用：

```text
/entity-list/{entityCode}/{listKey}
```

页面、菜单、弹窗、抽屉、嵌入页面、表单选择器和子表选择器复用同一运行时。

列表自定义按钮支持 `open-list` 模式，配置目标 `entityCode + listKey`、弹窗/抽屉、选择方式和可信上下文 `relationKey`。CUSTOM 实体引用字段也可指定 listKey，并以 `FORM_PICKER` 场景复用统一列表；选择型场景默认隐藏新增、编辑、审批和删除操作。

## 数据范围

数据库表：

- `entity_list_scope_policy`：定义哪些数据可见。
- `entity_list_scope_binding`：定义哪些用户在什么列表获得方案。
- `entity_list_scope_release`：不可变发布快照。
- `entity_list_scope_audit_log`：保存、发布、模拟、拒绝和绕过审计。
- `entity_list_scope_delegation`：有时间边界的数据范围委托。

组合语义：

```text
E = 匹配的实体级 ALLOW 并集
L = 匹配的当前列表 ALLOW 并集
D = 匹配的实体级和当前列表 DENY 并集

INHERIT  = E AND NOT D
NARROW   = E AND L AND NOT D
OVERRIDE = L AND NOT D
```

没有匹配 ALLOW、快照损坏或扩展缺失时默认拒绝全部。`OVERRIDE` 只允许超级管理员配置。超级管理员通过显式 `entity:{code}:scope:bypass` 权限绕过，并记录审计。

## 扩展接口

`workflow-contracts` 提供：

- `EntityListContextResolver`：把来源记录解析为可信过滤条件。
- `EntityListDataProvider`：复杂列表查询，必须执行 `DataScopePlan`。
- `EntityListSchemaProvider`：扩展列表 schema。
- `EntityListActionProvider`：扩展列表动作。
- `DataScopePredicateProvider`：扩展结构化数据范围条件。

自定义前端组件只能接管展示，必须通过统一列表查询接口读取数据，不能绕过数据范围、字段权限和操作能力。

## JSON 配置存储规范

平台配置不再依赖数据库原生 `JSON` 类型。需要查询、关联、唯一约束或权限审计的配置使用关系表；结构变化较大、只需整体读取的文档使用可移植大文本。

| 数据库 | JSON 文档物理类型 |
|---|---|
| MySQL | `LONGTEXT` |
| PostgreSQL | `TEXT` |
| Oracle | `CLOB` |
| SQL Server | `NVARCHAR(MAX)` |

应用层统一使用 `JsonDocumentCodec` 解析、校验和规范化文档，并限制文档长度、嵌套深度、对象属性数、数组项数及危险键。配置文档应包含 `schemaVersion`，升级时由所属模块执行版本转换。运行时代码禁止依赖 `JSON_EXTRACT`、`JSON_SET` 等数据库专属函数。

已关系化的关键配置：

- `entity_list_action`：工具栏和行操作按钮。
- `entity_list_scene`：列表允许使用的场景。
- `entity_field_option`：实体字段静态选项。
- `process_node_approval_option`：节点审批选项。
- `process_action_definition_entity`：流程动作定义的实体可见范围。
- `config_migration_asset_dependency`：迁移资产依赖。
- `process_task_candidate_user`、`process_task_candidate_group`：任务候选人和候选组。

继续使用文本 JSON 的配置包括数据范围 AST、固定条件、组件高级属性、流程节点扩展、动作参数、发布快照、迁移快照、执行上下文、审计详情和执行轨迹。API DTO 直接返回对象或数组，不返回需要前端再次 `JSON.parse` 的二次编码字符串。

多选和多引用业务字段统一保存到 `biz_{entityCode}_multi`。新实体不再创建原生 JSON 业务列；历史字段迁移时只有在目标实体可确认且关系记录校验通过后才删除旧列，无法可靠识别的空字段暂时保留为文本列并记录告警。

Flyway `V025__portable_json_document_storage.sql` 负责清理空字符串和重复转义数据、回填关系表、补齐 `entity_form.init_config`、标记普通文本操作日志，并将 Flowable `ACT_*` 之外的原生 JSON 字段转换为可移植大文本。迁移完成后会断言平台和 `biz_*` 表不存在原生 JSON 类型。

MySQL 动态实体主表、`_multi` 多值表和 `_team` 团队表统一使用
`utf8mb4_unicode_ci`。Flyway `V026__normalize_dynamic_entity_collation.sql`
会转换历史 `biz_*` 表并在启动前断言不存在混合排序规则，避免实体数据与
`process_*` 平台表关联时触发 MySQL 1267。
