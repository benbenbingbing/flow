# 流程动作时机扩展

## 自定义处理器

实现 `FlowActionProvider` 并注册为 Spring Bean。已有实现无需修改；可按需覆盖：

- `supportedTriggerTimings()`：限制允许配置的触发时机。
- `supportedExecutionModes()`：限制事务内或提交后执行。
- `recommendedExecutionMode()`：设计器选择处理器时自动带出的推荐方式。

提交后处理器应使用 `FlowActionContext#getIdempotencyKey()` 调用外部系统。

## 自定义时机

实现 `FlowActionTriggerProvider`，返回自定义 `FlowActionTimingOption`。时机编码需要全局唯一，并声明：

- 适用作用域 `PROCESS / NODE / SEQUENCE_FLOW`
- 是否仅允许用户任务
- 默认执行方式和失败策略
- 配置人员可以使用的上下文说明

业务代码通过注入 `FlowActionDispatcher` 调用 `dispatchCustom(...)`。自定义时机仍会经过发布版本解析、处理器校验、执行排序、事务策略、Outbox、重试和执行日志。

## 执行语义

- `IN_TRANSACTION + ROLLBACK`：处理器异常回滚当前流程操作。
- `IN_TRANSACTION + CONTINUE`：记录失败后继续流程。
- `AFTER_COMMIT + RETRY`：主事务提交后执行，失败按指数退避重试。
- `AFTER_COMMIT + IGNORE`：主事务提交后执行，失败记录为终态。

流程结束类动作不能假定运行时流程或当前任务仍存在，应使用上下文变量快照、历史流程数据和实体数据。

## 标准业务操作阶段

设计器的“全局流程动作”只作用于当前流程定义及其发布版本，对应 `PROCESS`；目录中的 `GLOBAL` 仅表示任意实体的流程可选用该处理器。任务操作按源用户任务节点绑定，不隐式继承流程级规则，也不自动套用到本地加签子任务。

每个实际业务边界只配置一次时机，处理器自行决定校验或处理；同一时机的事务内动作按配置顺序执行，任一异常阻断操作。目录共 27 个时机，其中业务操作时机 17 个、引擎生命周期时机 10 个。

| 配置位置 | 操作 | 执行前 | 执行后 |
| --- | --- | --- | --- |
| 当前流程全局动作 | 发起 | `PROCESS_STARTING`（发起前） | `PROCESS_START_EXECUTED` |
| 当前流程全局动作 | 撤回 | `PROCESS_WITHDRAWING`（撤回前） | `PROCESS_WITHDRAW_EXECUTED` |
| 当前流程全局动作 | 终止 | `PROCESS_TERMINATING`（终止前） | `PROCESS_TERMINATE_EXECUTED` |
| 用户任务节点 | 审批、驳回、重提、自定义办理 | `TASK_FORM_SAVING`（表单保存前）、`TASK_ADVANCING`（引擎推进前） | `TASK_SUBMITTED` |
| 用户任务节点 | 转办 | `TASK_TRANSFERRING`（转办前） | `TASK_TRANSFERRED` |
| 用户任务节点 | 发起加签 | `TASK_ADD_SIGNING`（加签前） | `TASK_ADD_SIGNED` |
| 源用户任务节点 | 加签源任务提交暂存 | `ADD_SIGN_SOURCE_SUBMITTING`（暂存前） | `ADD_SIGN_SOURCE_SUBMITTED` |
| 源用户任务节点 | 本地加签子任务办理 | `ADD_SIGN_TASK_COMPLETING`（办理前） | `ADD_SIGN_TASK_COMPLETED` |

上述时机由共用业务事务服务在实际步骤处触发，HTTP 和业务 SDK 共用公共命令编排，后台加签收口进入相同的原任务办理服务。成功命令的幂等重放及已有活动流程的发起重放不再次触发动作。Flowable 原生跳过没有用户任务办理命令，只产生实际引擎生命周期事件，不伪造审批或本地加签业务操作。

### 触发边界

1. 平台身份、任务归属、操作开关和状态检查通过后，才执行前置动作。前置动作先于自动认领、节点表单写入、办理人变更、加签持久化或引擎删除。发起入口为避免重复启动，先在同一事务内占用幂等流程链接；前置动作失败会回滚该占用。
2. 审批按 `TASK_FORM_SAVING → 自动认领及保存节点表单 → TASK_ADVANCING → 下一审批人重验 → 引擎办理 → 本地待办及实体活动同步 → TASK_SUBMITTED` 执行。影响路由和候选人的变量必须在 `TASK_ADVANCING` 或更早阶段写入，后续仍会重验下一审批人。
3. 转办不完成引擎任务，因此只触发转办业务时机及实际的办理人变更引擎事件；`TASK_TRANSFERRING` 位于转办表单保存及办理人变更之前，不触发 `TASK_ADVANCING`。
4. 发起前业务实体记录已由实体写入口保存，但引擎实例尚未创建；“发起前”不代表“业务实体表单第一次保存前”。`processInstanceId` 和 `taskId` 为空。`PROCESS_STARTING` 可用 `setProcessVariable(s)` 准备启动变量；平台再次拒绝改写发起人、实体身份及内部控制变量，再用最终变量准备人员集合和启动引擎。
5. `*_EXECUTED`、`TASK_SUBMITTED` 等执行后时机位于本次业务步骤及必要同步完成之后，仍在业务事务内。它们不是整个外层事务的 `beforeCommit`。选择 `AFTER_COMMIT` 后只在同事务写入 Outbox，整个事务提交后才可由工作线程领取；外层回滚不会发送通知。
6. `TASK_COMPLETING` 保留原编码及引擎 `TASK_COMPLETED` 映射，表单此时已经保存。旧配置继续执行；新增校验使用明确的业务时机，不按该名称推断表单、引擎或提交边界。

### 上下文与执行限制

- `businessOperation` 区分 START / WITHDRAW / TERMINATE / SUBMIT / TRANSFER / ADD_SIGN / ADD_SIGN_SOURCE_SUBMIT / ADD_SIGN_COMPLETE。
- `taskKind=ENGINE` 表示原引擎任务；`LOCAL_ADD_SIGN` 表示本地子任务，`taskId` 是子任务 ID，`sourceTaskId` 是源任务 ID，`elementId` 仍绑定源节点。
- `submittedFormData` 是本次提交表单的只读深拷贝；`operationParameters` 提供意见、转办目标或加签类型和有效人员。它们与动作配置的 `extraParams` 分开，随 Outbox 载荷保留，不授予额外写入权限。
- 前置处理器可校验输入，也可通过平台受控端口写业务数据和流程变量；不再根据时机名称施加“校验阶段只读”限制。提交表单及操作参数快照仍只读，节点字段授权和保留变量保护继续生效。
- 所有前置阶段强制 `IN_TRANSACTION + ROLLBACK`，保存、发布及运行时均检查，禁止 CONTINUE / CUSTOM / AFTER_COMMIT。设计器同步限制选项；元数据中的 `blocking`、`operationPhase` 提供同一约束和精确阶段。
- 执行后时机默认事务内回滚，也可选择提交后通知及其合法失败策略。最终变量快照在实例结束后读取历史根变量，排除同名任务本地变量和会签分支变量。

### 本地加签

加签源任务暂存输入时仅触发 `ADD_SIGN_SOURCE_*`；表单此时尚未按原任务写入实体。加签子任务仅触发 `ADD_SIGN_TASK_*`，没有独立的实体表单保存和引擎任务完成事件。最后子任务可能同步完成源任务，此时源任务才执行普通审批的三个业务阶段及实际引擎生命周期事件。若还需原办理人手选下一审批人，则恢复源待办，直到真正办理才触发这些阶段。

V137 放宽 `process_action_execution.process_instance_id` 为可空，支持发起前执行审计；实例不存在时通过发布版本、流程定义、实体编码及载荷中的实体数据 ID 定位。迁移不改写已有执行记录。

### V139 配置升级

V139 将旧“校验”编码转换为同一业务边界的现有编码，覆盖草稿、已发布及已删除配置，动作 ID、排序、启用状态、发布版本和依赖关系保持不变。旧枚举、分发调用及只读校验分支全部删除，运行时不保留别名。升级前停止旧版本写入，先执行 V139 再启用新版本。合并后按统一组内排序执行，不再隐式地先执行全部校验。

执行审计及历史载荷保留原始编码，不回写已发生的事实。旧环境导出的配置包应在升级后的环境重新导出；新接口不再接受已删除的时机编码。该迁移不涉及表结构、索引、约束或种子数据。

引擎事件与业务操作执行后仍分开：引擎启动/删除事件发生在引擎调用内部；对应业务执行后发生在流程关联、日志或本地待办同步完成之后。目录名称已明确标为“引擎…”，避免将两者视为相同边界。

### 验证记录（2026-11-17）

所选后端 **252 项测试通过，0 失败、0 错误、0 跳过**，包含既有流程动作、任务权限、候选认领及业务命令回归。正例和反例使用实际 Flowable、Spring 事务、MyBatis 和 H2 检查可观察的状态，覆盖：

- 同一“发起前”内的多个处理器按配置顺序执行校验或准备，均发生在实例创建前，准备变量进入真实引擎；拒绝发起或改写保留变量不会创建实例。
- 审批三个阶段分别观察表单尚未写入、表单已写入且任务仍存在、任务已完成；每阶段异常均回滚表单、引擎及命令回执，成功重放不重复触发。
- 候选人任务的表单保存前动作只触发一次，发生在真实自动认领之前；拒绝时另一数据库连接仍观察到未认领待办、原镜像和零认领日志。
- 引擎推进前修改变量会改变真实网关路由，并在下一审批人重验前可见；执行后修改变量不反向改变已选路径。
- 转办不触发审批推进；撤回和终止在删除前后观察真实实例，后置失败恢复删除前状态。
- 加签创建、源任务暂存及子任务办理分开检查顺序，覆盖等待、激活、驳回和最后子任务收口；前置拒绝不保存记录，子任务后置异常回滚本地更新。
- 提交前另一连接看不到通知；外层回滚不留下 Outbox，提交后失败重试沿用原输入、最终根变量、发布版本及幂等键，排除同名任务本地变量。
- 遍历全部标准时机的执行方式/失败策略组合；保存、运行时拒绝非法前置配置，缺少业务事务时不调用处理器、不入队。

前端时机约束的 **2 项正反例测试通过**，管理端及 Embed 生产构建通过。V137、V138 和 V139 的迁移测试均纳入回归；七种数据库脚本已静态核对，未连接业务库或执行七种数据库的实机升级。

V139 的3项迁移测试分别使用 H2 MySQL / PostgreSQL / Oracle 兼容模式执行实际 SQL，检查八种旧编码转换及其他字段保留。七份最终脚本仍为202张表、2,817个字段，除 V139 版本注释外全部 DDL 和种子逐字保持原状。

复现命令：

```sh
# 仓库根目录
mvn -o -f workflow-server/pom.xml -pl workflow-app -am clean test '-Dtest=FlowAction*Test,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
# workflow-frontend/workflow-web 目录
node src/shared/__tests__/flow-action-timing-policy.spec.js
npm run build
```
