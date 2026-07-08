# Flow 微服务拆分详细设计文档

## 1. 概述与目标

将现有 Spring Boot 单体后端拆分为 5 个微服务，按业务能力（DDD 限界上下文）划分边界，实现独立部署、独立扩展。遵循 Martin Fowler 微服务原则：按业务能力划分、去中心化数据、智能端点哑管道、演进式设计。

**核心约束（基于团队/技术决策）**：

- 团队 4-8 人 → 5 个服务粒度
- 运行时接受最终一致，但第一版保留为单服务（实体数据↔流程引擎强耦合，拆分代价 > 收益）
- 部署 Docker Compose → Nacos + Spring Cloud Gateway
- 数据库共享 MySQL 实例 + 逻辑隔离（按 schema/前缀）

**边界判定依据**：实体数据运行时与流程运行时存在 4 个直接写入口、共享事务、Flowable 监听器跨线程跨域、动态表 schema 内嵌流程字段、`@Lazy` 循环依赖——"总是一起改动"的强信号，合并为一个服务。

## 2. 服务清单与边界定义

| 服务 | 中文名 | 职责 | 拆分难度 |
|---|---|---|---|
| `flow-gateway` | 网关/BFF | 路由、JWT 校验、工作台聚合查询 | 低 |
| `flow-auth-service` | 认证基础服务 | 鉴权、用户/角色/菜单/组织/组/字典 | 中 |
| `flow-file-service` | 文件服务 | 上传/下载/预览/存储 | 低 |
| `flow-design-service` | 设计态服务 | 实体定义/表单/列表配置/状态/发布 + 流程定义/节点配置/动作 | 中 |
| `flow-runtime-service` | 运行时服务 | 实体数据动态表 + 流程实例/任务/审批 + Flowable 引擎 + 数据权限 | 高（不进一步拆） |

服务拓扑：

```
                    ┌─────────────────┐
                    │   API Gateway   │  (Spring Cloud Gateway, JWT 校验/路由)
                    │   + Workbench   │  (BFF 聚合查询)
                    └────────┬────────┘
          ┌──────────┬───────┴────┬──────────────┐
          ▼          ▼            ▼              ▼
   ┌────────────┐ ┌────────┐ ┌──────────┐ ┌───────────────────┐
   │ Auth-Svc   │ │File-Svc│ │Design-Svc│ │ Workflow-Runtime  │
   │ 认证+基础数据│ │ 文件存储 │ │ 设计态    │ │ 运行态(核心)       │
   │ user/role  │ │upload/ │ │entity-def│ │ entity-data-rt    │
   │ menu/org   │ │storage │ │proc-def  │ │ proc-rt + Flowable│
   │ group/dict │ │        │ │form/list │ │ perm + listeners  │
   └────────────┘ └────────┘ └──────────┘ └───────────────────┘
```

## 3. 数据库与表归属矩阵

共享 MySQL 实例，逻辑隔离（schema/前缀）。Flowable 引擎表 `ACT_*` 必须与运行时同库。

| Schema | 归属服务 | 表 |
|---|---|---|
| `flow_auth` | auth-service | `sys_user` `sys_role` `sys_role_menu` `sys_menu` `sys_organization` `sys_group` `sys_user_group` `sys_user_role` `sys_dict` `sys_dict_item` |
| `flow_file` | file-service | `entity_field_file_item`（文件元数据） |
| `flow_design` | design-service | `entity_definition` `entity_field` `entity_form` `entity_form_field` `form_config` `form_field_config` `entity_list_config` `entity_list_field` `entity_status` `entity_status_history` `entity_code_rule` `entity_flow_status_mapping` `entity_publish_history` `entity_relation`（设计态定义） `process_definition_config` `process_draft` `node_config` `process_node_form` `process_node_approval` `flow_action` `assignee_config` `process_version_history` `entity_list_permission` `entity_list_permission_delegate` `view_*` `workbench_*` |
| `flow_runtime` | runtime-service | `entity_data` `entity_data_<code>`（动态表，运行时建） `process_task` `process_task_instance` `process_cc_record` `process_operation_log` `process_common_opinion` + Flowable `ACT_*` 引擎表 |
| — | 待定/未使用 | `report_*` `service_*`（无 Java 代码，遗留表，暂不分配） |

**跨库引用处理**：

- `entity_definition.process_definition_id`（design → design 内，OK）
- 运行时通过发布快照（`entity_publish_history`）读取设计态配置 → 改为 design-service RPC 提供"读取已发布快照"
- `process_task.assignee` 引用 `sys_user` → runtime 通过 Feign 调 auth 查 displayName

## 4. 各服务 API 契约（端点清单）

### 4.1 flow-auth-service（前缀 `/api/auth` `/api/system/*`）

```
POST   /api/auth/login                 登录签发 JWT
GET    /api/auth/current               当前用户信息
POST   /api/auth/logout                登出
GET    /api/auth/permissions           当前用户权限码

GET    /api/system/user/list|{id}|roles
POST   /api/system/user                PUT /{id}  DELETE /{id}
PUT    /api/system/user/{id}/status|reset-password

GET    /api/system/role/list|enabled|{id}|menu-tree|{id}/menus
POST   /api/system/role                PUT /{id}  DELETE /{id}
PUT    /api/system/role/{id}/status|menus

GET    /api/system/menu/tree|sidebar-tree|{id}|perms|export|type-options
POST   /api/system/menu                PUT /{id}  DELETE /{id}
PUT    /api/system/menu/{id}/status|visible  PUT /sort  POST /import

GET    /api/system/org/tree|enabled|{id}|{id}/path-name
POST   /api/system/org                 PUT /{id}  DELETE /{id}
PUT    /api/system/org/{id}/status

GET    /api/system/group/list|enabled|{id}|users
POST   /api/system/group               PUT /{id}  DELETE /{id}
PUT    /api/system/group/{id}/status|users

GET    /api/system/dict/page|list|{id}
POST   /api/system/dict                PUT /{id}  DELETE /{id}
PUT    /api/system/dict/{id}/status
GET    /api/system/dict/item/tree/{dictId}|tree/code/{dictCode}
POST   /api/system/dict/item           PUT /item/{id}  DELETE /item/{id}
PUT    /api/system/dict/item/{id}/status
```

### 4.2 flow-file-service（前缀 `/api/file` `/api/entity-field-file-item`）

```
POST   /api/file/upload                上传
POST   /api/file/upload-image          上传图片(压缩)
DELETE /api/file                        删除(按url)
GET    /api/file/preview               预览/下载
GET    /api/entity-field-file-item/field/{fieldId}  字段附件项
```

### 4.3 flow-design-service

```
# 实体定义  /api/entity
GET /api/entity | /{id} | /code/{code} | /process/{processId}
POST /api/entity  PUT /{id}  DELETE /{id}
POST /api/entity/{id}/publish  POST /{entityId}/bind-process/{processId}

# 表单  /api/entity-form
GET /list | /entity/{entityId} | /{id} | /entity/{entityId}/fields | /entity/{entityId}/default
POST /api/entity-form  PUT /{id}  DELETE /{id}
PUT /{id}/fields  POST /{id}/copy  PUT /{id}/default  PUT /{id}/init-config

# 表单解析  /api/entity-form-resolve
GET /new-data/{entityCode}  GET /view-data/{entityCode}/{entityDataId}

# 列表配置  /api/entity-list-config
GET /entity/{entityId} | /{id}   POST /save   DELETE /delete/{id}

# 实体状态  /api/entity-status
GET /list/{entityCode} | /list/{entityCode}/{category}   POST /save   POST /save-list/{entityCode}   DELETE /delete/{id}

# 流程状态映射  /api/entity-flow-status
POST /save/{processConfigId}  GET /list/{processConfigId} | /list/by-key/{processKey}  DELETE /delete/{processConfigId}

# 编码规则  /api/entity-code-rule
GET /{entityCode}  POST /  POST /preview

# 发布历史  /api/entity-publish-history
GET /entity/{entityId} | /entity/{entityId}/latest | /{historyId} | /compare

# 版本差异  /api/entity-version-diff
GET /pending/{entityId}  GET /compare/{entityId}  GET /compare/{entityId}/{version}

# 实体选择器配置（仅配置查询归 design）
GET /api/entity-selector/config/{fieldId}

# 流程定义  /api/process
GET / | /published | /unbound | /bindable | /{id} | /key/{processKey}
POST /api/process  PUT /{id}  DELETE /{id}
POST /{id}/publish  POST /{id}/disable
GET /{processId}/versions  GET /versions/{versionId}  POST /{processId}/rollback/{versionId}  DELETE /versions/{versionId}
POST /{processId}/test-parse

# 节点配置  /api/process/{processId}/nodes
GET / | /{id}  POST /  DELETE /{id}

# 节点表单  /api/process-node-form
GET /process/{processConfigId} | /process/{processConfigId}/node/{nodeId} | /entity/{entityId}/forms
POST /api/process-node-form  DELETE /{id}  PUT /process/{processConfigId}

# 流转动作  /api/flow-actions
GET /process/{processConfigId} | /process/{processConfigId}/flow/{sequenceFlowId}
GET /version/{versionId} | /version/{versionId}/flow/{sequenceFlowId}
POST /api/flow-actions  DELETE /{actionId}  POST /sort  POST /{actionId}/toggle

# 权限规则管理(设计态，不含 preview-sql)
GET /api/entity-list-permission/entity/{entityCode}
POST /api/entity-list-permission  PUT /{id}  DELETE /{id}  POST /{id}/toggle
```

> 注：`EntitySelectorController` 的数据查询 `/api/entity-selector/{entityType}` 实际读运行时数据，归 runtime；`/config/{fieldId}` 读设计态配置，归 design。需拆分该 Controller。

### 4.4 flow-runtime-service

```
# 实体数据  /api/entity-data
GET /entity/{entityCode} | /entity/{entityCode}/list-with-config | /entity/{entityCode}/detail/{id}
GET /entity/{entityCode}/process/{processInstanceId} | /entity/{entityCode}/count
POST /api/entity-data  PUT /entity/{entityCode}/detail/{id}  DELETE /entity/{entityCode}/detail/{id}
POST /entity/{entityCode}/search  POST /entity/{entityCode}/export

# 流程实例  /api/process-instance
GET /{processInstanceId}/progress | /{instanceId}/detail | /{processInstanceId}/xml
GET /my-started   POST /{processInstanceId}/terminate

# 任务（合并 ProcessTask/Task/TaskAction 三处重复端点，保留 /api/process-task 主路径）
GET /api/process-task/todo | /done | /count/todo | /count/done | /statistics | /detail/{taskId} | /history/{processInstanceId}
POST /api/process-task/complete | /withdraw | /sync/{processInstanceId}

# 驳回  /api/process-rollback
POST /reject/{taskId}  POST /resubmit/{processInstanceId}  GET /rejected-status/{processInstanceId}

# 抄送  /api/process-cc
GET /my-cc | /process/{processInstanceId} | /statistics
POST /read/{ccId}  POST /read-all

# 权限SQL预览(运行态)
GET /api/entity-list-permission/preview-sql  GET /api/entity-list-permission/{id}/preview-sql

# 实体选择器数据查询
GET /api/entity-selector/{entityType} | /{entityType}/{id} | /{entityType}/batch

# 脚本测试 / 代码生成 / Demo（暂留 runtime 或下线）
POST /api/script/test
POST /api/code-gen/generate  GET /api/code-gen/preview/{entityId} | /download/{entityId}
GET /api/demo/hello  POST /api/demo/process
```

> **去重建议**：`TaskController`/`TaskActionController`/`ProcessTaskController` 待办相关端点重叠（complete/withdraw/history/statistics），拆分时合并为一套，保留 `/api/process-task` 主路径。

### 4.5 flow-gateway（BFF）

```
GET /api/workbench/data   聚合 runtime(待办统计) + auth(用户/菜单)
```

无核心状态，仅路由 + JWT 校验 + 工作台聚合。

## 5. 跨服务 RPC 接口定义（关键契约）

| # | 调用方 → 被调方 | Feign 接口 | 用途 |
|---|---|---|---|
| 1 | runtime → auth | `UserClient.getDisplayName(userId)` / `batchDisplayNames(ids)` | 待办 assigneeName 渲染 |
| 2 | runtime → auth | `OrgClient.getOrgPath(userId)` / `deptTree()` | 数据权限部门树子查询 |
| 3 | runtime → auth | `RoleClient.hasRole(userId, code)` | 多实例审批人收集 |
| 4 | runtime → design | `PublishSnapshotClient.get(entityCode)` | 发起流程前读已发布快照(流程定义ID/节点表单/字段) |
| 5 | runtime → design | `ListConfigClient.get(entityCode, configId)` | 列表查询扩展字段 |
| 6 | design → runtime | `DynamicTableClient.syncSchema(entityDef)` | 发布时建表/改表(DDL 在 runtime 执行) |
| 7 | design → runtime | `DynamicTableClient.tableExists(entityCode)` | 绑定流程前检查数据 |
| 8 | gateway → runtime+auth | (BFF 聚合，非 Feign 契约) | 工作台 |
| 9 | all → auth | `DictClient.items(dictCode)` | 字典（配合 Redis 缓存） |

**协议固化**：流程变量 `entityCode/entityDataId/dataNo/submitterId` 作为 runtime 与 design 间的显式数据契约，写入接口文档。

## 6. 共享基础设施设计

| 能力 | 选型 | 说明 |
|---|---|---|
| 服务发现+配置 | Nacos（单容器） | 注册中心 + 配置中心 |
| 网关 | Spring Cloud Gateway | 路由 + JWT 全局过滤器 |
| 服务间调用 | OpenFeign + Sentinel | 熔断降级 |
| 鉴权 | JWT（网关校验，透传 header） | `X-User-Id` `X-Username` `X-Roles` |
| 缓存 | Redis（单容器） | 字典缓存 + 分布式会话 |
| 消息(二期) | RabbitMQ | 运行时监听器事件化 |

**Docker Compose 拓扑**：mysql + nacos + redis + gateway + auth + file + design + runtime。

## 7. 横切关注点迁移

| 关注点 | 现状 | 目标方案 |
|---|---|---|
| `UserContext` (ThreadLocal) | 拦截器写入，`MetaObjectHandler` 隐式填充 | JWT claims → 网关透传 header → 各服务 Filter 重建上下文；自动填充改显式传值 |
| `DictCacheService` | 进程内 ConcurrentHashMap | Redis 缓存 + auth-service 为源 |
| 数据权限引擎 | service 层 SQL 字符串拼接，仅作用于动态表查询 | runtime 内保留为模块 + 共享 SDK；部门树改 RPC 查 auth |
| `MetaObjectHandler` 自动填充 createdBy | 全局隐式 | 各服务显式 set 或保留但依赖 header 上下文 |
| Flowable 监听器 | 直接写多域 mapper | 一期：本地 ApplicationEvent（同进程解耦）；二期：RabbitMQ 消息事件 + 补偿 |
| 动态表流程字段 | schema 内嵌 process_instance_id/current_task_*/status | 过渡：新增 `process_entity_mapping` 映射表；逐步剥离双写 |

## 8. 数据一致性策略

| 场景 | 一期方案 | 二期方案 |
|---|---|---|
| 运行时内（发起/审批/驳回） | 本地事务（单服务，保留强一致） | 事件驱动 + Saga 补偿 |
| 设计态发布建表 | design→runtime RPC（同步），失败回滚 | 事件 + 异步确认 |
| 跨服务 RPC 调用 | Feign + Sentinel 熔断 + 本地缓存兜底 | 同 |
| 监听器状态回写 | 本地事件（同事务） | 消息 + 最终一致 |

## 9. 风险与开放问题

1. **遗留表**：`report_*`/`service_*`/`view_*` 无 Java 代码，暂未分配服务。是否删除或规划为未来 analytics 服务？
2. **Controller 重复**：待办三 Controller 端点重叠，拆分需合并去重，可能影响前端 API 路径。
3. **`EntitySelectorController` 跨域**：数据查询归 runtime、配置查询归 design，需拆分该 Controller。
4. **动态表双写技术债**：`ProcessRollbackService` 写旧表 `entity_data` 而非动态表，状态不一致，拆分前需先修。
5. **Flowable 引擎表归属**：`ACT_*` 必须随 runtime，不可被 design 误扫（`nullCatalogMeansCurrent=true` 已处理）。

## 10. 分阶段迁移计划

| 阶段 | 内容 | 产出 |
|---|---|---|
| **0 单体模块化** | 按 5 边界整理包结构；消除跨边界直接 mapper 调用；`UserContext` 支持 header 重建；监听器改本地事件；新增 mapping 过渡表 | 模块化单体，边界清晰 |
| **1 基建+file** | Nacos/Redis 容器；拆 file-service；建 gateway 骨架；Feign/JWT 全链路验证 | 2 服务跑通 |
| **2 auth+网关鉴权** | 拆 auth-service；JWT 签发移入；displayName/部门树 RPC+Redis 缓存；DictCache 改查 auth | 3 服务 |
| **3 design** | 拆 design-service；发布快照 RPC 写 runtime；Flyway 按 schema 拆分 | 4 服务 |
| **4 runtime 收尾** | 单体瘦身为 runtime-service；待办 Controller 去重；可选引入 RabbitMQ | 5 服务完成 |
