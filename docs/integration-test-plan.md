# 工作流平台业务集成测试文档

> 文档版本：v2.0  
> 适用范围：workflow-server + workflow-web 工作流平台  
> 测试目标：对每个配置项的**具体含义、取值范围、默认值、业务影响、组合影响**进行全面验证，确保配置生效且各种组合下行为正确。

---

## 1. 测试范围与目标

### 1.1 范围

覆盖工作流平台全部核心业务链路，具体包括：

- 流程设计、发布、版本管理与 BPMN 归一化
- **各类 BPMN 节点配置**：开始事件、结束事件、用户任务、服务任务、脚本任务、发送任务、接收任务、手动任务、业务规则任务、排他网关、并行网关、包容网关、事件网关、调用活动、子流程
- 顺序流条件、实体状态映射、流程动作
- 执行人分配策略（固定人员、用户组、角色、表达式、接口动态）
- 多实例（会签/或签）与集合变量
- 实体建模、表单字段、校验规则、子表单、编码规则
- 列表视图、查询条件、按钮权限、数据权限规则、委托
- 流程启动、审批、驳回、转办、撤回、重新提交、超时处理
- 流程状态与实体状态同步
- 前端流程图、审批历史、待办列表展示

### 1.2 目标

- 每个配置项独立变更后，业务行为与配置说明一致。
- 不同配置项组合时无冲突、无覆盖、无静默失败。
- 边界条件、异常输入下系统给出清晰错误提示，数据不脏。
- Flowable 引擎数据、本地 `process_task`、实体动态表、子表数据保持一致。

---

## 2. 测试策略

| 测试类型 | 说明 | 优先级 |
|---|---|---|
| 配置项单测 | 每个配置字段独立设置，验证生效结果 | P0 |
| 节点类型专项测试 | 按 BPMN 节点类型分别设计用例 | P0 |
| 组合测试 | 两个及以上配置项组合，验证相互作用 | P0 |
| 端到端流程测试 | 从流程设计到流程结束的完整链路 | P0 |
| 边界/异常测试 | 空值、非法值、并发、超时、网络异常 | P1 |
| 数据一致性测试 | 校验 Flowable 历史、本地待办、实体表、子表 | P0 |
| 回归测试 | 修改后复现核心流程，确保无回归 | P1 |

### 2.1 测试数据准备

- 测试用户：至少 6 人（`user1`~`user6`），覆盖不同部门/角色。
- 测试角色：至少 2 个（`role_manager`、`role_hr`）。
- 测试用户组：至少 1 个（`group_dev`）。
- 测试部门：至少 2 个（`dept_tech`、`dept_hr`）。
- 测试实体：`test_leave`（请假）、`test_purchase`（采购）、`test_script`（脚本任务）。
- 测试流程：
  - 单审批人流程
  - 排他网关分支流程
  - 并行网关流程
  - 会签流程（3 人并行）
  - 或签流程（2 人并行/串行）
  - 自动跳过第一个节点流程
  - 超时处理流程
  - 脚本任务流程
  - 服务任务流程
  - 调用活动流程
  - 接收任务流程

---

## 3. 测试环境

### 3.1 依赖环境

| 组件 | 版本/要求 | 检查项 |
|---|---|---|
| MySQL | 8.0+ | 字符集 utf8mb4，时区 Asia/Shanghai |
| JDK | 17 | `java -version` |
| Flowable | 7.2.0 | 引擎表已初始化，历史级别 full |
| Flyway | 内嵌 | 迁移脚本 V001~V005 已执行 |
| 前端 | Vue3 + Vite | 可访问 `http://localhost:5173` |
| 文件存储 | local | `uploads` 目录可写 |

### 3.2 环境检查 SQL

```sql
-- 检查迁移版本
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;

-- 检查核心表
SELECT COUNT(*) AS process_count FROM process_definition_config;
SELECT COUNT(*) AS node_count FROM node_config;
SELECT COUNT(*) AS entity_count FROM entity_definition;

-- 检查 action_label 字段（V005 迁移）
SHOW COLUMNS FROM process_task LIKE 'action_label';
```

---

## 4. 通用配置项（所有节点类型）

### 4.1 流程定义配置（process_definition_config）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `process_key` | 流程在 Flowable 引擎中的唯一启动键，也是 BPMN `<process>` 的 `id` | 必填，英文+下划线，如 `leave_process` | 决定流程引擎如何识别和启动流程；前端按 key 发起流程 | 唯一性校验、特殊字符拦截、发布后 `<bpmn:process id="">` 与此一致 |
| `process_name` | 流程中文显示名称 | 必填，如 `请假流程` | 列表、详情、流程图标题、待办列表展示 | 修改后所有展示位置同步更新 |
| `category` | 流程分类，用于分组管理 | 可选，如 `人事流程` | 流程列表按分类筛选、统计报表分组 | 分类修改后筛选结果正确 |
| `status` | 流程生命周期状态 | `DRAFT`（默认）/`PUBLISHED`/`DISABLED` | `DRAFT` 可编辑；`PUBLISHED` 可发起且不可编辑；`DISABLED` 不可发起 | 状态切换后对应操作权限变化 |
| `version` | 每次发布自动递增的版本号 | 从 1 开始 | 旧流程实例按旧版本继续执行，新实例使用最新版本 | 多次发布后 version 递增；旧实例 BPMN 不变 |
| `bpmn_xml` | BPMN 2.0 XML 完整内容 | 由设计器生成 | 发布时经 `ProcessBpmnPublishSanitizer` 归一化后部署到 Flowable | 含 Camunda 属性可正常发布；多实例、脚本任务等转换正确 |
| `description` | 流程描述说明 | 可选 | 流程详情页展示 | 超长文本展示正常 |
| `created_by` | 创建人 ID | 当前用户 | 权限控制、操作日志 | 创建人可编辑草稿 |

**验证步骤**：

1. 新建流程，仅填写 `process_key` 和 `process_name`，保存草稿，确认 `status=DRAFT`，`version=1`。
2. 点击发布，确认 `status=PUBLISHED`，`version=1`，`bpmn_xml` 成功部署。
3. 修改 BPMN 后再次发布，确认 `version=2`；同时已启动的旧流程实例仍使用 version=1 的 BPMN。
4. 设置 `status=DISABLED`，从前端发起该流程，应提示“流程已禁用，无法发起”。
5. 发布含 `camunda:assignee`、`camunda:candidateGroups` 的 BPMN，确认发布后转换为 `flowable:` 前缀。

### 4.2 节点基础配置（node_config）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `node_id` | BPMN 元素在流程图中的唯一标识 | 设计器自动生成，如 `UserTask_1` | 与 BPMN XML 中 `id` 属性一致；执行人、表单、状态映射均按此关联 | 发布后 BPMN 中 id 与数据库一致；ID 冲突时发布器自动重命名 |
| `node_name` | 节点中文显示名称 | 默认与 BPMN 元素名相同 | 流程图节点标签、审批历史、待办列表、tooltip 展示 | 修改后实时同步到流程图和审批历史 |
| `node_type` | BPMN 节点类型枚举 | `START`/`END`/`USER_TASK`/`SERVICE_TASK`/`SCRIPT_TASK`/`SEND_TASK`/`RECEIVE_TASK`/`MANUAL_TASK`/`BUSINESS_RULE_TASK`/`EXCLUSIVE_GATEWAY`/`PARALLEL_GATEWAY`/`INCLUSIVE_GATEWAY`/`EVENT_BASED_GATEWAY`/`CALL_ACTIVITY`/`SUB_PROCESS` | 决定设计器显示哪些配置页签和运行时行为 | 不同类型节点打开设计器时页签正确；运行时按类型执行 |
| `config_json` | 节点扩展配置 JSON | 默认 `{}` | 存储多实例、超时、脚本、服务、审批等高级配置 | JSON 格式非法时保存报错；字段缺失时按默认值处理 |
| `skip_node` | 是否自动跳过当前用户任务节点 | `false`（默认） | `true` 时流程到达后自动完成，不生成待办 | 仅对**用户任务**生效；非用户任务设置无效 |
| `process_config_id` | 所属流程定义配置 ID | 必填 | 关联流程 | 删除流程时级联删除节点配置 |

**验证步骤**：

1. 创建用户任务节点，修改 `node_name`，确认流程图和数据库同步。
2. 设置 `skip_node=true`，发起流程，确认该节点自动完成。
3. 在服务任务节点设置 `skip_node=true`，确认不生效。

---

## 5. 按节点类型的详细配置与验证

### 5.1 开始事件（StartEvent）

#### 5.1.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `node_name` | 开始节点名称 | 默认“开始” | 审批历史展示 | 可自定义为“提交申请”等 |
| `form_source` / `entity_form_ids` | 发起时绑定的表单 | 可选 | 流程启动页展示表单 | 同“用户任务-表单配置” |

#### 5.1.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SE-001 | 默认开始事件 | 绘制开始节点并发布 | 流程可正常发起 |
| SE-002 | 开始节点绑定实体表单 | 开始节点选择实体表单 | 发起流程时显示该表单，提交后进入第一个任务 |
| SE-003 | 开始事件指定启动人 | 通过 `initiator` 变量 | 审批历史中“开始”节点执行人显示为发起人 |

---

### 5.2 结束事件（EndEvent）

#### 5.2.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `node_name` | 结束节点名称 | 默认“结束” | 流程图、审批历史展示 | 可自定义 |
| `terminate_end_event` | 是否为终止结束事件 | `false`（普通结束） | `true` 时结束整个流程实例，包括并行分支 | 并行分支中一个终止结束事件触发后，其他分支任务被删除 |

#### 5.2.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| EE-001 | 普通结束事件 | 流程到达结束节点 | 流程实例正常结束，状态 completed |
| EE-002 | 终止结束事件 | 并行分支中一个分支到达终止结束 | 所有活跃任务终止，流程实例结束 |
| EE-003 | 结束节点名称自定义 | 修改 `node_name` | 审批历史显示自定义名称 |

---

### 5.3 用户任务（UserTask）

用户任务是最复杂的节点类型，包含执行人、表单、审批、多实例、高级配置等多个配置组。

#### 5.3.1 执行人配置

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `assigneeType` | 指定任务处理人的方式 | `user`/`group`/`role`/`expression`/`interface` | 决定任务分配给单个用户、候选组/角色/表达式/动态接口 | 不同方式下待办生成逻辑正确 |
| `assignee` | 固定执行人用户 ID | 用户选择器返回的用户 ID | 任务直接分配给该用户，生成 `process_task` 和 Flowable 任务 | 只有该用户可见待办 |
| `candidateUserIds` | 候选人 ID 列表 | 多选用户选择器返回的数组 | 这些用户都能认领任务 | 所有候选人均生成待办；一人认领后其他候选人待办消失 |
| `candidateGroupIds` | 候选用户组 ID 列表 | 多选组选择器返回的数组 | 组内所有成员可见 | 组内成员变动仅影响新流程实例 |
| `candidateRoleIds` | 候选角色 ID 列表 | 多选角色选择器返回的数组 | 角色下所有用户可见 | 同 `candidateGroupIds` |
| `assignee`（表达式类型） | 执行人表达式 | 如 `${initiator}`、`${manager}` | 运行时解析为具体用户 ID | 变量不存在或解析为空时报错 |
| `candidateUsers`（表达式类型） | 候选人表达式 | 返回用户 ID 列表的表达式 | 动态生成候选人 | 表达式返回非列表时校验 |
| `candidateGroups`（表达式类型） | 候选组表达式 | 返回组编码的表达式 | 动态生成候选组 | 同上 |
| `interfaceType` | 接口动态执行人类型 | `spring`/`rest` | 调用 Spring Bean 或 HTTP 接口获取执行人 | Bean 存在/接口可达 |
| `interfaceName` | Spring Bean 名或 REST URL | 如 `userSelectorService` | 执行目标 | Bean 不存在时报错 |
| `interfaceMethod` | Spring Bean 方法名 | 默认 `selectAssignee` | 反射调用方法 | 方法签名、返回值格式 |
| `restMethod` | REST 请求方式 | `GET`/`POST` | HTTP 方法 | 与接口匹配 |
| `interfaceParams` | 请求参数 JSON | 支持 `${variable}` 流程变量表达式 | 参数注入 | 变量正确替换 |
| `resultMapping` | 返回结果映射 | `assignee`/`assigneeList`/`groupList` | 决定返回结果如何生成待办 | 单用户/用户列表/组列表分别处理 |
| `priority` | 执行人优先级 | 数字，越小越优先 | 多个执行人时的顺序控制 | 优先级高的先生成或先展示 |

**执行人配置验证步骤**：

1. `user` 类型：选择 `user1`，发起流程，确认 `user1` 待办列表出现该任务，其他人不可见。
2. `group` 类型：选择 `group_dev`，确认组内所有用户都有待办；`user1` 认领后，`user2` 待办消失。
3. `role` 类型：选择 `role_manager`，确认角色下用户都有待办。
4. `expression` 类型：设置 `${initiator}`，发起人为 `user1`，确认任务分配给 `user1`；设置不存在的变量，确认流程报错。
5. `interface` 类型（Spring）：配置 `userSelectorService.selectAssignee`，返回 `user1`，确认任务分配给 `user1`；Bean 不存在时发布或发起报错。
6. `interface` 类型（REST）：配置 GET 接口返回 `{"data":"user1"}`，确认任务分配给 `user1`。
7. `candidateUserIds` + `assignee` 同时存在：确认 `assignee` 优先，同时候选人也能看到（视具体实现而定，需确认产品设计）。

#### 5.3.2 多实例配置（会签/或签）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `isMultiInstance` | 是否启用多实例 | `false`（默认） | `true` 时按集合生成多个任务实例 | 任务数量与集合大小一致 |
| `multiInstanceType` | 执行方式 | `parallel`（会签，并行）/`sequential`（或签，串行） | `parallel`：多人同时审批；`sequential`：按顺序审批 | 并行时同时生成多个待办；串行时完成一个再生成下一个 |
| `collectionSource` | 会签人员集合来源 | `variable`（流程变量）/`interface`（接口动态） | 决定 `_wfMultiInstanceUsers_` 变量如何产生 | 两种来源都能正确生成集合 |
| `multiInstanceUserIds` | 会签用户 ID 列表 | 多选用户 | 所选用户每人生成一个会签任务 | 与 `collection` 变量一致性 |
| `multiInstanceGroupIds` | 会签用户组 ID 列表 | 多选组 | 组内成员展开为用户列表 | 组成员解析正确 |
| `multiInstanceRoleIds` | 会签角色 ID 列表 | 多选角色 | 角色下用户展开为用户列表 | 角色成员解析正确 |
| `collection` | 集合变量表达式 | 默认 `${_wfMultiInstanceUsers_}` | Flowable 多实例 `flowable:collection` 属性 | BPMN 中正确生成 `<multiInstanceLoopCharacteristics collection="_wfMultiInstanceUsers_">` |
| `collectionInterface` | 动态集合接口 | 如 `approverSelector.getApprovers` | 运行时调用接口获取用户 ID 列表 | 接口返回空/非列表时的处理 |
| `elementVariable` | 元素变量名 | 默认 `assignee` | 多实例迭代变量，用于 `flowable:assignee="${assignee}"` | 与任务执行人表达式一致 |
| `completionCondition` | 完成条件表达式 | 默认全部完成 | 满足时提前结束多实例 | 比例、人数、变量组合 |

**多实例验证步骤**：

1. `parallel` + 3 用户：发起流程后同时生成 3 个待办；全部通过后进入下一节点。
2. `parallel` + 1 人驳回：该人驳回后，其他未完成任务被终止，流程进入驳回分支。
3. `sequential` + 3 用户：先生成第 1 人待办，完成后生成第 2 人，依此类推；最后一人完成后进入下一节点。
4. `completionCondition` 设置为 `${nrOfCompletedInstances/nrOfInstances >= 0.5}`：4 人中 2 人通过后即结束多实例。
5. 集合来源为 `interface`：配置 `approverSelector.getApprovers`，返回 3 个用户，确认生成 3 个任务。
6. 集合为空：确认流程报错，不卡死。
7. 多实例 + 自定义 actionLabel：不同会签人选择不同动作标签，确认历史记录各自显示，不互相覆盖。

#### 5.3.3 表单配置

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `formSource` | 表单来源 | `entity`（实体表单）/`custom`（自定义表单）/`none`（无表单） | 决定节点显示什么表单 | 选择不同来源时界面变化 |
| `entityFormIds` | 实体表单 ID 列表 | 多选实体表单 | 绑定一个或多个实体表单到节点 | 字段正确加载；多表单按顺序展示 |
| `entityFormId` | 单个实体表单 ID（兼容字段） | 字符串 | 旧数据兼容 | 与 `entityFormIds` 一致性 |
| `formKey` | 自定义表单 Key | 字符串 | 外部表单标识 | 自定义表单渲染 |
| `isReadonly` | 是否只读 | `false`（默认） | `true` 时表单仅展示不可编辑 | 提交时跳过节点级字段校验 |

**表单配置验证步骤**：

1. `formSource=entity`，选择实体表单，确认节点审批时显示该表单字段。
2. `formSource=entity`，`isReadonly=true`，确认字段不可编辑，可正常提交。
3. `formSource=custom`，填写 `formKey`，确认前端按 `formKey` 渲染自定义表单。
4. `formSource=none`，确认节点无表单，仅显示审批操作。
5. 选择多个 `entityFormIds`，确认多表单按顺序展示。

#### 5.3.4 审批配置（approvalConfig）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `enabled` | 是否启用审批意见区域 | `true`（默认） | `false` 时隐藏审批意见输入框 | 前端不显示意见区 |
| `commentLabel` | 审批意见输入框标签 | `审批意见` | 前端展示文本 | 可自定义为“审批备注”等 |
| `options` | 审批操作选项数组 | 默认 `[{label:'通过',value:'approve',type:'primary',showComment:true,remarkRequired:false}, {label:'驳回',value:'reject',type:'danger',showComment:true,remarkRequired:false}]` | 决定审批按钮展示、流程变量 `approved` 取值、历史记录 actionLabel | 按钮样式、备注必填、分支条件 |
| `options[i].label` | 按钮显示文本 | 如 `通过`、`同意，需要会签` | 前端按钮文字、审批历史 actionLabel | 持久化到 `process_task.action_label` |
| `options[i].value` | 选项值（流程变量值） | 如 `approve`/`reject`/`needMeeting` | 写入 Flowable 变量 `approved`，供网关条件使用 | 网关条件按 value 判断 |
| `options[i].type` | 按钮样式 | `primary`/`success`/`warning`/`danger` | Element UI 按钮类型 | 视觉呈现正确 |
| `options[i].showComment` | 是否显示备注输入框 | `true`/`false` | 控制备注区显隐 | 选中该选项时显示/隐藏备注 |
| `options[i].remarkRequired` | 备注是否必填 | `true`/`false` | 备注为空时拒绝提交 | 仅当 `showComment=true` 时有效 |

**审批配置验证步骤**：

1. 默认配置：审批页面显示“通过”“驳回”两个按钮。
2. 添加自定义选项 `label=同意，需要会签`，`value=needMeeting`：
   - 按钮显示正确。
   - 点击后 `process_task.action=needMeeting`，`action_label=同意，需要会签`。
   - 网关条件 `${approved == 'needMeeting'}` 生效。
   - 审批历史显示“同意，需要会签”。
3. 设置 `remarkRequired=true`：未填备注时提交失败，提示“备注必填”。
4. 设置 `showComment=false`：该选项不显示备注输入框。
5. 设置 `enabled=false`：审批页面不显示意见输入框，但仍可提交。

#### 5.3.5 高级配置

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `async` | 是否启用异步执行 | `false`（默认） | `true` 时任务进入异步作业队列 | 适合长时间任务；需 Flowable 异步执行器激活 |
| `asyncBefore` | 进入节点前异步执行 | `false` | 在到达节点前生成异步作业 | 事务边界变化 |
| `asyncAfter` | 离开节点后异步执行 | `false` | 在离开节点后生成异步作业 | 事务边界变化 |
| `skipExpression` | 条件跳过表达式 | 如 `${skip}` | 表达式为 `true` 时跳过该节点 | 与 `skipNode` 互斥或叠加 |
| `skipNode` | 自动跳过标记 | `false` | `true` 时流程到达后自动完成 | 发布时生成 `flowable:skipExpression="${skipNodeEnabled}"` |

**高级配置验证步骤**：

1. `async=true` + `asyncBefore=true`：任务进入异步作业表 `ACT_RU_JOB`，异步执行器执行后生成待办。
2. `skipExpression=${amount < 1000}`：金额小于 1000 时自动跳过该审批节点。
3. `skipNode=true`：流程到达后自动完成。

#### 5.3.6 用户任务集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| UT-001 | 固定人员审批 | 用户任务指定 `user1` | `user1` 收到待办，其他人不可见 |
| UT-002 | 角色审批 | 指定 `role_manager` | 角色下所有用户收到待办 |
| UT-003 | 表达式执行人 | 设置 `${initiator}` | 任务分配给流程发起人 |
| UT-004 | 接口动态执行人 | Spring Bean 返回 `user1` | 任务分配给 `user1` |
| UT-005 | 候选人认领 | 设置 `user1`、`user2` 为候选人 | 两人均可见，一人认领后另一人不可见 |
| UT-006 | 会签全部通过 | parallel + 3 人 | 全部通过后进入下一节点 |
| UT-007 | 会签中驳回 | parallel + 3 人，第 2 人驳回 | 多实例终止，进入驳回分支 |
| UT-008 | 或签提前完成 | parallel + 2 人，1 人通过 | 另一任务自动完成 |
| UT-009 | 串行或签 | sequential + 3 人 | 按顺序生成待办 |
| UT-010 | 完成条件 | 4 人中 2 人通过即完成 | 满足条件后提前结束 |
| UT-011 | 表单只读 | `isReadonly=true` | 字段不可编辑，可正常提交 |
| UT-012 | 自定义审批选项 | 添加“同意，需要会签” | actionLabel 正确保存并显示 |
| UT-013 | 备注必填 | 设置 remarkRequired=true | 空备注拒绝提交 |
| UT-014 | 自动跳过 | `skipNode=true` | 节点自动完成 |
| UT-015 | 条件跳过 | `skipExpression=${amount < 1000}` | 满足条件时跳过 |

---

### 5.4 服务任务（ServiceTask）

服务任务用于自动执行 Java 类、Spring Bean、表达式或 REST 接口。

#### 5.4.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `implementationType` | 实现类型 | `class`（Java 类）/`expression`（表达式）/`delegateExpression`（Spring Bean）/`rest`（REST 接口） | 决定调用方式 | 类型选择后表单变化 |
| `implementation` | 实现值 | 类全名 / 表达式 / Bean 名 | 具体执行目标 | 类/Bean 存在性校验 |
| `resultVariable` | 结果变量名 | 空 | 非 REST 类型时存储返回值到流程变量 | 变量可在后续节点使用 |
| `restForm.method` | REST 请求方式 | `GET`/`POST`/`PUT`/`DELETE` | HTTP 方法 | 服务端路由匹配 |
| `restForm.url` | 请求 URL | 支持 `${variable}` 表达式 | 请求地址 | 变量替换正确 |
| `restForm.contentType` | Content-Type | `application/json`（默认）/`application/x-www-form-urlencoded`/`multipart/form-data`/`text/xml` | 请求头 | 服务端解析正确 |
| `restForm.headers` | 请求头 JSON | 支持 `${variable}` 表达式 | 附加头信息 | 认证、TraceId 传递 |
| `restForm.body` | 请求体 | JSON 或表单格式，支持 `${variable}` | POST/PUT 数据 | 变量替换、JSON 格式 |
| `restForm.queryParams` | 查询参数 JSON | 支持 `${variable}` | URL 查询参数 | 编码处理 |
| `restForm.timeout` | 超时秒数 | 默认 30 | 请求超时时间 | 超时后按错误处理策略执行 |
| `restForm.retryCount` | 重试次数 | 默认 0 | 失败后重试次数 | 重试间隔与幂等性 |
| `restForm.errorHandling` | 错误处理策略 | `throw`（抛异常终止流程）/`continue`（记录错误继续）/`ignore`（忽略） | 失败后的流程行为 | 事务回滚/继续 |
| `restForm.resultMapping` | 结果映射 JSON | 如 `{"data.id":"userId"}` | 将响应字段映射到流程变量 | 嵌套字段提取 |

#### 5.4.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| ST-001 | Java 类服务任务 | `implementationType=class`，实现类实现 `JavaDelegate` | 流程到达时自动执行，无待办生成 |
| ST-002 | Spring Bean 服务任务 | `implementationType=delegateExpression`，Bean 名 `myService` | Bean 方法被调用 |
| ST-003 | 表达式服务任务 | `implementationType=expression`，如 `${myBean.doSomething()}` | 表达式执行 |
| ST-004 | REST GET 成功 | 配置 GET URL，返回 JSON | 请求发送，结果按映射写入变量 |
| ST-005 | REST POST 成功 | 配置 POST URL、Body、Headers | 请求发送，服务端收到正确数据 |
| ST-006 | REST 超时 | 配置 timeout=1，服务端延迟 5 秒 | 超时后按 errorHandling 处理 |
| ST-007 | REST 失败 throw | `errorHandling=throw`，服务端返回 500 | 流程实例终止或进入异常分支 |
| ST-008 | REST 失败 continue | `errorHandling=continue` | 记录错误，流程继续 |
| ST-009 | REST 结果映射 | 响应 `{"data":{"id":"123"}}`，映射 `data.id` → `userId` | `userId=123` |
| ST-010 | 服务任务异常事务 | 服务任务抛异常 | 审批操作与动作调用同事务回滚 |

---

### 5.5 脚本任务（ScriptTask）

脚本任务用于执行轻量级脚本逻辑。

#### 5.5.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `scriptFormat` | 脚本语言 | `javascript`（Nashorn）/`groovy`/`python` | 脚本引擎选择 | 不同语言语法支持 |
| `script` | 脚本内容 | 代码字符串 | 执行逻辑 | 语法错误处理 |
| `resultVariable` | 结果变量名 | 空 | 最后一行表达式结果存储到该变量 | Groovy 中最后一行表达式自动返回 |
| `autoStoreVariables` | 自动存储脚本变量 | `false` | `true` 时脚本变量写入流程上下文 | 变量作用域 |

#### 5.5.2 脚本语言注意事项

- **JavaScript（Nashorn）**：避免使用 `var` 声明变量（var 为局部变量不会返回），直接赋值如 `result = a + b;` 可被 `resultVariable` 捕获。
- **Groovy**：支持 `?:` Elvis 运算符，最后一行表达式自动返回给 `resultVariable`。
- **Python**：Flowable 内嵌 Python 支持有限，避免复杂第三方库；在线测试暂不支持 Python。

#### 5.5.3 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SC-001 | JavaScript 脚本 | `scriptFormat=javascript`，计算总价 | `resultVariable` 获得计算结果 |
| SC-002 | Groovy 脚本 | `scriptFormat=groovy`，使用 Elvis 运算符 | 正确执行并返回 |
| SC-003 | 脚本语法错误 | 脚本中有语法错误 | 流程报错，不继续执行 |
| SC-004 | 脚本修改变量 | 使用 `execution.setVariable('flag', true)` | 流程变量被修改 |
| SC-005 | 自动存储变量 | `autoStoreVariables=true` | 脚本变量写入流程上下文 |
| SC-006 | 脚本测试功能 | 设计器中点击“测试执行” | 返回成功结果和变量值 |
| SC-007 | Python 脚本 | `scriptFormat=python` | 视环境支持情况，Unsupported 时给出提示 |

---

### 5.6 发送任务（SendTask）

发送任务用于发送邮件、短信、站内信通知。

#### 5.6.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `channels` | 发送渠道数组 | `email`/`sms`/`message` | 决定通过哪些渠道发送 | 可多选 |
| `to` | 接收人 | 支持 `${variable}` 表达式或具体地址 | 消息接收目标 | 变量解析为空时处理 |
| `subject` | 消息标题 | 字符串 | 邮件/站内信标题 | 变量替换 |
| `content` | 消息内容 | 支持 `${variable}` 表达式 | 消息正文 | 变量替换 |
| `templateKey` | 消息模板 | `PROCESS_SUBMIT`/`APPROVE_PASS`/`APPROVE_REJECT` | 使用预定义模板 | 模板存在性 |

#### 5.6.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SD-001 | 发送站内信 | 配置 `channels=[message]`，`to=${submitterId}` | 发起人收到站内信 |
| SD-002 | 发送邮件 | 配置 `channels=[email]`，填写邮箱 | 邮件发送成功（需邮件服务配置） |
| SD-003 | 多渠道发送 | 配置 `channels=[email,sms,message]` | 三个渠道均发送 |
| SD-004 | 消息模板 | 选择 `APPROVE_PASS` | 使用预定义模板内容 |
| SD-005 | 变量替换 | `content="流程 ${processName} 已提交"` | 变量被替换为实际值 |
| SD-006 | 接收人为空 | `to` 解析为空 | 记录警告，流程继续或报错（视实现） |

---

### 5.7 接收任务（ReceiveTask）

接收任务使流程暂停，等待外部消息触发后继续。

#### 5.7.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `messageRef` | 消息名称 | 如 `paymentCallback` | 外部系统发送同名消息触发流程继续 | 消息名称匹配 |
| `hasTimeout` | 是否启用超时 | `false` | `true` 时等待超过指定时间触发超时处理 | 超时机制 |
| `timeout` | 超时时间数值 | 正整数 | 等待时长 | 与 `timeoutUnit` 组合 |
| `timeoutUnit` | 时间单位 | `MINUTE`/`HOUR`/`DAY` | 单位换算 | 总秒数计算 |
| `timeoutAction` | 超时处理 | `error`（抛异常）/`continue`（继续执行） | 超时后行为 | 异常/继续 |

#### 5.7.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| RT-001 | 正常接收消息 | 流程到达接收任务，外部发送 `paymentCallback` 消息 | 流程继续执行 |
| RT-002 | 接收错误消息 | 外部发送不同名称消息 | 流程仍等待 |
| RT-003 | 超时继续 | 设置 1 分钟超时，`timeoutAction=continue` | 超时后流程继续 |
| RT-004 | 超时抛异常 | 设置 1 分钟超时，`timeoutAction=error` | 超时后流程异常终止 |
| RT-005 | 超时不生效 | 在超时前收到消息 | 正常继续，不触发超时 |

---

### 5.8 手动任务（ManualTask）

手动任务标记需要在流程系统外完成的工作，仅作记录，不生成待办。

#### 5.8.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `description` | 任务描述 | 字符串 | 线下工作说明 | 流程图 tooltip/历史展示 |
| `completionCriteria` | 完成条件 | 字符串 | 判断标准 | 仅记录 |
| `responsible` | 负责人 | 字符串 | 记录责任人 | 不生成待办 |
| `estimatedHours` | 预计工时 | 数值 | 工时估算 | 统计报表 |

#### 5.8.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| MT-001 | 手动任务记录 | 流程到达手动任务 | 流程历史记录该节点，不生成待办 |
| MT-002 | 负责人记录 | 填写 `responsible` | 历史显示负责人 |
| MT-003 | 流程继续 | 手动任务后连接用户任务 | 手动任务自动通过，进入下一节点 |

---

### 5.9 业务规则任务（BusinessRuleTask）

业务规则任务执行 DMN 决策表。

#### 5.9.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `decisionRef` | 决策表 Key | 如 `approvalLevelDecision` | 关联 DMN 决策表定义 | 决策表存在性 |
| `inputVariables` | 输入变量映射 JSON | 如 `{"amount":"${amount}"}` | 传递给决策表的输入变量 | 变量类型匹配 |
| `resultVariable` | 结果变量名 | 如 `decisionResult` | 存储决策结果 | 结果结构 |
| `mapDecisionResult` | 是否映射结果 | `true`/`false` | `true` 时将决策结果字段映射到流程变量 | 字段映射 |

#### 5.9.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| BR-001 | 决策表执行 | 配置决策表，输入金额和部门 | 输出审批级别 |
| BR-002 | 结果映射 | `mapDecisionResult=true` | 决策结果字段写入流程变量 |
| BR-003 | 决策表不存在 | 配置不存在的 `decisionRef` | 流程报错 |
| BR-004 | 输入变量缺失 | 未传递决策表所需变量 | 决策表执行失败或返回默认值 |

---

### 5.10 调用活动（CallActivity）

调用活动调用另一个独立的 BPMN 子流程，实现流程模块化复用。

#### 5.10.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `calledElement` | 子流程 Key | 已发布流程的 `process_key` | 被调用的子流程 | 流程存在且已发布 |
| `callActivityType` | 调用方式 | `bpmn`（默认）/`cmmn` | BPMN 子流程或 CMMN 案例 | CMMN 需引擎支持 |
| `inputParameters` | 输入参数映射 JSON | 如 `{"subVar":"${parentVar}"}` | 父流程变量传递给子流程 | 变量映射正确 |
| `outputParameters` | 输出参数映射 JSON | 如 `{"parentResult":"${subResult}"}` | 子流程变量返回给父流程 | 变量映射正确 |
| `businessKey` | 业务 Key | 字符串 | 子流程业务标识 | 子流程实例可通过 businessKey 查询 |

#### 5.10.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| CA-001 | 调用 BPMN 子流程 | 配置 `calledElement` 为已发布子流程 | 子流程启动并执行，完成后返回父流程 |
| CA-002 | 参数传递 | 配置 `inputParameters` 和 `outputParameters` | 子流程收到父流程变量，返回结果写入父流程 |
| CA-003 | 子流程未发布 | `calledElement` 指向草稿或禁用流程 | 父流程报错 |
| CA-004 | 子流程独立版本 | 子流程发布后修改并重新发布 | 父流程调用最新版本 |

---

### 5.11 排他网关（ExclusiveGateway）

排他网关根据条件表达式选择唯一分支。

#### 5.11.1 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `conditionList` | 顺序流条件列表 | 数组 | 多条件组合判断 | 逻辑运算符 |
| `condition[i].property` | 比较属性 | `approved` 或实体字段名 | 被比较字段 | 字段存在性 |
| `condition[i].operator` | 操作符 | `==`/`!=`/`>`/`<`/`>=`/`<=`/`contains` | 比较方式 | 类型匹配 |
| `condition[i].value` | 比较值 | 字符串/数字 | 阈值 | 引号处理 |
| `condition[i].logic` | 条件间逻辑关系 | `&&`/`||` | 与/或组合 | 优先级 |
| `type` | 连线条件类型 | 空（无条件）/`expression`/`default` | 无条件连线始终通过；default 为默认流 | 一个排他网关只能有一个 default |

#### 5.11.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| EG-001 | 条件通过分支 | 设置 `${approved == 'approve'}` | 通过时走该分支 |
| EG-002 | 条件驳回分支 | 设置 `${approved == 'reject'}` | 驳回时走该分支 |
| EG-003 | 默认流 | 一个分支设为 default | 其他条件不满足时走 default |
| EG-004 | 多条件组合 | `approved == 'approve' && amount > 1000` | 同时满足才走该分支 |
| EG-005 | 条件字段为实体字段 | 如 `${amount > 1000}` | 按实体数据字段判断 |
| EG-006 | 非法表达式 | 编写语法错误的 EL 表达式 | 发布或执行时报错 |
| EG-007 | 布尔表达式兼容 | 旧流程 `${approved == true}` | 发布时自动迁移为 `${approved == 'approve'}` |

---

### 5.12 并行网关（ParallelGateway）

并行网关将流程分成多条并行分支，所有分支完成后汇聚。

#### 5.12.1 配置项

并行网关本身无特殊配置项，依赖 BPMN 拓扑结构。

#### 5.12.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| PG-001 | 并行分支 | 一个并行网关分叉为 2 个用户任务 | 两个任务同时生成 |
| PG-002 | 汇聚等待 | 一个并行网关汇聚 2 个分支 | 两个任务都完成后才汇聚 |
| PG-003 | 并行 + 会签 | 并行分支中再包含会签节点 | 各分支独立执行，全部完成后汇聚 |

---

### 5.13 包容网关（InclusiveGateway）

包容网关根据条件选择一条或多条分支执行，所有被选中分支完成后汇聚。

#### 5.13.1 配置项

与排他网关类似，使用 `conditionList`、`type` 等字段。

#### 5.13.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| IG-001 | 单条件满足 | 两个分支条件，仅一个满足 | 执行一个分支 |
| IG-002 | 多条件满足 | 两个分支条件都满足 | 两个分支都执行 |
| IG-003 | 默认分支 | 所有条件不满足 | 走 default 分支 |
| IG-004 | 汇聚 | 两个分支都执行 | 都完成后汇聚 |

---

### 5.14 事件网关（EventBasedGateway）

事件网关等待多个事件中的任意一个触发后流程继续。

#### 5.14.1 配置项

事件网关本身无特殊配置项，依赖后续事件节点（接收任务、消息边界事件等）。

#### 5.14.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| EBG-001 | 任一消息触发 | 事件网关后连接两个接收任务 | 收到任一消息后流程继续，另一事件取消 |
| EBG-002 | 超时事件 | 配置定时边界事件 | 超时后按超时分支执行 |

---

### 5.15 子流程（SubProcess）

子流程将一组节点封装为一个可折叠的单元。

#### 5.15.1 配置项

子流程主要依赖 BPMN 结构，设计器支持嵌套绘制。

#### 5.15.2 集成测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SP-001 | 嵌套子流程 | 在子流程中绘制用户任务和结束事件 | 子流程内节点按顺序执行 |
| SP-002 | 子流程异常 | 子流程内服务任务抛异常 | 子流程回滚，父流程按异常处理 |
| SP-003 | 子流程变量作用域 | 子流程内设置变量 | 变量作用域限于子流程（默认） |

---

## 5.16 节点、实体、表单关系与展示方式（重点细化）

### 5.16.1 核心概念与层级关系

工作流平台的表单体系采用“实体表单设计 → 节点表单绑定 → 运行时表单渲染”三层结构：

```
Entity（实体定义）
├── EntityForm（实体表单，一个实体可设计多个表单）
│   ├── EntityFormField（表单字段，引用 EntityField 的元数据）
│   │   ├── 字段基本属性：fieldCode、fieldName、fieldLabel、fieldType
│   │   ├── 表单级权限：isRequired、isReadonly、isHidden
│   │   ├── 展示属性：componentType、componentProps、gridSpan、displayMode
│   │   └── 关联属性：refEntityType、refEntityId、relation（子表单关系）
│   └── 表单属性：formName、formKey、layoutType、isDefault、customComponent
│
ProcessNodeForm（流程节点表单绑定）
├── 关联 processConfigId + nodeId + formId
├── isReadonly：节点级只读覆盖
└── sortOrder：同一节点绑定多个表单时的排序
```

**关键关系说明**：

1. **一对多**：一个 `Entity` 可创建多个 `EntityForm`，用于不同场景（申请单、审批单、详情页）。
2. **多对多**：一个 `EntityForm` 可被绑定到多个流程节点的 `ProcessNodeForm`；一个节点也可绑定多个表单。
3. **运行时合并**：节点绑定多个表单时，后端返回 `formConfigs` 数组，前端通过 `mergeRuntimeFormConfigs()` 合并为一个虚拟表单展示。
4. **字段去重**：合并时按 `fieldCode` 去重，相同字段只展示一次。
5. **字段排序**：合并后的字段顺序 = `表单序号 × 10000 + 字段在表单内的 sortOrder`。

### 5.16.2 实体表单设计（EntityForm / EntityFormField）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `formName` | 表单名称 | 如 `请假申请表单` | 前端表单标题、Tab 标签 | 修改后同步 |
| `formKey` | 表单唯一标识 | 英文，如 `leave_apply` | 自定义表单路由、外部表单关联 | 唯一性 |
| `layoutType` | 表单布局 | `vertical`（垂直）/`horizontal`（水平）/`grid`（网格） | 表单整体布局 | 不同布局渲染正确 |
| `isDefault` | 是否默认表单 | `false` | 节点未绑定时回退到默认表单 | 只有一个默认 |
| `customComponent` | 自定义组件注册名 | 可选 | 使用完全自定义的表单组件 | 组件存在性 |
| `initConfig` | 表单初始化配置 JSON | 可选 | 表单加载时执行初始化逻辑 | JSON 合法性 |
| `fieldCode` | 字段编码 | 对应 `entity_field.field_code` | 数据绑定 key | 一致性 |
| `fieldLabel` | 字段显示标签 | 如 `报销金额` | 表单 label | 可覆盖 `entity_field` 的字段名 |
| `componentType` | 组件类型 | `input`/`select`/`date`/`number`/`sub_form` 等 | 前端渲染组件 | 与 `fieldType` 匹配 |
| `componentProps` | 组件额外配置 JSON | 如 `{"placeholder":"请输入"}` | 组件属性 | 正确解析 |
| `isRequired` | 表单级必填 | `0`/`1` | 提交时校验 | 与实体字段必填叠加 |
| `isReadonly` | 表单级只读 | `0`/`1` | 字段不可编辑 | 节点级只读可覆盖 |
| `isHidden` | 表单级隐藏 | `0`/`1` | 字段不展示 | 隐藏字段不提交校验 |
| `gridSpan` | 栅格宽度 | `1~24` | 字段占用的列宽 | 布局正确 |
| `displayMode` | 子表单显示方式 | `embedded`（嵌入）/`tab`（Tab 页） | 子表单在前端的组织形式 | 见 5.16.5 |

### 5.16.3 流程节点表单绑定（ProcessNodeForm）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `processConfigId` | 所属流程定义配置 ID | 必填 | 关联流程 | 删除流程时级联 |
| `nodeId` | BPMN 节点 ID | 必填 | 关联到具体节点 | 与 BPMN 中 id 一致 |
| `nodeName` | 节点名称快照 | 保存时的节点名 | 便于查看 | 修改节点名后此处是否同步 |
| `formId` | 绑定的实体表单 ID | 必填 | 节点展示哪个实体表单 | 表单存在性 |
| `isReadonly` | 节点级只读覆盖 | `0`/`1` | `1` 时该节点此表单所有字段只读，覆盖表单级 `isReadonly` | 优先级最高 |
| `sortOrder` | 排序号 | 整数 | 同一节点多个表单时的展示顺序 | 数值越小越靠前 |

**绑定关系验证点**：

1. 一个节点绑定 0 个表单：运行时回退到实体默认表单；无默认表单时回退到第一个可用表单。
2. 一个节点绑定 1 个表单：直接展示该表单。
3. 一个节点绑定 2 个表单：后端返回 2 个 `FormConfigDTO`，前端合并展示；重复字段去重；表单名称用 ` / ` 连接。
4. 同一表单绑定到多个节点：每个节点独立设置 `isReadonly`，互不影响。

### 5.16.4 表单加载优先级（运行时）

流程进度接口 `/process-instance/{id}/progress` 加载表单时，按以下优先级：

1. **最高优先级**：从流程发布快照查询当前节点的 `ProcessNodeForm` 绑定。
2. **回退 1**：节点无绑定时，使用实体的默认表单（`isDefault=true`）。
3. **回退 2**：无默认表单时，使用实体的第一个可用表单。
4. **回退 3**：仍无表单时，流程详情页不展示表单数据（仅展示流程图和历史）。

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| FLP-001 | 节点绑定表单 | 为节点 A 绑定表单 F1 | 流程到达 A 时展示 F1 |
| FLP-002 | 节点未绑定表单 | 节点 A 不绑定表单，实体有默认表单 F2 | 流程到达 A 时展示 F2 |
| FLP-003 | 无默认表单 | 节点 A 不绑定表单，实体无默认表单但有 F3 | 流程到达 A 时展示 F3 |
| FLP-004 | 完全无表单 | 实体无任何表单 | 详情页不展示表单，仅展示流程图和历史 |
| FLP-005 | 发布后修改表单 | 发布后修改实体表单字段 | 旧流程实例仍使用发布快照中的表单定义 |

### 5.16.5 多表单绑定与合并展示

#### 合并规则

后端返回 `formConfigs: [FormConfigDTO, ...]`，前端 `mergeRuntimeFormConfigs()` 执行：

1. **去重**：按 `fieldCode` 去重，第一个表单中的字段保留，后续重复字段忽略。
2. **排序**：字段排序值 = `formIndex × 10000 + field.sortOrder`。
3. **表单名连接**：多个表单的 `formName` 用 ` / ` 连接，作为合并后表单的标题。
4. **只读覆盖**：每个表单独立维护自己的 `isReadonly`（来自 `ProcessNodeForm.isReadonly`），合并后字段的只读状态已在后端处理。

#### 展示方式

| 场景 | 前端组件 | 展示效果 |
|---|---|---|
| 单表单、无 Tab 子表单 | `FormPreviewLinkage` | 平铺展示所有字段 |
| 单表单、含 Tab 子表单 | `FormPreviewLinkage` | 普通字段在“基本信息”Tab，Tab 子表单各一个 Tab |
| 多表单、无 Tab 子表单 | `FormPreviewLinkage` | 合并后的字段按排序平铺展示 |
| 多表单、含 Tab 子表单 | `FormPreviewLinkage` | 合并后的普通字段在“基本信息”Tab，Tab 子表单各一个 Tab |
| 审批弹窗外部接管 Tab | `EntityApprovalDialog` | `noInternalTabs=true`，基本信息一个 Tab，子表单各一个 Tab，再加“流程图”“审批历史”Tab |
| 自定义组件表单 | `customComponent` | 完全由自定义组件渲染 |

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| FM-001 | 同一节点绑定两个表单 | 节点 A 绑定 F1（字段 a,b）和 F2（字段 c,d） | 审批页展示 a,b,c,d 四个字段 |
| FM-002 | 多表单字段去重 | F1 和 F2 都包含字段 a | 字段 a 只展示一次 |
| FM-003 | 多表单字段排序 | F1.sortOrder=1, F2.sortOrder=2；字段 b 在 F2 中 sortOrder=1 | 最终顺序：a(F1) < b(F2) < c(F1) < d(F2) |
| FM-004 | 多表单表单名连接 | F1 名“基本信息”，F2 名“扩展信息” | 表单标题显示“基本信息 / 扩展信息” |
| FM-005 | 多表单只读覆盖 | F1 节点绑定 readonly=false，F2 节点绑定 readonly=true | F1 字段可编辑，F2 字段只读（各自字段独立） |

### 5.16.6 字段权限叠加规则

字段最终状态由多层配置共同决定，优先级从低到高：

```
1. 实体字段定义（EntityField）
   └── isRequired / isUnique / defaultValue
2. 实体表单字段（EntityFormField）
   └── isRequired / isReadonly / isHidden / fieldLabel / componentType
3. 节点表单绑定（ProcessNodeForm）
   └── isReadonly（节点级覆盖，最高优先级）
4. 运行时联动引擎（LinkageEngine）
   └── visibility / disabled / required / options（动态控制）
```

**详细规则**：

| 配置来源 | `isRequired` | `isReadonly` | `isHidden` |
|---|---|---|---|
| `EntityField.isRequired` | 基础必填 | - | - |
| `EntityFormField.isRequired` | 叠加：任一必填则必填 | - | 控制隐藏 |
| `ProcessNodeForm.isReadonly` | - | 节点级覆盖为只读 | - |
| `LinkageEngine` | 动态必填可覆盖 | 动态禁用可叠加 | 动态隐藏可叠加 |

**特殊规则**：

- 引用实体字段（`refEntityType` 为 `USER`/`DEPT`/`ROLE`/`GROUP`/`CUSTOM`）即使 `isReadonly=1`，在表单中仍可选择（用于展示和回填），但不可修改已选值。具体行为见前端 `isFieldDisabled` 逻辑。
- 节点级 `isReadonly=1` 时，所有字段（包括子表单）均不可编辑；但引用字段通常仍可打开选择器查看。
- 表单级 `isHidden=1` 的字段不展示、不参与校验、不提交。

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| FP-001 | 实体必填 + 表单非必填 | `EntityField.isRequired=true`，`EntityFormField.isRequired=0` | 字段必填 |
| FP-002 | 实体非必填 + 表单必填 | `EntityField.isRequired=false`，`EntityFormField.isRequired=1` | 字段必填 |
| FP-003 | 节点只读覆盖 | `EntityFormField.isReadonly=0`，`ProcessNodeForm.isReadonly=1` | 字段只读 |
| FP-004 | 表单隐藏 | `EntityFormField.isHidden=1` | 字段不展示 |
| FP-005 | 引用字段只读 | 用户选择字段 `isReadonly=1` | 字段显示当前用户，可打开选择器但不可变更 |
| FP-006 | 联动隐藏 | 配置“当类型=A 时隐藏金额字段” | 类型=A 时金额字段隐藏 |
| FP-007 | 联动必填 | 配置“当类型=B 时备注必填” | 类型=B 时备注必填 |
| FP-008 | 联动禁用 | 配置“当状态=完成时禁用备注” | 状态=完成时备注不可编辑 |

### 5.16.7 子表单/关联实体展示方式

子表单字段通过 `EntityRelation` 定义父子实体关系，通过 `EntityFormField.displayMode` 控制展示。

#### 配置项

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `refEntityType` | 引用实体类型 | `CUSTOM`（自定义实体）/`USER`/`DEPT`/`ROLE`/`GROUP` | 决定引用数据来源 | 对应数据加载 |
| `refEntityId` | 关联实体 ID | 实体定义 ID | 自定义子实体时关联 | 实体存在性 |
| `displayMode` | 子表单显示方式 | `embedded`（嵌入）/`tab`（Tab 页） | 前端组织形式 | 两种模式切换 |
| `relation.type` | 关系类型 | `ONE_TO_ONE`/`ONE_TO_MANY` | 子表数据条数限制 | 一对一只能有一条 |
| `relation.childEntityCode` | 子实体编码 | 对应动态表名 | 子表数据存储 | 一致性 |
| `relation.childRefFieldCode` | 子表外键字段 | 子实体字段编码 | 关联父记录 | 数据回填 |
| `relation.cascadeDelete` | 级联删除 | `true`/`false` | 删除父记录时是否删除子记录 | 级联行为 |
| `relation.required` | 子表单必填 | `true`/`false` | 父记录提交时子表必须有数据 | 校验 |

#### 展示效果

| `displayMode` | 适用场景 | 前端效果 |
|---|---|---|
| `embedded` | 子表字段少、需要与主表一起填写 | 子表直接嵌入在主表单中，可增删行 |
| `tab` | 子表字段多、需要分 Tab 展示 | 子表作为一个独立 Tab 页，标签为 `fieldName` |

#### 数据回填

- 新建父记录时：子表为空，显示“新增”按钮。
- 编辑父记录时：根据 `childRefFieldCode = parentId` 查询子表数据，回填到子表单。
- 审批查看时：子表数据只读展示。

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SF-001 | 嵌入子表单 | 子表字段 displayMode=embedded | 子表直接嵌入主表单 |
| SF-002 | Tab 子表单 | 子表字段 displayMode=tab | 子表作为独立 Tab 展示 |
| SF-003 | 子表数据回填 | 编辑已有父记录 | 子表自动加载关联数据 |
| SF-004 | 子表必填校验 | relation.required=true，子表为空 | 提交失败，提示子表必填 |
| SF-005 | 子表级联删除 | cascadeDelete=true，删除父记录 | 子表关联数据同步删除 |
| SF-006 | 一对一子表 | relation.type=ONE_TO_ONE | 子表只能新增一行 |
| SF-007 | 一对多子表 | relation.type=ONE_TO_MANY | 子表可新增多行 |
| SF-008 | 引用用户字段 | refEntityType=USER | 用户选择器加载系统用户 |
| SF-009 | 引用部门字段 | refEntityType=DEPT | 部门选择器加载组织架构 |

### 5.16.8 开始事件表单绑定

开始事件可绑定表单，用于流程发起时填写申请信息。

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `formSource` | 表单来源 | `entity`/`custom`/`none` | 同用户任务 | 开始事件通常用实体表单 |
| `entityFormIds` | 实体表单 ID 列表 | 多选 | 发起时展示 | 同用户任务 |

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| SEF-001 | 开始事件绑定表单 | 开始事件选择实体表单 | 发起流程时先显示表单填写页 |
| SEF-002 | 开始事件无表单 | 开始事件不绑定表单 | 直接发起流程，进入第一个任务 |
| SEF-003 | 发起数据保存 | 填写开始表单并提交 | 实体数据表插入记录，并启动流程 |

### 5.16.9 自定义组件表单

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `customComponent` | 自定义组件注册名 | 如 `CustomLeaveForm` | 完全由自定义组件渲染表单 | 组件已在 `customComponentRegistry.js` 注册 |

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| CF-001 | 自定义组件渲染 | 表单配置 customComponent | 使用自定义组件渲染，不走默认 FormPreviewLinkage |
| CF-002 | 自定义组件不存在 | 配置未注册的组件名 | 前端报错或回退到默认表单 |
| CF-003 | 自定义组件只读 | `readonly=true` | 自定义组件接收 readonly 属性并展示只读态 |

### 5.16.10 表单数据快照与版本控制

流程发布后，表单定义被快照到 `entity_publish_history` / 流程发布快照中。已启动的流程实例始终使用发布时的表单定义，不受后续实体表单修改影响。

**测试验证**：

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| FS-001 | 发布后修改表单 | 流程发布后将字段 A 改为只读 | 旧流程实例中字段 A 仍按发布时状态（可编辑） |
| FS-002 | 新版本使用新表单 | 修改表单后重新发布流程 | 新流程实例使用新表单定义 |
| FS-003 | 历史详情展示 | 查看已结束流程的详情 | 展示发布时的表单字段和数据 |

### 5.16.11 常见页面展示场景对照

| 页面 | 表单来源 | 展示组件 | 说明 |
|---|---|---|---|
| 数据新增/编辑页 | 实体默认表单 | `EntityDataFormFields` → `FormPreviewLinkage` | 可编辑模式 |
| 流程发起页 | 开始事件绑定表单 | `EntityDataFormFields` | 填写后保存并发起流程 |
| 待办审批弹窗 | 当前节点绑定的表单 | `EntityApprovalDialog` → `EntityApprovalBasicInfo` + `FormPreviewLinkage` | 可能只读，取决于 `ProcessNodeForm.isReadonly` |
| 流程详情页 | 当前/最近节点表单 | `useProcessDetail` → `FormPreviewLinkage` | 只读展示 |
| 审批历史 | 无表单，仅展示节点处理记录 | `EntityApprovalHistory` | 展示 actionLabel、comment、时间 |
| 流程图 | 无表单 | `VueBpmnViewer` / `EntityApprovalDiagram` | 高亮节点、tooltip |

### 5.16.12 节点-实体-表单关系集成测试用例汇总

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| NEF-001 | 实体创建多个表单 | 实体下创建 F1、F2，设置 F1 为默认 | F1.isDefault=true，F2.isDefault=false |
| NEF-002 | 节点绑定一个表单 | 节点 A 绑定 F1 | 到达 A 时展示 F1 字段 |
| NEF-003 | 节点绑定两个表单 | 节点 A 绑定 F1、F2 | 合并展示，去重、排序、名称连接 |
| NEF-004 | 节点未绑定表单 | 节点 A 不绑定，实体有默认表单 | 回退到默认表单 |
| NEF-005 | 节点只读覆盖 | ProcessNodeForm.isReadonly=1 | 节点 A 所有字段只读 |
| NEF-006 | 表单字段隐藏 | EntityFormField.isHidden=1 | 字段不展示 |
| NEF-007 | 字段权限叠加 | 实体必填+表单非必填 | 字段必填 |
| NEF-008 | 子表嵌入展示 | displayMode=embedded | 子表嵌入主表单 |
| NEF-009 | 子表 Tab 展示 | displayMode=tab | 子表作为 Tab 页 |
| NEF-010 | 子表数据回填 | 编辑已有数据 | 子表自动加载关联记录 |
| NEF-011 | 引用字段只读 | 用户字段 isReadonly=1 | 显示当前用户，选择器不可变更 |
| NEF-012 | 自定义组件表单 | customComponent=CustomForm | 使用自定义组件渲染 |
| NEF-013 | 表单版本快照 | 发布后修改表单 | 旧实例使用旧快照 |
| NEF-014 | 联动隐藏 | 配置字段联动规则 | 条件满足时字段隐藏 |
| NEF-015 | 联动必填 | 配置字段联动规则 | 条件满足时字段必填 |
| NEF-016 | 多布局类型 | layoutType=grid | 表单按网格布局展示 |

---

### 5.16.13 字段联动规则配置与验证

#### 联动规则类型

| 联动类型 | 配置字段 | 含义 | 典型场景 |
|---|---|---|---|
| 显隐控制 | `visibilityRule` / `visibilityConditions` | 满足条件时显示字段，否则隐藏 | 根据类型显示不同字段 |
| 禁用控制 | `disabledRule` / `disabledCondition` | 满足条件时禁用字段 | 状态锁定后不可修改 |
| 必填控制 | `requiredRule` / `requiredCondition` | 满足条件时字段必填 | 紧急类型必须填备注 |
| 值联动 | `valueMapping` / `valueFormula` | 根据其他字段值自动填充 | 选择省份后自动填充城市 |
| 计算字段 | `calculationFormula` / `calculationPrecision` | 按公式自动计算 | 数量 × 单价 = 总价 |
| 选项联动 | `optionsLinkage` / `optionsDependField` | 根据依赖字段过滤选项 | 选择省份后过滤城市选项 |
| 事件脚本 | `eventOnChange` / `eventOnBlur` / `eventOnFocus` | 字段事件触发自定义脚本 | 失焦时校验格式 |

#### 联动规则存储方式

联动规则可存储在以下位置，运行时按优先级合并：

1. 字段根属性：`visibilityRule`、`disabledRule`、`requiredRule` 等
2. `linkageRules` 对象：内存中临时保存的完整联动配置
3. `componentProps.linkageRules`：设计器保存的 JSON 格式联动规则

#### 条件表达式规则

- 支持 `${fieldCode}` 引用字段值
- 支持 `==`、`!=`、`>`、`<`、`>=`、`<=`、`contains` 等操作符
- 支持 `&&`、`||` 逻辑组合
- 字符串值会自动加引号
- 表达式安全评估使用白名单，禁止危险字符和函数调用

#### 计算字段规则

- 公式中 `${fieldCode}` 会被替换为数值，非数值按 0 处理
- 支持 `+`、`-`、`*`、`/`、`(`、`)`
- 可配置 `calculationPrecision` 小数位
- `calculationEditable=false` 时用户不可修改计算结果

#### 选项联动规则

```json
{
  "dependsOn": "province",
  "filterRules": {
    "beijing": ["chaoyang", "haidian"],
    "shanghai": ["pudong", "xuhui"]
  }
}
```

#### 联动测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| LK-001 | 显隐控制-单条件 | 配置：当 `type == 'urgent'` 时显示 `remark` | type=urgent 时 remark 显示；type=normal 时隐藏 |
| LK-002 | 显隐控制-多条件且 | 配置：当 `type == 'urgent' && amount > 1000` 时显示 | 两个条件同时满足才显示 |
| LK-003 | 显隐控制-多条件或 | 配置：当 `type == 'urgent' || amount > 1000` 时显示 | 任一条件满足即显示 |
| LK-004 | 禁用控制 | 配置：当 `status == 'locked'` 时禁用 `amount` | status=locked 时 amount 不可编辑 |
| LK-005 | 必填控制 | 配置：当 `type == 'urgent'` 时 `remark` 必填 | type=urgent 时未填 remark 拒绝提交 |
| LK-006 | 值联动-字段映射 | 配置：当 `province=beijing` 时 `city` 填充为 `chaoyang` | 选择北京后城市自动填充 |
| LK-007 | 值联动-计算公式 | 配置：`total = ${amount} * ${price}` | amount 或 price 变化时 total 自动计算 |
| LK-008 | 计算字段精度 | 配置：`total = ${a} / ${b}`，精度 2 | 结果显示两位小数 |
| LK-009 | 计算字段不可编辑 | `calculationEditable=false` | 用户无法修改计算结果 |
| LK-010 | 选项联动 | 配置省份-城市联动 | 选择省份后城市选项自动过滤 |
| LK-011 | 事件脚本-onChange | 配置 amount 字段 onChange 脚本 | 金额变化时执行自定义逻辑 |
| LK-012 | 事件脚本-onBlur | 配置 phone 字段 onBlur 格式校验脚本 | 失焦时校验手机号格式 |
| LK-013 | 联动循环依赖 | 字段 A 依赖 B，B 又依赖 A | 避免死循环，给出提示或按单向处理 |
| LK-014 | 联动目标字段不存在 | 规则引用已删除字段 | 表达式评估失败，字段默认显示 |
| LK-015 | 非法表达式 | 条件含 SQL 注入或危险函数 | 安全评估拒绝，条件不满足 |

### 5.16.14 自定义组件注册与运行时

#### 注册机制

自定义组件通过 `customComponentRegistry.js` 注册：

```javascript
import { registerCustomFormComponent } from '@/utils/customComponentRegistry'
import MyCustomForm from './MyCustomForm.vue'

registerCustomFormComponent('MyCustomForm', MyCustomForm)
```

#### 自定义表单组件接收的 Props

| Prop | 类型 | 说明 |
|---|---|---|
| `form` | Object | 表单配置对象 |
| `modelValue` | Object | 表单数据对象 |
| `readonly` | Boolean | 是否只读 |
| `fields` | Array | 字段数组 |
| `linkageState` | Object | 联动状态 `{ visibility, disabled, required, options, values }` |
| `entityCode` | String | 实体编码（数据录入场景） |
| `entityDefinition` | Object | 实体定义对象 |
| `entityFields` | Array | 实体字段数组 |
| `mode` | String | `create` / `edit` |

#### 自定义列表组件接收的 Props

| Prop | 类型 | 说明 |
|---|---|---|
| `entityCode` / `entityDefinition` / `entityName` | - | 实体信息 |
| `listConfig` / `listConfigFields` | - | 列表配置 |
| `dataList` / `loading` / `total` / `pageNum` / `pageSize` | - | 数据与分页 |
| `onSearch` / `onReset` / `onPageChange` / `onSizeChange` | Function | 查询与分页回调 |
| `onCreate` / `onView` / `onEdit` / `onDelete` / `onApprove` | Function | 操作回调 |
| `canApprove` / `getStatusType` / `getStatusText` / `formatDate` | Function | 辅助函数 |

#### 自定义组件测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| CC-001 | 注册自定义表单组件 | 在应用入口注册 `CustomLeaveForm` | 表单配置 `customComponent=CustomLeaveForm` 时生效 |
| CC-002 | 自定义组件接收 props | 组件内打印 props | 接收到 form、modelValue、readonly、fields、linkageState 等 |
| CC-003 | 自定义组件只读模式 | `readonly=true` | 组件展示只读态，不提交编辑 |
| CC-004 | 未注册组件 fallback | 配置不存在的组件名 | 前端报错或回退到默认表单渲染 |
| CC-005 | 注册自定义列表组件 | 注册 `CustomLeaveList` | 列表配置 `customComponent=CustomLeaveList` 时生效 |
| CC-006 | 自定义列表接收回调 | 组件内调用 onSearch/onCreate | 父组件响应查询和新增操作 |
| CC-007 | 扩展字段组件 | 调用 `registerFormFieldComponent('myType', MyComp)` | 字段类型 myType 使用 MyComp 渲染 |

### 5.16.15 字段类型详细展示与校验

#### 字段组件映射

| 字段类型/组件类型 | 前端组件 | 数据类型 | 展示说明 |
|---|---|---|---|
| `input` / `string` / `text` / `textarea` | `TextField` | String | 单行/多行文本输入 |
| `number` / `integer` / `long` / `decimal` / `double` | `NumberField` | Number | 数字输入框，可配置精度 |
| `date` / `datetime` | `DateField` | Date/String | 日期/日期时间选择器 |
| `select` / `multi_select` / `select_multiple` | `SelectField` | String/Array | 下拉单选/多选 |
| `radio` | `RadioField` | String | 单选按钮组 |
| `checkbox` | `CheckboxField` | Array | 多选框组 |
| `switch` / `boolean` | `SwitchField` | Boolean | 开关 |
| `file` / `image` | `FileField` | String/Array/Object | 文件/图片上传 |
| `user` / `dept` / `reference` / `multi_reference` | `EntityField` | String/Array | 用户/部门/实体引用选择器 |
| `sub_form` / `sub_form_list` | `SubFormField` | Object/Array | 子表单 |
| `cascader` | `CascaderField` | Array/String | 级联选择 |
| `rich_text` | `RichTextField` | String | 富文本编辑器 |
| `section` | `SectionField` | - | 分组标题，不占数据 |

#### 各字段类型测试要点

**文本类（TextField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `placeholder` | 占位提示 | 为空时默认显示“请输入xxx” |
| `minLength` / `maxLength` | 长度限制 | 超长/过短拒绝 |
| `pattern` | 正则校验 | 手机号、邮箱等格式校验 |
| `showWordLimit` | 字数统计 | 显示已输入/最大字数 |

**数字类（NumberField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `precision` | 小数精度 | 输入时自动格式化 |
| `min` / `max` | 数值范围 | 超出范围拒绝 |
| `step` | 步长 | 加减按钮步进 |
| 空字符串 | 数字空值 | 组件返回 null 而非 '' |

**日期类（DateField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `format` | 显示格式 | `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss` |
| `valueFormat` | 值格式 | 提交到后端的格式 |
| `defaultValue` | 默认值 | 新建时自动填充当前日期 |

**选择类（SelectField / RadioField / CheckboxField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `options` | 静态选项 | 选项 label/value 正确回显 |
| `optionsJson` | 选项 JSON | 从 entity_field 或 componentProps 解析 |
| `multiple` | 是否多选 | 多选时值为数组 |
| `linkageState.options` | 动态选项 | 联动过滤后选项更新 |

**文件类（FileField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `fileType` | 允许文件类型 | 非法类型拒绝上传 |
| `fileSize` | 单文件大小限制 | 超大文件拒绝 |
| `limit` | 最大文件数 | 超出数量限制拒绝 |
| `fileItems` | 附件分组配置 | 多分组文件展示 |
| 只读展示 | 文件下载链接 | 只读时显示文件名和下载链接 |

**引用类（EntityField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `refEntityType` | USER/DEPT/ROLE/GROUP/CUSTOM | 加载对应数据源 |
| `refEntityId` | 自定义实体 ID | 加载自定义实体数据 |
| `multiple` | 是否多选 | 多选时返回数组 |
| `isReadonly=1` | 只读 | 仍可打开选择器查看，但不可变更 |

**子表单类（SubFormField）**

| 配置项 | 含义 | 测试点 |
|---|---|---|
| `relation.type` | ONE_TO_ONE / ONE_TO_MANY | 控制可新增行数 |
| `displayMode` | embedded / tab | 嵌入或 Tab 展示 |
| `cascadeDelete` | 级联删除 | 删除父记录时子记录行为 |
| `required` | 子表必填 | 空表时拒绝提交 |

#### 字段类型测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| FT-001 | 文本长度校验 | maxLength=10，输入 11 个字符 | 前端/后端拒绝 |
| FT-002 | 文本正则校验 | pattern=手机号正则 | 非法手机号拒绝 |
| FT-003 | 数字精度 | precision=2，输入 3.1415 | 显示 3.14 |
| FT-004 | 数字范围 | min=0, max=100，输入 150 | 拒绝 |
| FT-005 | 日期格式 | format=yyyy-MM-dd | 按格式显示和提交 |
| FT-006 | 下拉选项回显 | 存储 value=1，options 有 label | 显示对应 label |
| FT-007 | 多选字段 | checkbox/select_multiple | 值为数组，回显正确 |
| FT-008 | 文件上传限制 | fileSize=1MB，上传 5MB | 拒绝上传 |
| FT-009 | 文件只读展示 | 只读模式 | 显示下载链接 |
| FT-010 | 用户选择 | refEntityType=USER | 加载系统用户列表 |
| FT-011 | 部门选择 | refEntityType=DEPT | 加载组织架构 |
| FT-012 | 自定义实体引用 | refEntityType=CUSTOM | 加载自定义实体数据 |
| FT-013 | 子表单一对一 | relation.type=ONE_TO_ONE | 只能新增一行 |
| FT-014 | 子表单一对多 | relation.type=ONE_TO_MANY | 可新增多行 |
| FT-015 | 富文本 | rich_text | 正常编辑和展示 HTML |
| FT-016 | 级联选择 | cascader | 按层级选择，值正确 |

### 5.16.16 表单布局（layoutType）

#### 布局类型说明

| 布局类型 | 标签位置 | 标签宽度 | 字段排列 | 适用场景 |
|---|---|---|---|---|
| `vertical` | `top` | `auto` | 每个字段独占一行 | 字段少、移动端友好 |
| `horizontal` | `right` | `120px` | 每行两个字段（width: calc(50% - 10px)） | 字段多、空间紧凑 |
| `grid` | `right` | `120px` | 按 `gridSpan` 分配列宽 | 灵活布局 |

#### grid 布局

- 总宽度为 24 栅格
- 字段宽度 = `(gridSpan / 24) × 100%`
- `gridSpan=24` 表示整行，`gridSpan=12` 表示半行
- 字段按 flex 流式排列

#### 布局测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| LY-001 | 垂直布局 | layoutType=vertical | 每个字段独占一行，标签在上方 |
| LY-002 | 水平布局 | layoutType=horizontal | 每行两个字段，标签在右侧 |
| LY-003 | 网格布局 | layoutType=grid，字段 gridSpan=12/8/6 | 按栅格宽度排列 |
| LY-004 | 网格布局整行 | gridSpan=24 | 字段占满整行 |
| LY-005 | 子表单嵌入布局 | displayMode=embedded | 子表字段按主表布局排列 |
| LY-006 | 子表单 Tab 布局 | displayMode=tab | 子表作为独立 Tab |

### 5.16.17 不同页面的表单展示差异

#### 数据新增/编辑页

- 来源：实体默认表单（`EntityDataFormFields`）
- 模式：`create` / `edit`
- 可编辑性：按表单字段 `isReadonly` 和联动状态
- 提交：保存实体数据，可选同时发起流程
- 校验：前端 `el-form` 规则 + 后端 `validatePublishedRequiredFields` / `validatePublishedUniqueFields`

#### 流程发起页

- 来源：开始事件绑定的实体表单
- 模式：`create`
- 可编辑性：开始节点通常全部可编辑
- 提交：保存实体数据 + 启动流程实例
- 特殊：可附带 `processVariables`

#### 待办审批弹窗

- 来源：当前节点绑定的表单（`EntityApprovalDialog`）
- 展示组件：`EntityApprovalBasicInfo` + `FormPreviewLinkage`
- 可编辑性：默认只读（`readonly=true`），除非节点 `isReadonly=false`
- 子表单处理：Tab 子表单由 `EntityApprovalDialog` 外部接管，生成独立 Tab
- 底部：审批意见区（操作按钮 + 备注）
- 提交：只提交审批操作，不修改实体数据（除非节点表单允许编辑）

#### 流程详情/查看页

- 来源：当前节点或最近完成节点绑定的表单
- 模式：`view`
- 可编辑性：全部只读
- 附加 Tab：流程图、审批历史

#### 审批历史

- 不展示完整表单，只展示节点处理记录
- 包含：节点名、执行人、actionLabel、comment、时间

#### 只读模式下的特殊展示

- 文件字段：显示文件名和下载链接
- 用户/部门字段：显示名称（可能带 username）
- 子表单：以表格或只读列表展示
- 图片：缩略图预览

### 5.16.18 默认值、数据回填与选项动态加载

#### 默认值规则

| 来源 | 优先级 | 说明 |
|---|---|---|
| 已有数据 | 最高 | 编辑/查看时回填已保存值 |
| 联动值 | 高 | `linkageState.values` 自动填充 |
| `defaultValue` | 中 | 新建时自动填充 |
| 空值 | 低 | 显示占位提示 |

**默认值注入时机**：字段组件 mounted 时，如果 `modelValue` 为空且 `defaultValue` 不为空，自动 emit 默认值。

#### 数据回填流程

1. 打开编辑/审批弹窗
2. 调用 `loadProcessDetail` 或 `entityDataApi.findById`
3. 返回 `entityData` 包含系统字段 + `data.{fieldCode}`
4. `FormPreviewLinkage` 绑定到 `formData`
5. 各字段组件按 `fieldCode` 读取对应值
6. 子表单字段按 `relation.childRefFieldCode` 查询子表数据并回填

#### 选项加载优先级

1. `props.options`（联动引擎动态选项）
2. `componentProps.options`
3. `field.optionsJson`
4. `field.options`
5. 引用实体动态加载（用户/部门/自定义实体）

### 5.16.19 表单校验时机与规则

#### 校验层级

| 层级 | 触发时机 | 校验内容 | 说明 |
|---|---|---|---|
| 前端实时校验 | 输入/失焦 | `el-form` rules（必填、长度、正则） | 即时反馈 |
| 前端提交校验 | 点击提交 | 整体验证 + 联动必填 | 阻止非法提交 |
| 后端必填校验 | 保存接口 | `EntityField.isRequired=true` 的字段 | 基于发布快照 |
| 后端唯一校验 | 保存接口 | `EntityField.isUnique=true` 的字段 | 基于发布快照 |
| 数据库约束 | 持久化 | 长度、类型、唯一索引 | 最后兜底 |

#### 校验规则配置

| 规则 | 配置位置 | 示例 |
|---|---|---|
| 必填 | `EntityField.isRequired` / `EntityFormField.isRequired` / 联动 `requiredRule` | - |
| 唯一 | `EntityField.isUnique` | - |
| 长度 | `validationRules.minLength` / `maxLength` | `{"minLength":5,"maxLength":100}` |
| 正则 | `validationRules.pattern` | `{"pattern":"^1[3-9]\\d{9}$"}` |
| 数值范围 | `componentProps.min` / `max` | `{"min":0,"max":100}` |
| 日期范围 | `componentProps` | 日期选择器限制 |

#### 校验测试用例

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| VD-001 | 前端必填校验 | 必填字段留空，失焦 | 显示红色提示 |
| VD-002 | 前端长度校验 | 文本超 maxLength | 输入受限或失焦报错 |
| VD-003 | 前端正则校验 | 手机号格式错误 | 失焦报错 |
| VD-004 | 提交时整体验证 | 多个字段非法，点击提交 | 滚动到第一个错误字段 |
| VD-005 | 后端必填校验 | 绕过前端提交空必填字段 | 后端返回“字段必填” |
| VD-006 | 后端唯一校验 | 提交重复唯一字段 | 后端返回“字段值已存在” |
| VD-007 | 联动必填校验 | 条件满足时字段必填 | 未填时前后端均拒绝 |
| VD-008 | 只读字段不校验 | 隐藏/只读字段无值 | 不影响提交 |
| VD-009 | 子表字段校验 | 子表内字段必填 | 子表行内字段校验生效 |
| VD-010 | 日期范围校验 | 配置起止日期限制 | 选择范围外日期拒绝 |

### 5.16.20 节点-实体-表单关系补充测试用例汇总

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| NEF-017 | 表单布局 vertical | layoutType=vertical | 字段垂直排列 |
| NEF-018 | 表单布局 horizontal | layoutType=horizontal | 每行两个字段 |
| NEF-019 | 表单布局 grid | gridSpan=8/12/24 | 按栅格排列 |
| NEF-020 | 字段默认值 | 配置 defaultValue | 新建时自动填充 |
| NEF-021 | 数据回填 | 编辑已有记录 | 表单显示已保存数据 |
| NEF-022 | 选项联动过滤 | 省份-城市联动 | 城市选项随省份变化 |
| NEF-023 | 计算字段联动 | 数量×单价=总价 | 修改数量总价自动更新 |
| NEF-024 | 自定义事件脚本 | 字段 onBlur 脚本 | 失焦时执行脚本 |
| NEF-025 | 文件字段只读展示 | 只读模式 | 显示下载链接 |
| NEF-026 | 用户字段选择 | refEntityType=USER | 加载用户并回填 |
| NEF-027 | 前端校验阻止提交 | 必填字段为空 | 点击提交不调用接口 |
| NEF-028 | 后端必填兜底 | 绕过前端 | 接口返回错误 |
| NEF-029 | 子表行内校验 | 子表字段必填 | 空行提交失败 |
| NEF-030 | 审批弹窗只读 | ProcessNodeForm.isReadonly=1 | 表单只读，仅审批操作可提交 |
| NEF-031 | 审批弹窗可编辑 | ProcessNodeForm.isReadonly=0 | 可修改字段并随审批提交 |
| NEF-032 | Tab 子表单在审批弹窗 | displayMode=tab | 子表单作为独立 Tab，基本信息 separate |
| NEF-033 | 多表单审批弹窗 | 节点绑定两个表单 | 合并后展示，子表单正确分离 |
| NEF-034 | 自定义组件审批弹窗 | customComponent | 自定义组件接收 readonly=true |
| NEF-035 | 表单版本隔离 | 发布后修改实体表单 | 旧实例仍用旧字段定义 |

---

## 6. 顺序流配置与流程动作

### 6.1 顺序流条件配置

见 5.11 排他网关。

### 6.2 实体状态映射

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `entityStatusCode` | 实体状态编码 | 从 `entity_status` 表中选择 | 流程经过该连线时，实体数据状态变更为此值 | 状态存在性、分类匹配 |
| `statusCategory` | 状态分类 | `NEW`/`PROCESSING`/`COMPLETED`/`TERMINATED` | 用于状态分组和统计 | 分类一致性 |
| `description` | 状态变更说明 | 字符串 | 变更记录 | 历史展示 |

**验证步骤**：

1. 连线上配置 `entityStatusCode=DEPT_APPROVING`，审批通过后查看 `entity_data.status` 变为该值。
2. 结束连线上配置 `entityStatusCode=APPROVED`，流程结束后实体状态为已完成。
3. 驳回连线上配置 `entityStatusCode=REJECTED`，驳回后实体状态为终止。

### 6.3 流程动作（FlowAction）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `actionName` | 动作名称 | 如“发送通知” | 动作列表展示 | 唯一标识 |
| `interfaceName` | 接口名称 | Spring Bean 名或类全名 | 执行目标 | Bean/类存在性 |
| `methodName` | 方法名 | 默认 `execute` | 反射调用 | 方法签名匹配 |
| `paramsJson` | 参数 JSON | 对象 | 方法参数 | 类型匹配 |
| `enabled` | 是否启用 | `true` | `false` 时不执行 | 禁用后跳过 |
| `sortOrder` | 执行顺序 | 数字 | 多个动作按顺序执行 | 排序正确 |

**验证步骤**：

1. 在“通过”连线上添加动作，配置 `notificationService.sendApproveNotice`，审批通过后调用该服务。
2. 设置 `enabled=false`，确认动作不执行。
3. 配置多个动作，按 `sortOrder` 顺序执行。
4. 动作抛异常，确认审批事务回滚（如果设计为同事务）。

---

## 7. 实体与表单配置细化

> 本节聚焦实体、表单、字段各自的静态配置项含义。它们之间的运行时关系、节点绑定、合并展示、权限叠加等细节请参见 **5.16 节点、实体、表单关系与展示方式**。

### 7.1 实体定义（entity_definition）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `entity_code` | 实体编码，对应动态表名 | 英文+下划线，如 `demo_expense` | 动态建表、API 路径 | 唯一、特殊字符拦截 |
| `entity_name` | 实体中文名称 | 如 `费用报销` | 菜单、列表标题 | 修改后同步 |
| `enable_process` | 是否启用流程 | `false`（默认） | `true` 时可绑定流程并发起 | false 时仅作为数据表 |
| `process_definition_id` | 绑定的流程定义配置 ID | 可选 | 实体数据提交后启动该流程 | 流程存在且已发布 |
| `status` | 实体定义状态 | `DRAFT`/`PUBLISHED`/`DISABLED` | 控制实体是否可用 | 发布后才能创建数据 |

### 7.2 实体字段（entity_field）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `field_code` | 字段编码，对应数据库列名 | 英文+下划线 | 数据库存储、API 字段名 | 唯一、特殊字符 |
| `field_name` | 字段中文名称 | 如 `报销金额` | 表单标签、列表列头 | 修改后同步 |
| `field_type` | 字段类型 | `TEXT`/`TEXTAREA`/`NUMBER`/`DATE`/`DATETIME`/`SELECT`/`RADIO`/`CHECKBOX`/`FILE`/`USER` | 表单组件、校验规则、数据库类型 | 各类型渲染正确 |
| `db_type` | 数据库字段类型 | 根据 field_type 推导 | 动态建表列类型 | 发布后与数据库一致 |
| `field_length` | 字段长度 | 如 255 | 字符串类型长度限制 | 超长拒绝 |
| `field_precision` | 小数精度 | 如 2 | DECIMAL 类型精度 | 金额类字段 |
| `is_required` | 是否必填 | `false` | 提交时校验 | 空值拒绝 |
| `is_unique` | 是否唯一 | `false` | 数据库唯一约束 | 重复拒绝 |
| `default_value` | 默认值 | 字符串 | 新建时自动填充 | 各类型默认值解析 |
| `options_json` | 选项配置 | `[{"label":"","value":""}]` | 下拉/单选/多选选项 | 选项回显、存储值 |
| `validate_rules` | 校验规则 JSON | 如 `{"minLength":5}` | 自定义校验 | 正则、长度、范围 |
| `ref_entity_id` / `ref_entity_type` | 关联实体 | `CUSTOM`/`USER`/`DEPT`/`ROLE`/`GROUP` | 子表单、用户选择、部门选择 | 引用数据加载 |
| `display_type` | 子表单显示方式 | `embedded`/`tab` | 子表单嵌入或 Tab 页展示 | 前端渲染 |

### 7.3 表单字段配置（form_field_config）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `field_key` | 字段标识 | 与 entity_field.field_code 对应 | 数据绑定 | 一致性 |
| `is_required` | 节点级必填 | `false` | 与实体字段必填叠加 | 任一未满足均拒绝 |
| `sort_order` | 字段排序 | 0 | 表单字段顺序 | 按数字升序 |

### 7.4 编码规则（entity_code_rule）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `prefix` | 编码前缀 | 如 `CG` | 生成编码前缀 | 组合到最终编码 |
| `date_format` | 日期格式 | `yyyyMMdd` | 中间日期部分 | 不同格式 |
| `seq_length` | 序列号位数 | 6 | 补零位数 | 000001 |
| `seq_type` | 序列重置周期 | `DAY`/`MONTH`/`YEAR`/`NEVER` | 序列号何时重置 | 跨天/跨月/跨年 |
| `current_seq` | 当前序列号 | 0 | 当前已用序号 | 递增不跳号 |

---

## 8. 列表、权限与按钮配置细化

### 8.1 实体列表配置（entity_list_config）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `list_key` | 列表标识 | 如 `default`、`myList` | 同一实体多个列表 | 唯一性 |
| `list_name` | 列表名称 | 如 `默认列表` | 前端切换展示 | 修改同步 |
| `is_default` | 是否默认列表 | `false` | 未指定时展示该列表 | 只有一个默认 |
| `custom_component` | 自定义列表组件 | 组件注册名 | 使用自定义渲染 | 组件存在性 |
| `toolbar_config` | 工具栏按钮配置 JSON | 对象 | 顶部按钮（新增、导入、导出等） | 按钮显隐、权限 |
| `row_action_config` | 操作列按钮配置 JSON | 对象 | 行内按钮（编辑、删除、查看等） | 按钮显隐、权限 |

### 8.2 列表字段配置（entity_list_field）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `field_code` | 字段编码 | 实体字段编码 | 列数据来源 | 一致性 |
| `show_in_list` | 是否显示 | `true` | 隐藏字段不展示 | 切换后列表变化 |
| `is_query` | 是否查询条件 | `true` | 查询表单展示 | 切换后查询区变化 |
| `query_type` | 查询方式 | `LIKE`/`EQ`/`NE`/`GT`/`LT`/`BETWEEN`/`IN` | 后端查询逻辑 | 各类型结果正确 |
| `width` | 列宽 | 0（自适应） | 列宽度 | 固定宽度生效 |
| `align` | 对齐方式 | `left`/`center`/`right` | 文本对齐 | 视觉正确 |
| `formatter` | 简单格式化 | 如 `yyyy-MM-dd`、`#0.00` | 数据显示格式 | 日期、金额格式化 |
| `data_source_type` | 数据源类型 | `ENTITY_FIELD`/`REFERENCE`/`AGGREGATE`/`CUSTOM_PROVIDER` | 列数据来源 | 自定义处理器 |
| `render_component` | 渲染组件 | 组件名 | 自定义列渲染 | 组件存在性 |

### 8.3 数据权限规则（entity_list_permission）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `rule_name` | 规则名称 | 字符串 | 规则展示 | 唯一性 |
| `priority` | 优先级 | 数字，越大越优先 | 规则执行顺序 | 高优先级覆盖低优先级 |
| `enabled` | 是否启用 | `true` | `false` 时不生效 | 禁用后规则不生效 |
| `match_config` | 匹配条件 JSON | 如 `{"role":"manager"}` | 哪些用户命中该规则 | 条件解析 |
| `filter_config` | 过滤规则 JSON | 如 `{"create_by":"${currentUser}"}` | 命中用户可见的数据范围 | 数据过滤 |
| `combine_mode` | 规则叠加方式 | `UNION`（并集）/`INTERSECT`（交集） | 多条规则组合方式 | 并集/交集结果 |

### 8.4 委托配置（entity_list_permission_delegate）

| 配置项 | 具体含义 | 取值/默认值 | 业务影响 | 测试验证点 |
|---|---|---|---|---|
| `from_user_id` | 委托方 | 用户 ID | 谁的数据被委托 | 只能委托自己的权限 |
| `to_user_id` | 受托方 | 用户 ID | 谁能查看委托数据 | 登录后可见 |
| `delegate_scope` | 委托范围 | `ALL`/`PERSONAL`/`CONDITION` | 全部/仅本人/按条件 | 范围控制 |
| `delegate_config` | 委托范围配置 JSON | 条件对象 | `CONDITION` 时使用 | 条件解析 |
| `start_time` / `end_time` | 委托有效期 | 日期时间 | 仅在有效期内生效 | 过期后不可见 |

---

## 9. 核心业务流程集成测试用例

### 9.1 流程生命周期

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| LC-001 | 新建并保存草稿 | 创建流程，绘制开始→用户任务→结束，保存 | `status=DRAFT`，`version=1` |
| LC-002 | 发布流程 | 点击发布 | `status=PUBLISHED`，Flowable 部署成功 |
| LC-003 | 发布新版本 | 修改 BPMN 后再次发布 | `version=2`，旧实例仍用 version=1 |
| LC-004 | 禁用流程 | 设置 `status=DISABLED` | 发起流程提示“流程已禁用” |
| LC-005 | 删除草稿 | 删除 DRAFT 流程 | 逻辑删除，数据库 `deleted=1` |

### 9.2 流程启动与实体绑定

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| PS-001 | 实体启用流程并发起 | `enable_process=true`，绑定已发布流程，提交数据 | `entity_data.status=PROCESSING`，`process_instance_id` 写入 |
| PS-002 | 未启用流程的实体发起 | `enable_process=false` 时提交数据 | 仅保存实体数据，不启动流程 |
| PS-003 | 流程禁用后发起 | 绑定流程被禁用 | 提示流程已禁用，数据不进入审批 |
| PS-004 | 流程变量注入 | 提交时附带 `processVariables` | Flowable 流程实例变量包含注入值 |
| PS-005 | 跳过第一个节点发起 | 第一个用户任务 `skipNode=true` | 流程发起后自动完成第一个节点 |

### 9.3 审批操作

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| AP-001 | 通过 | 审批人点击通过 | `process_task` 状态 done，action=approve，进入下一节点 |
| AP-002 | 自定义动作通过 | 选择“同意，需要会签” | actionLabel 持久化，流程按该动作分支走，历史显示自定义文本 |
| AP-003 | 驳回 | 审批人点击驳回 | action=reject，进入驳回分支或返回上一节点 |
| AP-004 | 转办 | 审批人转给其他人 | 原任务状态 transfer，新任务生成，操作日志记录 |
| AP-005 | 撤回 | 发起人撤回 | 流程实例终止，实体状态回退 |
| AP-006 | 重新提交 | 被驳回后修改数据重新提交 | 流程回到被驳回节点，实体数据更新 |
| AP-007 | 评论必填 | 节点配置评论必填 | 未填评论拒绝提交 |
| AP-008 | 并发审批冲突 | 同一任务两人同时提交 | 仅一人成功，另一人提示任务已处理 |

### 9.4 超时处理

| 用例编号 | 场景 | 操作步骤 | 预期结果 |
|---|---|---|---|
| TO-001 | 超时提醒 | 配置 1 小时超时 + REMIND | 超时后发送提醒通知 |
| TO-002 | 超时自动转办 | 配置超时 + TRANSFER + 目标人 | 超时后目标人生成待办 |
| TO-003 | 超时自动通过 | 配置超时 + AUTO_APPROVE | 超时后流程自动进入下一节点 |
| TO-004 | 超时自动驳回 | 配置超时 + AUTO_REJECT | 超时后流程进入驳回分支 |
| TO-005 | 超时前人工处理 | 在超时前审批通过 | 不触发超时策略 |

---

## 10. 配置组合测试矩阵

以下组合必须重点验证：

| 组合场景 | 涉及配置 | 验证要点 |
|---|---|---|
| 跳过节点 + 多实例 | `skipNode=true` + `isMultiInstance=true` | 跳过节点后正确生成会签任务 |
| 多实例 + 自定义 actionLabel | `isMultiInstance=true` + `approvalConfig.options` | 各会签人历史显示各自动作，变量不覆盖 |
| 超时 + 转办 | `timeoutAction=TRANSFER` + 转办操作 | 转办后重置超时计时，不重复触发 |
| 超时 + 自动通过 + 网关 | `timeoutAction=AUTO_APPROVE` + 排他网关 | 自动通过后按 `approved=approve` 走分支 |
| 数据权限 + 委托 | `entity_list_permission` + `entity_list_permission_delegate` | 受托人可见委托人规则允许的数据 |
| 表单只读 + 字段必填 | `isReadonly=true` + `is_required=true` | 只读时跳过节点级校验，可正常提交 |
| 流程动作 + 事务异常 | `flow_action` + 服务任务异常 | 动作失败时审批回滚 |
| 分支条件 + 自定义动作 | `conditionExpression` + `options.value` | `approved` 取自定义动作原始值 |
| 会签 + 完成条件 + 驳回 | `multiInstanceType=parallel` + `completionCondition` + 驳回 | 驳回时立即终止多实例 |
| 服务任务 + REST + 超时 + 重试 | `implementationType=rest` + `timeout` + `retryCount` | 超时后重试指定次数 |
| 脚本任务 + 结果变量 | `script` + `resultVariable` | 脚本结果正确写入变量 |
| 调用活动 + 参数映射 | `inputParameters` + `outputParameters` | 父子流程变量正确传递 |
| 接收任务 + 超时 | `hasTimeout=true` + `timeoutAction` | 超时后按配置处理 |
| 排他网关 + 默认流 + 实体状态 | `default` + `entityStatusCode` | 默认流触发时实体状态变更 |
| 异步执行 + 跳过表达式 | `async=true` + `skipExpression` | 异步作业正确判断跳过条件 |

---

## 11. 异常与边界测试

| 用例编号 | 场景 | 操作 | 预期 |
|---|---|---|---|
| EX-001 | 审批人不存在 | `assignee_value` 指向已删除用户 | 流程报错，提示审批人不存在 |
| EX-002 | 角色下无用户 | `ROLE` 类型对应角色无成员 | 流程报错或任务无处理人 |
| EX-003 | 必填字段为空 | 提交时必填字段未填 | 前端/后端均拒绝 |
| EX-004 | 唯一字段重复 | 重复提交相同唯一字段值 | 数据库报错，业务层提示 |
| EX-005 | 非法 BPMN | 发布含非法 BPMN 的流程 | 发布失败，给出错误信息 |
| EX-006 | 非法脚本 | 脚本任务语法错误 | 发布或执行时报错 |
| EX-007 | 服务任务类不存在 | `implementationType=class`，类不存在 | 发布或执行时报错 |
| EX-008 | REST 接口不可达 | `implementationType=rest`，URL 404 | 按 `errorHandling` 处理 |
| EX-009 | 决策表不存在 | `decisionRef` 指向不存在的 DMN | 流程报错 |
| EX-010 | 子流程未发布 | `calledElement` 指向草稿 | 父流程报错 |
| EX-011 | 并发审批 | 同一任务两人同时提交 | 仅一人成功，另一人提示任务已处理 |
| EX-012 | 重复发起 | 同一实体数据重复点击提交 | 幂等处理，不重复启动流程 |
| EX-013 | 数据库断连 | 审批过程中数据库断开 | 事务回滚，无脏数据 |
| EX-014 | 超大附件 | 上传超过 50MB 文件 | 拒绝上传，提示大小限制 |
| EX-015 | XSS/注入 | 表单输入脚本标签 | 后端转义或前端安全渲染 |

---

## 12. 数据一致性验证

每次核心操作后，检查以下数据：

| 操作 | 检查点 | SQL/方法 |
|---|---|---|
| 发起流程 | entity_data 写入 process_instance_id、current_task、status | `SELECT * FROM entity_data WHERE id=?` |
| 发起流程 | Flowable 流程实例存在 | `runtimeService.createProcessInstanceQuery()` |
| 发起流程 | process_task 生成待办 | `SELECT * FROM process_task WHERE process_instance_id=?` |
| 审批通过 | process_task 状态 done，action/actionLabel 正确 | `SELECT * FROM process_task WHERE task_id=?` |
| 审批通过 | Flowable 任务完成，历史变量存在 | `historyService.createHistoricVariableInstanceQuery()` |
| 审批通过 | entity_data current_task 更新 | 查看动态表 current_task 字段 |
| 驳回 | process_task 状态 done，action=reject | `SELECT * FROM process_task WHERE task_id=?` |
| 会签 | 多实例任务全部生成 | `SELECT COUNT(*) FROM process_task WHERE node_id=?` |
| 转办 | 原任务 transfer，新任务 todo | `SELECT * FROM process_task WHERE process_instance_id=? ORDER BY id` |
| 超时 | 超时任务被自动处理 | 检查任务状态与操作时间 |
| 脚本/服务任务 | 流程变量被修改 | `ACT_HI_VARINST` 或运行时变量查询 |
| 调用活动 | 子流程实例生成 | `ACT_HI_PROCINST` 中查询 SUPER_PROCESS_INSTANCE_ID_ |

---

## 13. BPMN 发布归一化转换验证

发布流程时，`ProcessBpmnPublishSanitizer` 会执行以下转换，需逐一验证：

| 转换规则 | 输入示例 | 输出示例 | 测试验证 |
|---|---|---|---|
| Camunda 属性转 Flowable | `camunda:assignee="user1"` | `flowable:assignee="user1"` | 含 Camunda 属性的 BPMN 可正常部署 |
| Camunda Properties 转 Flowable | `<camunda:Properties>` | `<flowable:Properties>` | 扩展属性保留 |
| 裸属性加前缀 | `assignee="user1"` | `flowable:assignee="user1"` | 无前缀属性不丢失 |
| 多实例属性加前缀 | `collection="users"` | `flowable:collection="users"` | 多实例生效 |
| 跳过节点加表达式 | `<flowable:property name="skipNode" value="true"/>` | `flowable:skipExpression="${skipNodeEnabled}"` | 跳过生效 |
| 脚本配置提取 | `<flowable:property name="scriptConfig" value="..."/>` | `<script>xxx</script>` + `scriptFormat` | 脚本任务执行 |
| approved 布尔迁移 | `${approved == true}` | `${approved == 'approve'}` | 旧条件仍生效 |
| ID 冲突解决 | 元素 ID 与 processKey 冲突 | 重命名冲突元素 | 发布成功 |
| 使用 processKey | `<bpmn:process id="old">` | `<bpmn:process id="processKey">` | 启动键正确 |
| 无效多实例清理 | 空 `<multiInstanceLoopCharacteristics/>` | 移除该标签 | 不导致部署失败 |
| 多实例执行人修复 | 无 `flowable:assignee` | 添加 `flowable:assignee="${assignee}"` | 会签任务有执行人 |

---

## 14. 回归测试清单

每次发布前必须执行：

- [ ] 单审批人流程完整跑通（发起→通过→结束）。
- [ ] 排他网关分支流程跑通（通过/驳回两条分支）。
- [ ] 会签流程 3 人全部通过。
- [ ] 或签流程 1 人通过，其他任务自动完成。
- [ ] 第一个节点自动跳过。
- [ ] 转办后审批。
- [ ] 实体数据子表单增删改。
- [ ] 列表数据权限（本人数据）。
- [ ] 审批历史显示自定义 actionLabel。
- [ ] 流程图 tooltip 显示正确。
- [ ] 发布新版本不影响旧流程实例。
- [ ] 脚本任务计算结果正确。
- [ ] REST 服务任务调用成功。
- [ ] 接收任务消息触发后继续。
- [ ] 调用活动调用子流程成功。
- [ ] 节点表单绑定正确加载（绑定表单 > 默认表单 > 第一个可用表单）。
- [ ] 同一节点绑定多个表单时字段去重、排序、名称连接正确。
- [ ] 节点级只读覆盖表单级只读。
- [ ] 子表单 embedded/tab 两种展示方式正常。
- [ ] 子表数据回填、级联删除、必填校验正常。
- [ ] 引用字段（用户/部门/角色/组）只读时仍可查看选择器但不可变更。
- [ ] 表单字段联动（显隐/必填/禁用/选项）正常。
- [ ] 发布后修改表单不影响旧流程实例展示。

---

## 15. 验收标准

- 所有 P0 用例 100% 通过。
- P1 用例通过率不低于 90%。
- 无阻塞性 Bug（导致流程无法继续或数据不一致）。
- 测试报告包含用例编号、执行结果、失败原因、截图/日志。
- 性能：单条流程发起/审批接口响应时间 < 2s（本地环境）。

---

## 16. 测试交付物

1. 测试计划（本文档）
2. 测试用例执行表（Excel/在线文档）
3. 缺陷清单及跟踪记录
4. 数据一致性校验脚本
5. 测试报告与验收签字

---

## 附录 A：常用校验 SQL

```sql
-- 检查流程定义
SELECT id, process_key, process_name, status, version, LENGTH(bpmn_xml) AS xml_len
FROM process_definition_config
WHERE process_key = 'your_process_key';

-- 检查节点配置
SELECT id, node_id, node_name, node_type, skip_node, config_json
FROM node_config
WHERE process_config_id = 'your_process_config_id'
ORDER BY node_id;

-- 检查执行人配置
SELECT ac.*, nc.node_name 
FROM assignee_config ac 
JOIN node_config nc ON ac.node_config_id = nc.id 
WHERE nc.process_config_id = 'your_process_config_id';

-- 检查表单配置
SELECT fc.*, nc.node_name 
FROM form_config fc 
JOIN node_config nc ON fc.node_config_id = nc.id 
WHERE nc.process_config_id = 'your_process_config_id';

-- 检查待办/已办
SELECT task_id, node_name, assignee_id, status, action, action_label, comment, start_time, end_time
FROM process_task
WHERE process_instance_id = 'your_proc_id'
ORDER BY id;

-- 检查 Flowable 历史任务
SELECT ID_, NAME_, ASSIGNEE_, END_TIME_, DURATION_
FROM ACT_HI_TASKINST
WHERE PROC_INST_ID_ = 'your_proc_id';

-- 检查流程变量
SELECT NAME_, TEXT_
FROM ACT_HI_VARINST
WHERE PROC_INST_ID_ = 'your_proc_id';

-- 检查实体数据
SELECT id, status, process_instance_id, current_task_name, current_task_assignee
FROM entity_data
WHERE id = 'your_data_id';
```

## 附录 B：配置项快速对照表

| 节点类型 | 主要配置页签 | 关键配置项 |
|---|---|---|
| 开始事件 | 基本信息、表单 | `node_name`、`formSource`、`entityFormIds` |
| 结束事件 | 基本信息 | `node_name`、`terminate_end_event` |
| 用户任务 | 基本信息、执行人、表单、审批、高级 | `assigneeType`、`assignee`、`isMultiInstance`、`multiInstanceType`、`entityFormIds`、`options`、`skipNode` |
| 服务任务 | 基本信息、服务 | `implementationType`、`implementation`、`restForm.*`、`resultVariable` |
| 脚本任务 | 基本信息、脚本 | `scriptFormat`、`script`、`resultVariable`、`autoStoreVariables` |
| 发送任务 | 基本信息、发送 | `channels`、`to`、`subject`、`content`、`templateKey` |
| 接收任务 | 基本信息、接收 | `messageRef`、`hasTimeout`、`timeout`、`timeoutAction` |
| 手动任务 | 基本信息、手动 | `description`、`responsible`、`estimatedHours` |
| 业务规则任务 | 基本信息、规则 | `decisionRef`、`inputVariables`、`resultVariable` |
| 调用活动 | 基本信息、子流程 | `calledElement`、`inputParameters`、`outputParameters` |
| 排他网关 | 条件、实体状态 | `conditionList`、`type=default/expression` |
| 并行网关 | 无 | BPMN 拓扑 |
| 包容网关 | 条件、实体状态 | `conditionList` |
| 事件网关 | 无 | BPMN 拓扑 + 后续事件 |
| 顺序流 | 条件、实体状态、流程动作 | `conditionList`、`entityStatusCode`、`flow_action` |
| 节点-实体-表单关系 | 实体表单、节点表单绑定、字段权限、子表单 | `EntityForm`、`ProcessNodeForm`、`EntityFormField`、`displayMode`、`isReadonly` | 详见 5.16 节 |
