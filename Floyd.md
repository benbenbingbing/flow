# Spring Flowable 框架详解

## 一、框架概述

### 1.1 什么是 Flowable

Flowable 是一个用 Java 编写的轻量级业务流程引擎（BPMN 2.0 工作流引擎），是 Activiti 的一个分支版本。它支持：

- **BPMN 2.0** - 业务流程模型和标记法
- **CMMN** - 案例管理模型和标记法
- **DMN** - 决策模型和标记法
- **表单引擎** - 动态表单管理

### 1.2 核心特性

| 特性 | 说明 |
|------|------|
| 轻量级 | 核心引擎仅有约 3MB |
| 高性能 | 支持高并发流程实例执行 |
| 多引擎 | 流程、案例、决策、表单、内容、事件注册六大引擎 |
| 云原生 | 支持 Spring Boot、微服务架构 |
| 历史数据 | 完整的历史数据记录和查询 |

### 1.3 Spring Boot 集成

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>6.8.0</version>
</dependency>
```

## 二、核心服务 API

### 2.1 流程引擎服务

```java
@Autowired
private RuntimeService runtimeService;      // 运行时服务 - 启动/查询流程实例
@Autowired
private TaskService taskService;            // 任务服务 - 查询/完成任务
@Autowired
private RepositoryService repositoryService; // 仓库服务 - 部署/查询流程定义
@Autowired
private HistoryService historyService;      // 历史服务 - 查询历史数据
@Autowired
private ManagementService managementService; // 管理服务 - 数据库/作业管理
@Autowired
private IdentityService identityService;    // 身份服务 - 用户/组管理
@Autowired
private FormService formService;            // 表单服务 - 表单渲染/提交
```

### 2.2 服务职责对照表

| 服务 | 主要职责 | 常用方法 |
|------|----------|----------|
| `RuntimeService` | 流程实例管理 | `startProcessInstanceByKey()`, `deleteProcessInstance()` |
| `TaskService` | 任务管理 | `complete()`, `claim()`, `delegateTask()` |
| `RepositoryService` | 流程定义管理 | `createDeployment()`, `deleteDeployment()` |
| `HistoryService` | 历史数据查询 | `createHistoricProcessInstanceQuery()` |
| `IdentityService` | 用户/组管理 | `newUser()`, `newGroup()` |
| `FormService` | 表单处理 | `getRenderedStartForm()`, `submitStartForm()` |

## 三、数据库表结构详解

Flowable 自动创建的数据库表以 `ACT_` 为前缀，分为五大类：

### 3.1 通用命名规则

| 前缀 | 含义 |
|------|------|
| `ACT_RE_` | REpository 仓库数据（静态资源） |
| `ACT_RU_` | RUntime 运行时数据（流程执行中） |
| `ACT_HI_` | HIstory 历史数据（流程结束后） |
| `ACT_ID_` | IDentity 身份数据（用户/组） |
| `ACT_GE_` | GEneral 通用数据（资源/属性） |

---

## 四、核心表详细说明

### 4.1 流程定义相关表（ACT_RE_）

#### ACT_RE_DEPLOYMENT - 部署表

存储流程部署信息，每次部署会生成一条记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，部署ID |
| `NAME_` | VARCHAR(255) | 部署名称 |
| `CATEGORY_` | VARCHAR(255) | 分类 |
| `KEY_` | VARCHAR(255) | 部署Key |
| `TENANT_ID_` | VARCHAR(255) | 租户ID（多租户支持） |
| `DEPLOY_TIME_` | TIMESTAMP | 部署时间 |
| `DERIVED_FROM_` | VARCHAR(64) | 父部署ID |
| `ENGINE_VERSION_` | VARCHAR(255) | 引擎版本 |

**使用场景**：
- 发布流程时自动插入
- 查询流程定义所属部署
- 删除部署时级联删除关联数据

---

#### ACT_RE_PROCDEF - 流程定义表

存储流程定义元数据，每次部署新版本会生成新记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，格式：{key}:{version}:{deploymentId} |
| `REV_` | INT | 乐观锁版本号 |
| `CATEGORY_` | VARCHAR(255) | 流程分类（来自 BPMN targetNamespace） |
| `NAME_` | VARCHAR(255) | 流程名称 |
| `KEY_` | VARCHAR(255) | 流程Key（BPMN 中 process id） |
| `VERSION_` | INT | 版本号，同Key自动递增 |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 关联部署ID |
| `RESOURCE_NAME_` | VARCHAR(4000) | BPMN 文件名称 |
| `DGRM_RESOURCE_NAME_` | VARCHAR(4000) | 图片资源名称 |
| `DESCRIPTION_` | VARCHAR(4000) | 描述 |
| `HAS_START_FORM_KEY_` | TINYINT | 是否有启动表单 |
| `HAS_GRAPHICAL_NOTATION_` | TINYINT | 是否有图形标记 |
| `SUSPENSION_STATE_` | INT | 挂起状态（1=激活，2=挂起） |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `ENGINE_VERSION_` | VARCHAR(255) | 引擎版本 |
| `DERIVED_FROM_` | VARCHAR(64) | 父定义ID |
| `DERIVED_FROM_ROOT_` | VARCHAR(64) | 根定义ID |
| `DERIVED_VERSION_` | INT | 派生版本 |

**使用场景**：
- 启动流程时通过 `KEY_` 查找最新版本
- 挂起/激活流程定义
- 查询流程定义列表

---

#### ACT_RE_MODEL - 模型表

存储流程设计器创建的模型（未部署前的草稿）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 乐观锁版本 |
| `NAME_` | VARCHAR(255) | 模型名称 |
| `KEY_` | VARCHAR(255) | 模型Key |
| `CATEGORY_` | VARCHAR(255) | 分类 |
| `CREATE_TIME_` | TIMESTAMP | 创建时间 |
| `LAST_UPDATE_TIME_` | TIMESTAMP | 最后更新时间 |
| `VERSION_` | INT | 版本号 |
| `META_INFO_` | VARCHAR(4000) | 元信息（JSON格式） |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID（部署后填充） |
| `EDITOR_SOURCE_VALUE_ID_` | VARCHAR(64) | 编辑器源值ID |
| `EDITOR_SOURCE_EXTRA_VALUE_ID_` | VARCHAR(64) | 编辑器额外源值ID |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |

**使用场景**：
- 流程设计器保存草稿
- 发布前预览
- 版本管理

---

### 4.2 运行时数据表（ACT_RU_）

运行时表数据在流程实例结束后会迁移到历史表，然后删除。

#### ACT_RU_EXECUTION - 运行时流程执行实例表

存储活动的流程实例和执行路径（非常重要！）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，执行实例ID |
| `REV_` | INT | 乐观锁版本 |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID（根执行） |
| `BUSINESS_KEY_` | VARCHAR(255) | 业务Key（关联业务数据） |
| `PARENT_ID_` | VARCHAR(64) | 父执行ID（子流程/分支） |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `SUPER_EXEC_` | VARCHAR(64) | 父执行（调用活动） |
| `ROOT_PROC_INST_ID_` | VARCHAR(64) | 根流程实例ID |
| `ACT_ID_` | VARCHAR(255) | 当前活动节点ID |
| `IS_ACTIVE_` | TINYINT | 是否活跃 |
| `IS_CONCURRENT_` | TINYINT | 是否并发执行 |
| `IS_SCOPE_` | TINYINT | 是否是作用域 |
| `IS_EVENT_SCOPE_` | TINYINT | 是否是事件作用域 |
| `IS_MI_ROOT_` | TINYINT | 是否是多实例根 |
| `SUSPENSION_STATE_` | INT | 挂起状态 |
| `CACHED_ENT_STATE_` | INT | 缓存实体状态 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `NAME_` | VARCHAR(255) | 执行名称 |
| `START_TIME_` | DATETIME | 开始时间 |
| `START_USER_ID_` | VARCHAR(255) | 启动用户ID |
| `LOCK_TIME_` | TIMESTAMP | 锁定时间 |
| `IS_COUNT_ENABLED_` | TINYINT | 是否计数 |
| `EVT_SUBSCR_COUNT_` | INT | 事件订阅计数 |
| `TASK_COUNT_` | INT | 任务计数 |
| `JOB_COUNT_` | INT | 作业计数 |
| `TIMER_JOB_COUNT_` | INT | 定时作业计数 |
| `SUSP_JOB_COUNT_` | INT | 挂起作业计数 |
| `DEADLETTER_JOB_COUNT_` | INT | 死信作业计数 |
| `VAR_COUNT_` | INT | 变量计数 |
| `ID_LINK_COUNT_` | INT | 身份链接计数 |

**使用场景**：
- 查询正在运行的流程实例
- 跟踪流程执行路径
- 判断流程是否在并行分支

---

#### ACT_RU_TASK - 运行时任务表

存储待办任务（用户任务节点）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，任务ID |
| `REV_` | INT | 乐观锁版本 |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `NAME_` | VARCHAR(255) | 任务名称 |
| `PARENT_TASK_ID_` | VARCHAR(64) | 父任务ID（子任务） |
| `DESCRIPTION_` | VARCHAR(4000) | 任务描述 |
| `TASK_DEF_KEY_` | VARCHAR(255) | 任务定义Key（BPMN 中 userTask id） |
| `OWNER_` | VARCHAR(255) | 任务拥有人 |
| `ASSIGNEE_` | VARCHAR(255) | 被指派人（当前处理人） |
| `DELEGATION_` | VARCHAR(64) | 委托状态（PENDING/RESOLVED） |
| `PRIORITY_` | INT | 优先级（默认50） |
| `CREATE_TIME_` | TIMESTAMP | 创建时间 |
| `DUE_DATE_` | DATETIME | 到期时间 |
| `CATEGORY_` | VARCHAR(255) | 分类 |
| `SUSPENSION_STATE_` | INT | 挂起状态 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `FORM_KEY_` | VARCHAR(255) | 表单Key |
| `CLAIM_TIME_` | DATETIME | 认领时间 |
| `IS_COUNT_ENABLED_` | TINYINT | 是否计数 |
| `VAR_COUNT_` | INT | 变量计数 |
| `ID_LINK_COUNT_` | INT | 身份链接计数 |
| `SUB_TASK_COUNT_` | INT | 子任务计数 |

**使用场景**：
- 查询待办任务列表
- 任务指派/认领
- 设置任务到期时间

---

#### ACT_RU_VARIABLE - 运行时变量表

存储流程变量（运行时）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 乐观锁版本 |
| `TYPE_` | VARCHAR(255) | 变量类型（string/integer/boolean等） |
| `NAME_` | VARCHAR(255) | 变量名称 |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID（任务变量） |
| `BYTEARRAY_ID_` | VARCHAR(64) | 字节数组ID（大数据） |
| `DOUBLE_` | DOUBLE | DOUBLE值 |
| `LONG_` | BIGINT | LONG值 |
| `TEXT_` | VARCHAR(4000) | TEXT值 |
| `TEXT2_` | VARCHAR(4000) | TEXT值2 |
| `VAR_SCOPE_` | VARCHAR(255) | 变量作用域 |

**变量类型对照**：

| 数据库类型 | Java类型 | 存储字段 |
|-----------|----------|----------|
| `string` | String | `TEXT_` |
| `integer` | Integer | `LONG_` |
| `long` | Long | `LONG_` |
| `double` | Double | `DOUBLE_` |
| `boolean` | Boolean | `LONG_` (0/1) |
| `date` | Date | `LONG_` (时间戳) |
| `serializable` | Object | `BYTEARRAY_ID_` |

**使用场景**：
- 流程间传递数据
- 条件分支判断
- 任务表单数据

---

#### ACT_RU_IDENTITYLINK - 运行时身份链接表

存储任务/流程的候选人、候选组、参与者信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 乐观锁版本 |
| `GROUP_ID_` | VARCHAR(255) | 组ID（候选组） |
| `TYPE_` | VARCHAR(255) | 类型（candidate/assignee/owner/participant） |
| `USER_ID_` | VARCHAR(255) | 用户ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |

**TYPE_ 说明**：
- `candidate` - 候选人/候选组
- `assignee` - 被指派人
- `owner` - 拥有人
- `participant` - 参与者（历史）
- `starter` - 启动者

**使用场景**：
- 设置任务候选人
- 设置任务候选组
- 查询某用户可办理的任务

---

#### ACT_RU_JOB - 运行时作业表

存储异步作业、定时器、定时任务。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 乐观锁版本 |
| `TYPE_` | VARCHAR(255) | 作业类型（timer/message/async） |
| `LOCK_EXP_TIME_` | TIMESTAMP | 锁过期时间 |
| `LOCK_OWNER_` | VARCHAR(255) | 锁拥有者 |
| `EXCLUSIVE_` | TINYINT | 是否独占 |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `PROCESS_INSTANCE_ID_` | VARCHAR(64) | 流程实例ID |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `RETRIES_` | INT | 重试次数 |
| `EXCEPTION_STACK_ID_` | VARCHAR(64) | 异常堆栈ID |
| `EXCEPTION_MSG_` | VARCHAR(4000) | 异常消息 |
| `DUEDATE_` | TIMESTAMP | 到期时间 |
| `REPEAT_` | VARCHAR(255) | 重复规则（Cron表达式） |
| `HANDLER_TYPE_` | VARCHAR(255) | 处理器类型 |
| `HANDLER_CFG_` | VARCHAR(4000) | 处理器配置 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |

**使用场景**：
- 定时边界事件
- 异步服务任务
- 定时开始事件

---

### 4.3 历史数据表（ACT_HI_）

历史表数据永久保留，用于审计、查询、统计。

#### ACT_HI_PROCINST - 历史流程实例表

存储已完成的流程实例信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，流程实例ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID（同ID_） |
| `BUSINESS_KEY_` | VARCHAR(255) | 业务Key |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `START_TIME_` | DATETIME | 开始时间 |
| `END_TIME_` | DATETIME | 结束时间 |
| `DURATION_` | BIGINT | 持续时间（毫秒） |
| `START_USER_ID_` | VARCHAR(255) | 启动用户ID |
| `START_ACT_ID_` | VARCHAR(255) | 开始活动ID |
| `END_ACT_ID_` | VARCHAR(255) | 结束活动ID |
| `SUPER_PROCESS_INSTANCE_ID_` | VARCHAR(64) | 父流程实例ID |
| `DELETE_REASON_` | VARCHAR(4000) | 删除原因 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `NAME_` | VARCHAR(255) | 流程名称 |
| `CALLBACK_ID_` | VARCHAR(64) | 回调ID |
| `CALLBACK_TYPE_` | VARCHAR(255) | 回调类型 |
| `REFERENCE_ID_` | VARCHAR(64) | 引用ID |
| `REFERENCE_TYPE_` | VARCHAR(255) | 引用类型 |
| `PROPAGATED_STAGE_INST_ID_` | VARCHAR(64) | 传播阶段实例ID |

**使用场景**：
- 查询已完成的流程
- 流程耗时统计
- 流程实例审计

---

#### ACT_HI_ACTINST - 历史活动实例表

存储每个活动节点的执行历史（最详细的历史记录）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `ACT_ID_` | VARCHAR(255) | 活动节点ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `CALL_PROC_INST_ID_` | VARCHAR(64) | 调用的流程实例ID |
| `ACT_NAME_` | VARCHAR(255) | 活动名称 |
| `ACT_TYPE_` | VARCHAR(255) | 活动类型（userTask/startEvent等） |
| `ASSIGNEE_` | VARCHAR(255) | 被指派人 |
| `START_TIME_` | DATETIME | 开始时间 |
| `END_TIME_` | DATETIME | 结束时间 |
| `DURATION_` | BIGINT | 持续时间 |
| `DELETE_REASON_` | VARCHAR(4000) | 删除原因 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |

**使用场景**：
- 流程图高亮显示已执行节点
- 节点耗时分析
- 流程回溯

---

#### ACT_HI_TASKINST - 历史任务实例表

存储已完成的历史任务。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，任务ID |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `TASK_DEF_KEY_` | VARCHAR(255) | 任务定义Key |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `NAME_` | VARCHAR(255) | 任务名称 |
| `PARENT_TASK_ID_` | VARCHAR(64) | 父任务ID |
| `DESCRIPTION_` | VARCHAR(4000) | 描述 |
| `OWNER_` | VARCHAR(255) | 拥有人 |
| `ASSIGNEE_` | VARCHAR(255) | 被指派人 |
| `START_TIME_` | DATETIME | 开始时间 |
| `CLAIM_TIME_` | DATETIME | 认领时间 |
| `END_TIME_` | DATETIME | 结束时间 |
| `DURATION_` | BIGINT | 持续时间 |
| `DELETE_REASON_` | VARCHAR(4000) | 删除原因 |
| `PRIORITY_` | INT | 优先级 |
| `DUE_DATE_` | DATETIME | 到期时间 |
| `FORM_KEY_` | VARCHAR(255) | 表单Key |
| `CATEGORY_` | VARCHAR(255) | 分类 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `LAST_UPDATED_TIME_` | DATETIME | 最后更新时间 |

**使用场景**：
- 查询历史任务
- 任务耗时统计
- 个人任务历史

---

#### ACT_HI_VARINST - 历史变量实例表

存储历史变量值。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `NAME_` | VARCHAR(255) | 变量名 |
| `VAR_TYPE_` | VARCHAR(100) | 变量类型 |
| `REV_` | INT | 版本 |
| `BYTEARRAY_ID_` | VARCHAR(64) | 字节数组ID |
| `DOUBLE_` | DOUBLE | DOUBLE值 |
| `LONG_` | BIGINT | LONG值 |
| `TEXT_` | VARCHAR(4000) | TEXT值 |
| `TEXT2_` | VARCHAR(4000) | TEXT值2 |
| `CREATE_TIME_` | DATETIME | 创建时间 |
| `LAST_UPDATED_TIME_` | DATETIME | 最后更新时间 |

**使用场景**：
- 查询历史变量
- 流程数据审计

---

#### ACT_HI_DETAIL - 历史详情表

存储变量变更历史（非常详细）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `TYPE_` | VARCHAR(255) | 详情类型（VariableUpdate/FormProperty） |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `ACT_INST_ID_` | VARCHAR(64) | 活动实例ID |
| `NAME_` | VARCHAR(255) | 名称 |
| `VAR_TYPE_` | VARCHAR(255) | 变量类型 |
| `REV_` | INT | 版本 |
| `TIME_` | DATETIME | 时间 |
| `BYTEARRAY_ID_` | VARCHAR(64) | 字节数组ID |
| `DOUBLE_` | DOUBLE | DOUBLE值 |
| `LONG_` | BIGINT | LONG值 |
| `TEXT_` | VARCHAR(4000) | TEXT值 |
| `TEXT2_` | VARCHAR(4000) | TEXT值2 |

**使用场景**：
- 变量变更审计
- 完整的操作追溯

---

#### ACT_HI_COMMENT - 历史评论表

存储任务评论和事件。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `TYPE_` | VARCHAR(255) | 类型（comment/event） |
| `TIME_` | DATETIME | 时间 |
| `USER_ID_` | VARCHAR(255) | 用户ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `ACTION_` | VARCHAR(255) | 动作（AddComment/DeleteComment等） |
| `MESSAGE_` | VARCHAR(4000) | 消息内容 |
| `FULL_MSG_` | LONGBLOB | 完整消息（大文本） |

**使用场景**：
- 任务批注
- 审批意见
- 事件记录

---

#### ACT_HI_ATTACHMENT - 历史附件表

存储任务附件。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 版本 |
| `USER_ID_` | VARCHAR(255) | 用户ID |
| `NAME_` | VARCHAR(255) | 附件名称 |
| `DESCRIPTION_` | VARCHAR(4000) | 描述 |
| `TYPE_` | VARCHAR(255) | 附件类型 |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `URL_` | VARCHAR(4000) | URL地址 |
| `CONTENT_ID_` | VARCHAR(64) | 内容ID（关联ACT_GE_BYTEARRAY） |
| `TIME_` | DATETIME | 时间 |

---

#### ACT_HI_IDENTITYLINK - 历史身份链接表

存储历史身份链接信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `GROUP_ID_` | VARCHAR(255) | 组ID |
| `TYPE_` | VARCHAR(255) | 类型 |
| `USER_ID_` | VARCHAR(255) | 用户ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |

---

### 4.4 身份信息表（ACT_ID_）

存储用户、组、成员关系。

#### ACT_ID_USER - 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，用户ID |
| `REV_` | INT | 版本 |
| `FIRST_` | VARCHAR(255) | 名 |
| `LAST_` | VARCHAR(255) | 姓 |
| `EMAIL_` | VARCHAR(255) | 邮箱 |
| `PWD_` | VARCHAR(255) | 密码 |
| `PICTURE_ID_` | VARCHAR(64) | 头像ID |

---

#### ACT_ID_GROUP - 组表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键，组ID |
| `REV_` | INT | 版本 |
| `NAME_` | VARCHAR(255) | 组名称 |
| `TYPE_` | VARCHAR(255) | 组类型（assignment/security-role） |

---

#### ACT_ID_MEMBERSHIP - 组成员关系表

| 字段 | 类型 | 说明 |
|------|------|------|
| `USER_ID_` | VARCHAR(64) | 用户ID |
| `GROUP_ID_` | VARCHAR(64) | 组ID |

---

#### ACT_ID_INFO - 用户信息表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 版本 |
| `USER_ID_` | VARCHAR(64) | 用户ID |
| `TYPE_` | VARCHAR(64) | 类型（account/userinfo） |
| `KEY_` | VARCHAR(255) | 键 |
| `VALUE_` | VARCHAR(255) | 值 |
| `PASSWORD_` | LONGBLOB | 密码（加密） |
| `PARENT_ID_` | VARCHAR(255) | 父ID |

---

#### ACT_ID_PRIV - 权限表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `NAME_` | VARCHAR(255) | 权限名称 |

---

#### ACT_ID_PRIV_MAPPING - 权限映射表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `PRIV_ID_` | VARCHAR(64) | 权限ID |
| `USER_ID_` | VARCHAR(64) | 用户ID |
| `GROUP_ID_` | VARCHAR(64) | 组ID |

---

#### ACT_ID_TOKEN - 令牌表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 版本 |
| `TOKEN_VALUE_` | VARCHAR(255) | 令牌值 |
| `TOKEN_DATE_` | TIMESTAMP | 令牌时间 |
| `IP_ADDRESS_` | VARCHAR(255) | IP地址 |
| `USER_AGENT_` | VARCHAR(255) | 用户代理 |
| `USER_ID_` | VARCHAR(64) | 用户ID |
| `TOKEN_DATA_` | VARCHAR(4000) | 令牌数据 |

---

### 4.5 通用资源表（ACT_GE_）

#### ACT_GE_BYTEARRAY - 通用字节数组表

存储 BPMN 文件、图片、序列化对象等二进制数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 版本 |
| `NAME_` | VARCHAR(255) | 资源名称 |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID |
| `BYTES_` | LONGBLOB | 字节内容 |
| `GENERATED_` | TINYINT | 是否生成（0=上传，1=生成） |

**使用场景**：
- 存储 BPMN XML 文件
- 存储流程图 PNG
- 存储序列化的流程变量

---

#### ACT_GE_PROPERTY - 引擎属性表

存储引擎版本等信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| `NAME_` | VARCHAR(64) | 属性名（主键） |
| `VALUE_` | VARCHAR(300) | 属性值 |
| `REV_` | INT | 版本 |

**常用属性**：
- `schema.version` - 数据库 Schema 版本
- `next.dbid` - 下一个数据库ID

---

### 4.6 事件订阅表（ACT_RU_EVENT_SUBSCR）

存储事件订阅信息（信号、消息事件）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `REV_` | INT | 版本 |
| `EVENT_TYPE_` | VARCHAR(255) | 事件类型（message/signal） |
| `EVENT_NAME_` | VARCHAR(255) | 事件名称 |
| `EXECUTION_ID_` | VARCHAR(64) | 执行实例ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `ACTIVITY_ID_` | VARCHAR(255) | 活动ID |
| `CONFIGURATION_` | VARCHAR(255) | 配置 |
| `CREATED_` | TIMESTAMP | 创建时间 |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |

**使用场景**：
- 消息中间事件
- 信号事件订阅

---

## 五、扩展引擎表

### 5.1 表单引擎表（ACT_FO_）

#### ACT_FO_FORM_DEFINITION - 表单定义表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `NAME_` | VARCHAR(255) | 表单名称 |
| `KEY_` | VARCHAR(255) | 表单Key |
| `VERSION_` | INT | 版本 |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID |
| `PARENT_DEPLOYMENT_ID_` | VARCHAR(64) | 父部署ID |
| `DESCRIPTION_` | VARCHAR(4000) | 描述 |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |
| `RESOURCE_NAME_` | VARCHAR(4000) | 资源名称 |
| `DEPLOY_TIME_` | TIMESTAMP | 部署时间 |

---

#### ACT_FO_FORM_INSTANCE - 表单实例表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `FORM_DEFINITION_ID_` | VARCHAR(64) | 表单定义ID |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `PROC_DEF_ID_` | VARCHAR(64) | 流程定义ID |
| `SCOPE_ID_` | VARCHAR(255) | 作用域ID |
| `SCOPE_TYPE_` | VARCHAR(255) | 作用域类型 |
| `SUB_SCOPE_ID_` | VARCHAR(255) | 子作用域ID |
| `SCOPE_DEFINITION_ID_` | VARCHAR(255) | 作用域定义ID |
| `PROPAGATED_STAGE_INST_ID_` | VARCHAR(255) | 传播阶段实例ID |
| `VALUES_ID_` | VARCHAR(64) | 值ID |
| `TENANT_ID_` | VARCHAR(255) | 租户ID |

---

### 5.2 决策引擎表（ACT_DMN_）

#### ACT_DMN_DECISION_TABLE - 决策表定义表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `NAME_` | VARCHAR(255) | 名称 |
| `KEY_` | VARCHAR(255) | Key |
| `VERSION_` | INT | 版本 |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID |

---

#### ACT_DMN_HI_DECISION_EXECUTION - 决策执行历史表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `DECISION_DEFINITION_ID_` | VARCHAR(64) | 决策定义ID |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID |
| `START_TIME_` | TIMESTAMP | 开始时间 |
| `END_TIME_` | TIMESTAMP | 结束时间 |
| `INSTANCE_ID_` | VARCHAR(255) | 实例ID |
| `EXECUTION_JSON_` | VARCHAR(4000) | 执行JSON |

---

### 5.3 案例引擎表（ACT_CMMN_）

CMMN（Case Management Model and Notation）案例管理引擎。

#### ACT_CMMN_CASEDEF - 案例定义表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `NAME_` | VARCHAR(255) | 名称 |
| `KEY_` | VARCHAR(255) | Key |
| `VERSION_` | INT | 版本 |
| `DEPLOYMENT_ID_` | VARCHAR(64) | 部署ID |

---

#### ACT_CMMN_RU_CASE_INST - 运行时案例实例表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `CASE_DEF_ID_` | VARCHAR(64) | 案例定义ID |
| `NAME_` | VARCHAR(255) | 名称 |
| `STATE_` | VARCHAR(255) | 状态 |
| `START_TIME_` | TIMESTAMP | 开始时间 |
| `START_USER_ID_` | VARCHAR(255) | 启动用户ID |

---

### 5.4 内容引擎表（ACT_CO_）

内容引擎用于管理附件、文档等。

#### ACT_CO_CONTENT_ITEM - 内容项表

| 字段 | 类型 | 说明 |
|------|------|------|
| `ID_` | VARCHAR(64) | 主键 |
| `NAME_` | VARCHAR(255) | 名称 |
| `TASK_ID_` | VARCHAR(64) | 任务ID |
| `PROC_INST_ID_` | VARCHAR(64) | 流程实例ID |
| `CONTENT_STORE_ID_` | VARCHAR(255) | 内容存储ID |
| `CONTENT_STORE_NAME_` | VARCHAR(255) | 内容存储名称 |
| `FIELD_` | VARCHAR(400) | 字段 |
| `MIME_TYPE_` | VARCHAR(255) | MIME类型 |
| `CREATED_` | TIMESTAMP | 创建时间 |

---

## 六、Flowable 数据流转图

```
┌─────────────────────────────────────────────────────────────────┐
│                         流程部署阶段                              │
│  BPMN XML → ACT_RE_DEPLOYMENT + ACT_GE_BYTEARRAY                 │
│          → ACT_RE_PROCDEF (流程定义)                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         流程启动阶段                              │
│  Start → ACT_RU_EXECUTION (流程实例)                             │
│       → ACT_HI_PROCINST (历史记录)                               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         流程执行阶段                              │
│  到达用户任务:                                                   │
│    → ACT_RU_TASK (待办任务)                                      │
│    → ACT_RU_IDENTITYLINK (候选人)                                │
│    → ACT_HI_TASKINST (历史任务)                                  │
│    → ACT_HI_ACTINST (活动历史)                                   │
│                                                                  │
│  设置变量:                                                       │
│    → ACT_RU_VARIABLE (运行时变量)                                │
│    → ACT_HI_VARINST (历史变量)                                   │
│                                                                  │
│  完成任务:                                                       │
│    ← DELETE ACT_RU_TASK                                          │
│    ← UPDATE ACT_HI_TASKINST (设置结束时间)                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         流程结束阶段                              │
│  End → DELETE ACT_RU_EXECUTION                                   │
│      → DELETE ACT_RU_VARIABLE                                    │
│      → UPDATE ACT_HI_PROCINST (设置结束时间)                      │
└─────────────────────────────────────────────────────────────────┘
```

## 七、常用查询示例

### 7.1 查询待办任务

```sql
-- 查询某用户的待办任务
SELECT t.*, p.NAME_ as PROC_NAME
FROM ACT_RU_TASK t
LEFT JOIN ACT_RE_PROCDEF p ON t.PROC_DEF_ID_ = p.ID_
WHERE t.ASSIGNEE_ = 'userId'
   OR (t.ASSIGNEE_ IS NULL AND EXISTS (
       SELECT 1 FROM ACT_RU_IDENTITYLINK i
       WHERE i.TASK_ID_ = t.ID_
       AND i.TYPE_ = 'candidate'
       AND (i.USER_ID_ = 'userId' OR i.GROUP_ID_ IN (
           SELECT g.ID_ FROM ACT_ID_GROUP g
           INNER JOIN ACT_ID_MEMBERSHIP m ON g.ID_ = m.GROUP_ID_
           WHERE m.USER_ID_ = 'userId'
       ))
   ))
ORDER BY t.CREATE_TIME_ DESC;
```

### 7.2 查询流程实例详情

```sql
-- 查询流程实例完整信息
SELECT 
    pi.ID_ as PROC_INST_ID,
    pi.BUSINESS_KEY_,
    pd.NAME_ as PROC_NAME,
    pd.VERSION_ as PROC_VERSION,
    pi.START_TIME_,
    pi.END_TIME_,
    pi.START_USER_ID_,
    (SELECT COUNT(*) FROM ACT_RU_TASK WHERE PROC_INST_ID_ = pi.ID_) as ACTIVE_TASKS,
    (SELECT COUNT(*) FROM ACT_HI_ACTINST WHERE PROC_INST_ID_ = pi.ID_) as COMPLETED_ACTIVITIES
FROM ACT_HI_PROCINST pi
LEFT JOIN ACT_RE_PROCDEF pd ON pi.PROC_DEF_ID_ = pd.ID_
WHERE pi.ID_ = 'processInstanceId';
```

### 7.3 查询流程变量

```sql
-- 查询流程实例的所有变量
SELECT 
    NAME_,
    VAR_TYPE_,
    CASE 
        WHEN VAR_TYPE_ = 'string' THEN TEXT_
        WHEN VAR_TYPE_ IN ('integer', 'long') THEN CAST(LONG_ AS CHAR)
        WHEN VAR_TYPE_ = 'double' THEN CAST(DOUBLE_ AS CHAR)
        WHEN VAR_TYPE_ = 'boolean' THEN IF(LONG_ = 1, 'true', 'false')
        ELSE '【复杂类型】'
    END as VALUE
FROM ACT_RU_VARIABLE
WHERE PROC_INST_ID_ = 'processInstanceId';
```

### 7.4 查询流程图高亮数据

```sql
-- 查询已完成的活动（用于流程图高亮）
SELECT 
    ACT_ID_,
    ACT_NAME_,
    ACT_TYPE_,
    ASSIGNEE_,
    START_TIME_,
    END_TIME_
FROM ACT_HI_ACTINST
WHERE PROC_INST_ID_ = 'processInstanceId'
ORDER BY START_TIME_;

-- 查询当前活动（运行时）
SELECT ACT_ID_
FROM ACT_RU_EXECUTION
WHERE PROC_INST_ID_ = 'processInstanceId' AND IS_ACTIVE_ = 1;
```

## 八、性能优化建议

### 8.1 索引优化

```sql
-- 常用查询索引
CREATE INDEX IDX_TASK_ASSIGNEE ON ACT_RU_TASK(ASSIGNEE_);
CREATE INDEX IDX_TASK_PROC_INST ON ACT_RU_TASK(PROC_INST_ID_);
CREATE INDEX IDX_EXEC_PROC_INST ON ACT_RU_EXECUTION(PROC_INST_ID_);
CREATE INDEX IDX_VAR_PROC_INST ON ACT_RU_VARIABLE(PROC_INST_ID_, NAME_);
CREATE INDEX IDX_HI_PROC_BUSINESS ON ACT_HI_PROCINST(BUSINESS_KEY_);
```

### 8.2 历史数据清理

```sql
-- 清理已完成的历史数据（谨慎操作！）
DELETE FROM ACT_HI_DETAIL WHERE TIME_ < DATE_SUB(NOW(), INTERVAL 1 YEAR);
DELETE FROM ACT_HI_VARINST WHERE CREATE_TIME_ < DATE_SUB(NOW(), INTERVAL 1 YEAR);
DELETE FROM ACT_HI_ACTINST WHERE END_TIME_ < DATE_SUB(NOW(), INTERVAL 1 YEAR);
```

### 8.3 异步历史数据

Flowable 6.6+ 支持异步历史数据处理：

```yaml
flowable:
  history:
    async:
      enabled: true
      threads:
        core-pool-size: 5
        max-pool-size: 20
```

---

## 九、总结

### 9.1 表分类速查

| 类别 | 表前缀 | 主要表 |
|------|--------|--------|
| 仓库 | `ACT_RE_` | DEPLOYMENT, PROCDEF, MODEL |
| 运行时 | `ACT_RU_` | EXECUTION, TASK, VARIABLE, IDENTITYLINK, JOB |
| 历史 | `ACT_HI_` | PROCINST, ACTINST, TASKINST, VARINST, DETAIL |
| 身份 | `ACT_ID_` | USER, GROUP, MEMBERSHIP |
| 通用 | `ACT_GE_` | BYTEARRAY, PROPERTY |

### 9.2 核心概念

1. **Deployment（部署）**：一次部署包含多个流程定义
2. **ProcessDefinition（流程定义）**：BPMN 的静态定义
3. **ProcessInstance（流程实例）**：流程定义的一次执行
4. **Execution（执行）**：流程执行路径，包含分支
5. **Task（任务）**：用户任务节点，需要人工处理
6. **Variable（变量）**：流程间传递的数据
7. **IdentityLink（身份链接）**：任务与用户的关联关系

### 9.3 生命周期

```
部署 → 启动 → 执行 → 完成 → 历史
```

---

*文档生成时间：2026-03-31*
*Flowable 版本：6.8.0*
