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

每个实际业务边界只配置一次时机，处理器自行决定校验或处理。同一时机的事务内动作按配置顺序执行。目录共 22 个标准时机，包含流程级 7 个、用户任务节点 14 个和连线 1 个。

| 配置位置 | 操作 | 执行前 | 执行后 |
| --- | --- | --- | --- |
| 当前流程全局动作 | 发起 | `PROCESS_STARTING`（发起前） | `PROCESS_START_EXECUTED` |
| 当前流程全局动作 | 撤回 | `PROCESS_WITHDRAWING`（撤回前） | `PROCESS_WITHDRAWN`（撤回后） |
| 当前流程全局动作 | 终止 | `PROCESS_TERMINATING`（终止前） | `PROCESS_TERMINATED`（流程终止后） |
| 源用户任务节点 | 普通审批、驳回、重提、自定义办理、加签期间原任务提交及加签子任务审批 | `TASK_SUBMITTING`（审批办理前） | `TASK_SUBMITTED`（审批办理后） |
| 源用户任务节点 | 选择节点回退 | `TASK_RETURNING`（选择回退前） | `TASK_RETURNED`（选择回退后） |
| 用户任务节点 | 转办 | `TASK_TRANSFERRING`（转办前） | `TASK_TRANSFERRED` |
| 用户任务节点 | 发起加签 | `TASK_ADD_SIGNING`（加签前） | `TASK_ADD_SIGNED` |

上述操作前时机及普通操作后时机由共用业务事务服务在实际步骤处触发；原生发起登记用于补齐调用子流程的发起后，流程结束后的三个时机由引擎事件登记，在外层事务提交前统一收口。人工、管理员恢复、关联流程协同及 BPMN 自动结束共用该出口，同一事务内同一实例只触发一次。HTTP 和业务 SDK 共用公共命令编排，后台加签收口进入相同的原任务办理服务。成功命令的幂等重放及已有活动流程的发起重放不再次触发动作。Flowable 原生跳过没有用户任务办理命令，只产生实际引擎生命周期事件，不伪造审批或本地加签业务操作。

### 触发边界

1. 平台身份、任务归属、操作开关和状态检查通过后，才执行前置动作。审批办理前先于自动认领，加签前先于加签持久化，转办前先于办理人变更，撤回/终止前先于引擎删除。审批表单保存前是确认实际进入表单处理后的独立边界。发起入口为避免重复启动，先在同一事务内占用幂等流程链接；前置动作失败会回滚该占用。
2. 普通审批按 `TASK_SUBMITTING → 自动认领及变量准备 → TASK_FORM_SAVING → 节点表单处理 → TASK_ADVANCING → 下一审批人重验 → 审批结果写入 → 引擎办理及 NODE_COMPLETED → 后续路由 → 本地同步 → TASK_SUBMITTED` 执行。`TASK_FORM_SAVING` 在表单服务确认存在实体上下文后、可信数据投影及表单前置处理前触发；无实体上下文时不触发。影响下一审批人预览、重验的变量必须在 `TASK_ADVANCING` 或更早阶段写入；不涉及选人依据的本次审批结果及路由计算可在 `NODE_COMPLETED` 处理。
3. 转办不完成引擎任务，因此只触发转办业务时机及实际的办理人变更引擎事件；`TASK_TRANSFERRING` 位于转办表单保存及办理人变更之前，不触发 `TASK_ADVANCING`。
4. 发起前业务实体记录已由实体写入口保存，但引擎实例尚未创建；“发起前”不代表“业务实体表单第一次保存前”。`processInstanceId` 和 `taskId` 为空。`PROCESS_STARTING` 可用 `setProcessVariable(s)` 准备启动变量；平台再次拒绝改写发起人、实体身份及内部控制变量，再用最终变量准备人员集合和启动引擎。
5. `*_EXECUTED`、`TASK_SUBMITTED` 等执行后时机位于本次业务步骤及必要同步完成之后，仍在业务事务内。它们不是整个外层事务的 `beforeCommit`。选择 `AFTER_COMMIT` 后只在同事务写入 Outbox，整个事务提交后才可由工作线程领取；外层回滚不会发送通知。
6. `TASK_ADVANCING`（审批流转前，表单保存后）在引擎办理前，本次审批结果尚未汇总写入；`NODE_COMPLETED`（节点离开前）在当前执行实例完成后、继续路由前，此时已能读取本次审批结果。`TASK_SUBMITTED`（审批办理后）位于本次提交及同步处理之后，可能已经创建下一待办，也可能仍在等待加签。这三个边界不能按名称或相邻调用合并；会签节点离开按实际执行实例触发，不表示全员完成。

### 上下文与执行限制

选择回退前在校验当前办理权、已发布策略和目标活动后执行。选择回退后在目标待办创建、待办同步及退回记录写入后执行。两者的 `elementId` 和 `taskId` 始终指向发起回退操作的用户任务，目标节点正常执行进入节点和待办创建等生命周期动作。回退参数包括 `comment`、`targetNodeId`、`targetActivityInstanceId`，后置阶段另含 `targetTaskId`。

- `businessOperation` 区分 START / COMPLETE / WITHDRAW / TERMINATE / SUBMIT / RETURN / TRANSFER / ADD_SIGN。普通审批、加签原任务提交及子任务审批均为 SUBMIT，通过任务类型和加签输入区分。
- `taskKind=ENGINE` 表示原引擎任务；`LOCAL_ADD_SIGN` 表示本地子任务，`taskId` 是子任务 ID，`sourceTaskId` 是源任务 ID，`elementId` 仍绑定源节点。
- `submittedFormData` 是本次提交表单的只读深拷贝；`operationParameters` 提供意见、转办目标或加签类型和有效人员。它们与动作配置的 `extraParams` 分开，随 Outbox 载荷保留，不授予额外写入权限。
- 前置处理器可校验输入，也可通过平台受控端口写业务数据和流程变量；不再根据时机名称施加“校验阶段只读”限制。提交表单及操作参数快照仍只读，节点字段授权和保留变量保护继续生效。
- 所有前置阶段强制 `IN_TRANSACTION + ROLLBACK`，保存、发布及运行时均检查，禁止 CONTINUE / CUSTOM / AFTER_COMMIT。设计器同步限制选项；元数据中的 `blocking`、`operationPhase` 提供同一约束和精确阶段。
- 普通操作后时机默认事务内回滚；流程正常结束后、撤回后和流程终止后默认提交后重试。均可按合法组合选择同事务处理或 Outbox 通知。最终变量快照在实例结束后读取历史根变量，排除同名任务本地变量和会签分支变量。

### 本地加签

加签期间原办理人提交审批，以及每位加签人办理子任务，都使用 `TASK_SUBMITTING → 本次处理 → TASK_SUBMITTED`。办理后表示本次提交处理完成，可能仍在等待其他加签，不代表节点或流程完成。加签上下文通过 `taskKind=ENGINE / LOCAL_ADD_SIGN`、当前 `taskId`、`sourceTaskId` 及 `operationParameters.type=BEFORE / AFTER / PARALLEL` 区分；子任务借用源节点绑定但保留自身任务身份。

原任务提交暂存动作、意见和输入，没有实际节点表单写入或引擎推进；子任务本身也没有独立表单提交或 Flowable Task。这两类提交不伪造 `TASK_FORM_SAVING`、`TASK_ADVANCING` 或引擎完成事件。最后子任务可能同步完成原任务，后台原任务只触发实际表单保存、引擎推进及原生引擎事件，不重复原审批的 `TASK_SUBMITTING / TASK_SUBMITTED`。若需人工选择下一审批人，则恢复原待办，等新的交互提交时执行统一审批前后。

后加签的典型顺序：原办理人办理前 → 保存输入并激活子任务 → 原办理人办理后；最后加签人办理前 → 更新子任务 → 原任务实际表单保存前 → 原任务审批流转前及节点离开事件 → 加签人办理后。每个用户提交各一对，后台收口不增加一对。

V137 放宽 `process_action_execution.process_instance_id` 为可空，支持发起前执行审计；实例不存在时通过发布版本、流程定义、实体编码及载荷中的实体数据 ID 定位。迁移不改写已有执行记录。

### V139 配置升级

V139 将旧“校验”编码转换为同一业务边界的现有编码，覆盖草稿、已发布及已删除配置，动作 ID、排序、启用状态、发布版本和依赖关系保持不变。旧枚举、分发调用及只读校验分支全部删除，运行时不保留别名。升级前停止旧版本写入，先执行 V139 再启用新版本。合并后按统一组内排序执行，不再隐式地先执行全部校验。

执行审计及历史载荷保留原始编码，不回写已发生的事实。旧环境导出的配置包应在升级后的环境重新导出；新接口不再接受已删除的时机编码。该迁移不涉及表结构、索引、约束或种子数据。

### V140 结束时机合并及触发边界

V140 将 `PROCESS_WITHDRAW_EXECUTED` 迁移为 `PROCESS_WITHDRAWN`，将 `PROCESS_TERMINATE_EXECUTED` 迁移为 `PROCESS_TERMINATED`；旧枚举及分发调用删除，不保留运行时别名。覆盖草稿、发布和已删除配置，保留各动作 ID、执行方式、失败策略、排序和依赖。两组原来各自配置的动作仍各执行一次，合并后按统一组内排序执行。升级时停止旧版本写入，执行 V140 后启用新代码；旧配置包需重新导出。审计及已入队快照保持原文，工作线程继续按快照执行。

三个结束时机均在真实引擎结束事件中登记，于 Spring 外层事务 `beforeCommit` 收口，等待本次命令的日志、待办清理、管理员异常处置及其他同步业务写入完成。同一事务、同一实例只分发一次，撤回或终止不会再触发正常完成；局部作用域结束而根实例仍存活时不分发。正常完成也覆盖无人工任务的自动流程。

`IN_TRANSACTION` 处理器在此边界执行，按失败策略决定回滚或继续；`AFTER_COMMIT` 只同事务写入 Outbox。外层事务先失败或标记回滚时不调用结束动作，也不留下通知。实体流程状态和链接结束状态仍由既有状态同步 Outbox 最终更新，不能假定结束动作执行时这些异步投影已刷新。

V144 起不再公开独立引擎启动时机，统一使用发起后。普通发起在流程关联和待办同步完成后执行；调用子流程等直接引擎启动路径在外层事务提交前补齐。普通审批办理后仍在业务步骤末尾触发，未统一推迟到 `beforeCommit`。

### V141 统一审批时机

V141 将四种加签办理专用编码转换为 `TASK_SUBMITTING / TASK_SUBMITTED`，旧枚举和分发调用删除，运行时不保留别名。`TASK_SUBMITTED` 名称统一为“审批办理后”，新增“审批办理前”；发起加签本身仍使用加签前/加签操作执行后。

迁移覆盖草稿、发布和删除配置，保留各动作 ID、排序、执行方式、失败策略、发布版本和依赖。合并后使用统一时机及组内排序，同一个处理器若只适用于某类任务，应读取上述任务上下文；处理器的时机声明同步改用新编码。旧 `TASK_FORM_SAVING` 配置保留表单边界语义，若逻辑要求在候选自动认领前阻断，应配置 `TASK_SUBMITTING`。历史审计和已入队快照保持原文；升级前停止旧版本写入，执行迁移后启用新版本，配置包重新导出。

### V142 合并引擎任务完成时机

`TASK_ADVANCING` 改名“审批流转前（表单保存后）”，保留表单处理后、下一审批人重验前的边界。`NODE_COMPLETED` 改名“节点离开前”，说明当前执行实例已完成且尚未继续路由；会签、循环按实际执行实例触发，不承诺全员完成。本次审批结果可用于路由计算，但不能改写已通过下一审批人重验的选人依据。

V142 将旧 `TASK_COMPLETING` 配置迁移到 `NODE_COMPLETED`，不提前到尚未汇总审批结果的 `TASK_ADVANCING`。删除旧枚举、公开事件映射、处理器声明及前端说明，运行时不保留别名。普通任务的原生 `TASK_COMPLETED` 只在引擎命令内暂存完成任务的 ID、名称和办理人，随后由匹配实例、执行及节点的离开事件消费，不独立触发动作；同命令结束自动释放，避免循环或回滚串用身份。Flowable 会签、顺序多实例分支不发送普通 `ACTIVITY_COMPLETED`，因此使用其实际任务完成事件补齐同一个 `NODE_COMPLETED`；会签整体结束不再追加一遍，提前结束而被取消的分支不伪造节点离开。变量在实际触发时读取已写入的最新结果。

迁移覆盖草稿、发布、禁用及已删除配置，保留动作身份、排序、执行方式、失败策略、发布版本和依赖。原有节点离开动作和迁入动作按同一时机排序，各自执行一次。历史审计及已入队快照保留原文并继续消费。升级时停止旧版本写入，执行 V142 后启用新代码；旧配置包重新导出，自定义处理器的时机声明同步改为 `NODE_COMPLETED`。

### V144 合并发起时机

按产品决定，将旧 `PROCESS_STARTED` 配置全部后移至 `PROCESS_START_EXECUTED`，不按处理器用途提前到发起前。移除旧枚举及独立动作分发，`PROCESS_START_EXECUTED` 名称改为“发起后”，`PROCESS_COMPLETED` 名称改为“流程正常结束后”。当前全局7项、用户任务节点12项、连线1项，共20项。

原生启动事件仅登记事实，不立即执行动作。普通公共发起服务完成流程关联和待办同步后消费登记，同一事务同一实例仅执行一次；调用子流程等没有普通发起服务调用栈的路径在外层事务提交前补齐。待办及权限投影先于此收口执行；事务同步列表隔离 `REQUIRES_NEW`，内层发起不会消费外层登记。流程在发起过程中立即结束时，先发起后、再流程正常结束后；任一事务内回滚策略动作失败，实例及本次同步写入共同回滚，不留下待通知记录。

发起后首节点已经执行，不能用此时机准备首节点变量或改变已走过的初始分支；首节点所需输入应配置发起前。项目发起、系统变更和成员变更等旧业务处理器及随包资产统一改为发起后，仍允许按回滚策略阻断本次事务。子流程实体初始化和知会使用的内部原生监听保留，不再作为独立流程动作选项。

迁移覆盖草稿、发布、禁用和已删除配置，保留动作身份、版本、参数、顺序、执行方式、失败策略及依赖。原有发起后动作和迁入动作按统一组内排序各执行一次；历史审计和已入队快照保持原文并继续消费。升级时停止旧版本写入，先执行 V144，再启用新代码；旧配置包重新导出。工作区已有 V143 实体选项字段迁移，本轮未改写或复用该版本。

### 下拉框顺序

标准项采用下列顺序，扩展自定义时机保留提供器顺序并追加。不同操作分组展示，不能把独立结束路径理解为一个流程必然逐项经历的时间线。初始已指定办理人的任务先产生分配事件再产生创建事件，后续认领或转办仍可重复产生分配事件。



**全局流程**

| 顺序 | 执行时机 | 编码 |
| --- | --- | --- |
| 1 | 发起前 | `PROCESS_STARTING` |
| 2 | 发起后 | `PROCESS_START_EXECUTED` |
| 3 | 流程正常结束后 | `PROCESS_COMPLETED` |
| 4 | 撤回前 | `PROCESS_WITHDRAWING` |
| 5 | 撤回后 | `PROCESS_WITHDRAWN` |
| 6 | 终止前 | `PROCESS_TERMINATING` |
| 7 | 流程终止后 | `PROCESS_TERMINATED` |

**用户任务节点**

| 顺序 | 执行时机 | 编码 |
| --- | --- | --- |
| 1 | 进入节点时 | `NODE_ENTERED` |
| 2 | 办理人分配或变更后 | `TASK_ASSIGNED` |
| 3 | 待办创建后 | `TASK_CREATED` |
| 4 | 审批办理前 | `TASK_SUBMITTING` |
| 5 | 审批表单保存前 | `TASK_FORM_SAVING` |
| 6 | 审批流转前（表单保存后） | `TASK_ADVANCING` |
| 7 | 节点离开前 | `NODE_COMPLETED` |
| 8 | 审批办理后 | `TASK_SUBMITTED` |
| 9 | 选择回退前 | `TASK_RETURNING` |
| 10 | 选择回退后 | `TASK_RETURNED` |
| 11 | 转办前 | `TASK_TRANSFERRING` |
| 12 | 转办操作执行后 | `TASK_TRANSFERRED` |
| 13 | 加签前 | `TASK_ADD_SIGNING` |
| 14 | 加签操作执行后 | `TASK_ADD_SIGNED` |

其中审批办理前后覆盖本地加签；表单保存前、审批流转前及节点离开前仅在实际执行对应步骤时触发。节点离开后先路由、再触发审批办理后，流程结束动作则延迟到外层事务提交前，不应把此表当成所有分支都会经过的单一路径。

**连线**

| 顺序 | 执行时机 | 编码 |
| --- | --- | --- |
| 1 | 分支选中、进入目标节点前 | `TRANSITION_TAKEN` |

### V144 验证记录（2026-11-18）

相关后端 **377项测试通过，43类，0失败、0错误、0跳过**。普通发起的真实原生事件只登记、不提前触发动作；关联和待办同步后统一执行，重复原生回调及重复业务触发仍只执行一次。原有发起后动作和迁入动作按同组配置顺序各执行一次。发起后首节点已经执行，若该首节点失败，不执行发起后也不留下通知。

真实 Flowable / Spring / MyBatis / H2 覆盖普通发起和同步立即结束的正反例：发起后拒绝、发起后外层异常均回滚引擎实例、历史、关联/待办写入及通知；正常完成先发起后再流程正常结束后，提交后通知保留发起时变量。调用子流程没有公共发起命令调用栈，仍在真实权限/待办投影收口后补齐相同发起后动作，各自保留父/子实体身份及最新变量；子流程拒绝共同回滚父子实例和同事务投影。`REQUIRES_NEW` 内层发起不消费外层登记。既有调用子流程实体初始化和待办权限回归同时通过。

V144 的3项真实 Flyway 升级用例分别覆盖 H2 MySQL / PostgreSQL / Oracle 兼容模式、全部旧配置状态、配置字段和审计保留、validate 及再次 migrate；已入队旧启动编码快照继续消费。项目三份实际随包资产和处理器声明一致，目录逐项断言20项及新名称，旧启动标准编码查找失败。

前端时机约束2项、页面配置总审计、管理端及 Embed 构建通过。七份空库脚本本轮仅新增 V144 说明；最终包含已有 V143 的只读选项接口字段，均为202表、2,818字段，全部表名和字段清单一致。历史迁移无修改、删除或重命名；未连接业务库或进行七种数据库的实机升级、全量导入。

验证命令：

```sh
FLOW_INBOX_MYSQL_TEST=false mvn -o -f workflow-server/pom.xml -pl workflow-app -am test '-Dtest=FlowAction*Test,NodeFormSubmissionServiceTest,EntityFieldAssignmentFlowableTest,AssigneeIncident*Test,EmptyAssigneePolicyFlowableIntegrationTest,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest,TaskInboxDatabaseTest,CalledProcessEntityRuntimeTest,ProjectExtensionAcceptanceFlowActionHandlerTest,ProjectStartActionTimingTest' -Dsurefire.failIfNoSpecifiedTests=false
# 以下在 workflow-frontend/workflow-web 执行
node src/shared/__tests__/flow-action-timing-policy.spec.js
node src/__tests__/page-config-audit.spec.js
npm run build
```

### V142 验证记录（2026-11-18）

相关后端 **344项测试通过，39类，0失败、0错误、0跳过**。真实 Flowable / Spring / MyBatis / H2 验证普通审批按 `TASK_SUBMITTING → TASK_FORM_SAVING → TASK_ADVANCING → NODE_COMPLETED → TASK_SUBMITTED` 执行；流转前本次审批结果尚未产生，节点离开前已能读取本次通过/驳回结果并写入真实路由变量。流转前、节点离开前或办理后异常均恢复待办、认领、审批投影和同事务变量；节点离开异常不留下提交后通知，成功后的通知保留实际任务及审批结果快照。

并行会签和顺序多实例逐个实际完成任务触发一次节点离开，首个办理不等待全员；会签拒绝时业务写入及引擎变量一起回滚。或签提前结束只触发实际完成分支，不为被取消分支伪造动作。原生跳过节点离开时任务 ID 为空，不伪造审批或待办。加签最后子任务推动原任务离开时仅触发原任务节点动作，异常回滚本次收口，不重复审批前后。命令内身份匹配测试排除其他执行实例、重复消费及跨命令串用。

V142 的3项 Flyway 测试覆盖 H2 MySQL / PostgreSQL / Oracle 兼容模式的实际升级、配置字段保留、历史执行、validate 及再次 migrate；旧时机已入队快照仍能继续执行。目录逐项断言21项、各作用域顺序及新名称，并拒绝查找旧标准编码。前端时机约束2项、页面配置总审计、管理端和 Embed 生产构建通过；页面审计同步动态分组标题及退役时机断言。

七份空库 SQL 的表名及字段清单一致，均为202表、2,817字段；剔除 V140～V142 新增的版本说明后与 Git 原文件逐字一致。本轮仅新增 V142，沿用前轮 V140/V141，没有修改、删除或重命名历史 SQL / Java 迁移。未连接业务库或执行七种数据库的实机升级、全量导入。

验证命令：

```sh
mvn -o -f workflow-server/pom.xml -pl workflow-app -am test '-Dtest=FlowAction*Test,NodeFormSubmissionServiceTest,EntityFieldAssignmentFlowableTest,AssigneeIncident*Test,EmptyAssigneePolicyFlowableIntegrationTest,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest,ProjectExtensionAcceptanceFlowActionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false
# 以下在 workflow-frontend/workflow-web 执行
node src/shared/__tests__/flow-action-timing-policy.spec.js
node src/__tests__/page-config-audit.spec.js
npm run build
```

### V141 验证记录（2026-11-18）

所选后端 **322 项测试通过，37 类，0 失败、0 错误、0 跳过**。本轮在已有结束时机及命令回归基础上，验证统一审批前后的全部加签返回分支、普通审批四阶段、候选自动认领前拒绝、实际表单边界、源提交与最后子任务收口的完整嵌套顺序。真实 Flowable / Spring / MyBatis / H2 测试在源提交前后、子任务办理前后、原任务表单保存前及引擎推进前分别注入异常，检查真实引擎待办恢复、同事务 JDBC 写入回滚、先前已提交的源输入保留，以及后台原任务不重复触发审批前后。表单服务测试检查缺少实体上下文不调用扩展，保存前拒绝不读取表单或写实体，正常准备在可信数据投影前仅调用一次。目录逐项断言22项及各作用域顺序，旧四种标准编码均查找失败。

V141 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行实际 SQL，检查四种旧编码、草稿/发布/删除配置、其他字段、历史执行、validate 及再次 migrate。七份空库脚本逐字核对，本轮仅新增 V141 说明，DDL、字段、索引、约束、用途注释、种子及清理结果保持不变，仍为202表、2,817字段；SQL 和 Java 历史迁移无修改、删除或重命名。未连接业务库，七种数据库未执行实机升级或全量导入。

前端时机约束2项通过，管理端及 Embed 生产构建通过。配置面板“审批前校验”模板已使用 TASK_SUBMITTING；指南沿用后端排序，并说明统一审批上下文。既有页面总审计的动态标题断言失配见下方 V140 验证记录，本轮未修改该断言。

复现命令：

```sh
mvn -o -f workflow-server/pom.xml -pl workflow-app -am test '-Dtest=FlowAction*Test,NodeFormSubmissionServiceTest,EntityFieldAssignmentFlowableTest,AssigneeIncident*Test,EmptyAssigneePolicyFlowableIntegrationTest,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
# workflow-frontend/workflow-web
node src/shared/__tests__/flow-action-timing-policy.spec.js
npm run build
```

### V140 验证记录（2026-11-17）

所选后端 **303 项测试通过，35 类，0 失败、0 错误、0 跳过**。真实 Flowable / Spring / MyBatis / H2 覆盖人工撤回、人工终止、系统删除、管理员故障恢复、BPMN 终止、无人工任务的正常完成、局部子流程终止、重复事件及 REQUIRES_NEW 隔离；后置失败回滚，外层异常或 rollbackOnly 不执行结束动作。即使已在 beforeCommit 入队，其他连接仍不能看到未提交通知；升级前已入队的两个旧时机快照仍能成功消费。目录测试逐项断言流程8项、用户任务16项、其他节点2项和连线1项的返回顺序，真实任务事件验证初始分配先于创建、任务完成先于节点完成。

V140 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行实际 SQL，检查两种旧编码、草稿/发布/删除配置、其他字段、历史执行、重复 migrate 和 validate。七份最终脚本除新增 V140 说明外与原文件逐字一致，结构、字段清单、方言类型、索引、约束、种子及注释不变，仍为202表、2,817字段。未连接业务库；未执行七种数据库的实机升级或全量导入。

前端时机约束2项通过，管理端及 Embed 生产构建通过。页面总审计存在已有静态断言失配：`page-config-audit.spec.js:1744` 查找固定 `title="说明与参数"`，HEAD 的配置面板已使用动态标题；本次未修改该面板和该断言。

复现命令：

```sh
mvn -o -f workflow-server/pom.xml -pl workflow-app -am test '-Dtest=FlowAction*Test,EntityFieldAssignmentFlowableTest,AssigneeIncident*Test,EmptyAssigneePolicyFlowableIntegrationTest,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
# workflow-frontend/workflow-web
node src/shared/__tests__/flow-action-timing-policy.spec.js
npm run build
```

### V139 验证记录（2026-11-17）

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
