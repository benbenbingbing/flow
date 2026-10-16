# 流程编程接口设计

日期：2026-10-16。状态：已按本设计实施，验证结果与具体限制见 [实施记录](process-programming-api-implementation.md)。

本文记录后端流程公共能力的接口设计，供平台内部和其他 biz 模块通过 Java 调用。运行代码、接入示例、迁移及测试记录见实施文档；不扩展实体数据查询或变更能力。

## 1. 设计结论

采用可注入的公共接口和唯一业务实现，按查询、变量、任务办理、实例控制划分。业务模块只依赖 `workflow-port`；实现流程动作等扩展时再依赖 `workflow-spi`。Spring 装配和 Flowable 适配保留在 `workflow-process`。

不新增静态 `FlowableUtils` 或要求业务通过 Spring 容器查找 Bean。纯粹的字段转换工具可以在流程模块内部使用，跨模块的“辅助方法”以 Port 提供，方便事务代理、替换实现和单元测试。

| 公共入口 | 主要用途 | 放置位置 |
| --- | --- | --- |
| `ProcessQueryPort` | 实例、待办、历史、活动节点和操作能力查询 | `contracts.process.query.port` |
| `ProcessVariablePort` | 流程变量、任务本地变量的读取与补丁写入 | `contracts.process.variable.port` |
| `ProcessTaskCommandPort` | 认领、审批、转办、退回、重提、加签、知会 | `contracts.process.task.port` |
| `ProcessInstanceCommandPort` | 撤回、终止、触发等待中的接收任务 | `contracts.process.instance.port` |
| 现有 `ProcessRuntimePort` | 保留实体绑定流程的内部启动和重新发起判断 | 保留现有包和职责 |
| 现有 `ProcessCatalogPort` | 保留流程定义目录和绑定状态查询 | 保留现有包和职责 |

首轮不再叠加一个转发全部方法的大型 Helper。业务需要什么能力就注入对应 Port；流程动作继续通过 `FlowActionContext` 获得当前流程的快捷方法。

调用结构：

```text
平台应用服务 / biz 模块 / 流程动作
                 |
      workflow-port 公共接口
                 |
 workflow-process 应用服务与适配器
                 |
 现有授权、任务编排、状态同步、Flowable、持久化
```

查询可共用内部读取组件；命令必须共用同一业务执行路径。Controller 负责请求转换，不能继续独占业务必需的校验和编排，也不能由 Java 公共入口反向调用 Controller。

## 2. 查询接口

以下签名省略 import。辅助模型的字段和行为见第 6 节。

```java
public interface ProcessQueryPort {
    // 同时覆盖运行中和已结束的实例；缺失返回 Optional.empty()。
    Optional<ProcessInstanceView> findInstance(String processInstanceId);

    // 适合后续必须执行操作的业务路径；缺失抛标准异常。
    ProcessInstanceView requireInstance(String processInstanceId);

    ProcessPage<ProcessInstanceView> pageInstances(ProcessInstanceQuery query);

    // 以可访问的一轮为锚点，返回同一业务记录的可访问流程轮次。
    List<ProcessRoundView> listRounds(String anchorProcessInstanceId);

    // 活动任务包含平台本地加签任务，缺失或已完成返回 empty。
    Optional<ProcessTaskView> findActiveTask(String taskId);

    // 返回该实例下全部可见活动任务，不隐式选取第一条。
    List<ProcessTaskView> listActiveTasks(String processInstanceId);

    ProcessPage<ProcessTaskView> pageTasks(ProcessTaskQuery query);
    ProcessPage<ProcessTaskHistoryView> pageTaskHistory(ProcessHistoryQuery query);
    ProcessPage<ProcessOperationView> pageOperations(ProcessHistoryQuery query);

    // 包含人工节点与正在等待的接收任务等，用于定位当前执行位置。
    List<ProcessActivityView> listActiveActivities(String processInstanceId);

    // 能力结果用于判断/展示，不是授权凭据，执行命令时重新校验。
    TaskCapabilities getTaskCapabilities(String taskId);
    NextApprovalPreview previewNextApproval(NextApprovalQuery query);
    ProcessPage<ApproverOption> pageNextApproverOptions(NextApproverOptionsQuery query);
}
```

### 查询语义

- `find` 的空结果只表示目标缺失或不再活动；目标存在但无权访问时抛授权异常，不用空集合掩盖技术故障。
- 分页查询先施加授权和业务筛选，再计数与分页。不能拿一页数据在内存中过滤后返回不准确的 total。
- 默认页码从 1 开始，每页 20，最大 100；非法页码或超限参数明确报错。排序字段使用枚举，默认时间降序并追加 ID 保证稳定顺序。
- `pageTasks` 至少支持 `MY_TODO`、`MY_DONE`、`INSTANCE_ACTIVE`。前两者的用户来自实际执行身份；按实例查询必须提供实例 ID 并通过对应范围检查。
- `pageInstances` 支持 `MY_STARTED`、`PARTICIPATED`、`READABLE`。`READABLE` 是调用方实际可读范围，不是平台全部实例。
- 平台管理查询有单独的授权策略，不提供 `ignorePermission`、任意用户 ID 或 `system=true` 参数来扩大查询范围。
- 待办查询复用现有候选用户、用户组、角色和本地加签任务规则。不能仅使用 Flowable 原生候选查询替代平台待办范围。
- 任务历史与操作历史分开。一次转办、重提或加签可能产生多条操作记录，历史项需要独立 `historyId`/`operationId`，不能只按 taskId 去重。
- `listRounds` 按每一轮检查读取范围，不因知道一轮 ID 就自动获得全部历史。
- 审批预览沿用现有需要延期计算的语义。返回 `READY / DEFERRED / BLOCKED`、原因与 scopeKey；不为了预览提前执行有业务副作用的数据源。
- 查询默认返回轻量流程投影，不附带实体全量数据、页面令牌、表单布局或 Flowable 对象。

## 3. 变量接口

```java
public interface ProcessVariablePort {
    Map<String, Object> getProcessVariables(
            String processInstanceId, VariableReadMode readMode);

    VariableValue getProcessVariable(
            String processInstanceId, String name, VariableReadMode readMode);

    Map<String, Object> getTaskLocalVariables(
            String taskId, VariableReadMode readMode);

    VariableValue getTaskLocalVariable(
            String taskId, String name, VariableReadMode readMode);

    ProcessCommandReceipt patchProcessVariables(ProcessVariablePatch command);
    ProcessCommandReceipt patchTaskLocalVariables(TaskVariablePatch command);
}

public enum VariableReadMode {
    RUNTIME_ONLY, HISTORY_ONLY, RUNTIME_OR_HISTORY
}

// 区分“变量不存在”和“变量存在且值为 null”。
public record VariableValue(boolean present, Object value) {}

public record ProcessVariablePatch(
        CommandMeta meta,
        String processInstanceId,
        Map<String, Object> setValues,
        Set<String> removeNames) {}

public record TaskVariablePatch(
        CommandMeta meta,
        String taskId,
        Map<String, Object> setValues,
        Set<String> removeNames) {}
```

### 变量语义

1. `RUNTIME_OR_HISTORY` 只有在确认实例/任务已退出运行态时回退历史。数据库错误、反序列化错误不能被吞掉后伪装成历史结果或空结果。
2. 流程变量指根流程实例作用域。历史读取也按作用域过滤，不能把任务本地、执行分支变量按同名键覆盖到流程变量上。
3. 任务本地变量仅对真实引擎任务有效。平台本地加签任务没有等价引擎作用域，明确返回 `VARIABLE_SCOPE_UNSUPPORTED`；操作所属流程变量应使用 processInstanceId。
4. 一个补丁同时设置/删除多个变量，原子执行；同名键不能同时出现在 setValues 和 removeNames 中。`setValues` 的 null 表示写入 null，删除必须使用 removeNames。
5. 已结束实例和已完成任务不允许通过本接口修改历史。历史查询能力不代表历史修改能力。
6. 返回 Map 及嵌套集合是脱离引擎状态的只读快照，修改返回值不能产生持久化副作用。公共变量值限制为约定的标量、日期表示和 JSON 可表达集合，不向 biz 泄露引擎实体或序列化代理。
7. 平台保留变量集中维护。发起人、业务绑定、`_wf*`、跳过控制、多实例计数及审批编排变量由对应业务命令维护，公共变量写入拒绝覆盖。读取隐藏内部控制数据；身份与绑定等允许查询的信息由明确投影提供。
8. 普通写变量不会自动更新实体字段，也不替代审批/转办命令；使用者不能通过改 assignee、approved 等编排字段模拟办理。
9. 不额外开放任意 execution-local 写入。需要操作多实例分支时，应通过明确业务命令处理。

为方便日常调用，实现时可以增加委托到上述方法的 default 方法，例如单变量 `setProcessVariable(meta, instanceId, name, value)`。所有便捷方法仍走同一补丁入口，不能出现另一条写入路径。

## 4. 任务办理接口

```java
public interface ProcessTaskCommandPort {
    TaskCommandResult claim(ClaimTaskCommand command);
    TaskCommandResult complete(CompleteTaskCommand command);
    TaskCommandResult transfer(TransferTaskCommand command);
    TaskCommandResult returnToNode(ReturnTaskCommand command);
    TaskCommandResult resubmit(ResubmitTaskCommand command);
    AddSignResult addSign(AddSignCommand command);
    TaskCommandResult cancelAddSign(CancelAddSignCommand command);
    ManualCcResult addCc(ManualCcCommand command);
}
```

| 命令 | 字段（除 meta 外） | 关键业务语义 |
| --- | --- | --- |
| `ClaimTaskCommand` | taskId | 只认领给实际操作人；已由自己认领可返回未变化，其他人持有则冲突 |
| `CompleteTaskCommand` | taskId、actionCode、submission | 支持通过、拒绝及已发布配置允许的自定义动作；统一处理普通任务和加签任务 |
| `TransferTaskCommand` | taskId、targetUserId、submission | 更新引擎办理人、本地新待办及转办日志；禁止同时选择下一节点审批人 |
| `ReturnTaskCommand` | taskId、targetNodeId、submission | 退回服务端认定可退回的节点，不能任意跳转；与普通拒绝分开 |
| `ResubmitTaskCommand` | taskId、submission | 在当前实例的退回待办上重新提交，不创建新一轮实例 |
| `AddSignCommand` | taskId、type、userIds、comment | BEFORE、AFTER、PARALLEL；人员去重并校验，完成策略初期仅支持 ALL |
| `CancelAddSignCommand` | addSignId、reason | 撤销指定加签编排，恢复/推进源任务须沿用平台规则 |
| `ManualCcCommand` | taskId、userIds、comment | 复用人工知会开关、人员解析和通知机制 |

公共提交模型：

```java
public record TaskSubmission(
        String comment,
        String actionLabel,
        Map<String, Object> formData,
        TaskFormReference formReference,
        NextApproverSelection nextApprovers) {}

public record TaskFormReference(String formId, String configHash) {}
```

- taskId 是服务端解析实例、节点、业务记录和发布表单的权威依据，不允许调用方通过额外传入 entityCode/recordId 改变任务绑定。
- formReference 用于校验调用方所依据的表单版本。Java 调用方没有页面摘要时，由服务端解析当前任务绑定的发布表单，仍执行按钮条件和字段规则；省略摘要不意味着跳过校验。
- formData 只包含此次提交的表单补丁，不接受全量流程运行态字段。原有节点表单保存和校验继续复用，本设计不重做实体接口。
- actionCode 必须属于当前发布配置允许的动作。actionLabel 是显示文本，不决定审批结果。
- 下一审批人选择必须包含预览 scopeKey 与各目标节点的人选，执行时重新校验。加签子任务、转办等不支持的组合直接拒绝。
- `TaskCommandResult` 返回操作回执、所属实例、被操作任务 ID、任务处理结果与实例状态。加签可能只处理了子任务或登记延后完成，不能一律声称主流程已经推进。

### 办理路径必须统一

```text
接收命令 → 校验调用身份与幂等请求
         → 校验任务归属、发布版本和节点操作规则
         → 校验已发布表单提交按钮及提交字段
         → 识别普通任务 / 本地加签任务 / 加签源任务
         → 执行节点表单处理、下一审批人重验
         → 执行引擎及加签编排
         → 同步本地待办、日志、既有流程关联状态
         → 写回执 → 随事务一起提交
```

加签和普通任务的分支由平台解析，业务模块不用先调用 `isAddSignTask()` 再决定调用哪个服务。当前 Controller 中的完整分支需要迁入公共应用服务，不能仅把 `TaskActionService.completeTask()` 原样导出。

退回与重提目前有多条历史路径。实现该组接口之前，应明确一个权威路径并将旧入口委托到它；不直接把旧方法逐个包装后宣称语义已经统一。

## 5. 实例控制及启动边界

```java
public interface ProcessInstanceCommandPort {
    ProcessCommandReceipt withdraw(WithdrawProcessCommand command);
    ProcessCommandReceipt terminate(TerminateProcessCommand command);
    ReceiveTaskTriggerResult triggerReceiveTask(TriggerReceiveTaskCommand command);
}
```

| 命令 | 字段（除 meta 外） | 业务约束 |
| --- | --- | --- |
| `WithdrawProcessCommand` | processInstanceId、reason | 沿用发起人及当前活动节点撤回限制，保留真实 WITHDRAWN 类型 |
| `TerminateProcessCommand` | processInstanceId、reason | 沿用终止身份与节点限制，真实结束类型为 TERMINATED |
| `TriggerReceiveTaskCommand` | processInstanceId、executionId、activityId、messageRef、variables | 校验执行实例属于目标流程、确为等待中的接收任务，核对消息约束；多个匹配执行必须指定 executionId |

接收任务触发中的变量写入与引擎推进在同一事务内完成，复用公共变量边界。executionId/activityId 至少提供一个，同时提供时必须一致，不能忽略矛盾参数。

### 启动保留现有契约

现有 `ProcessRuntimePort.start(ProcessStartRequest)` 继续保留，不复制为新的同义接口。但其性质必须写清楚：它是实体绑定流程的内部启动能力，要求调用方已经完成业务记录准备、授权和同事务锁定，启动结果仍由原编排回写业务记录。

当前平台完整发起还包含实体记录保存、提交审计、流程关联和运行态回写。本次排除实体接口调整，因此不承诺提供“只传 processKey 即可完成全部业务发起”的新 API，也不让普通 biz 调用示例直接使用这个底层入口。

`canRestart()` 仅是当前状态判断，不是重新发起授权。撤回后的重新发起新一轮，与本节 `resubmit()` 在原实例退回任务上重提，必须保持不同语义。完整的便捷发起接口应在后续业务记录与流程编排边界一起设计时确定。

本轮不开放挂起/恢复、强制换人、任意节点跳转、删除历史等尚未形成统一平台业务语义的操作，不把引擎的所有方法直接变成公共 API。

## 6. 公共模型及错误约定

### 查询模型

| 模型 | 核心字段 |
| --- | --- |
| `ProcessInstanceQuery` | scope、processConfigId/processKey、businessReference、生命周期、结束类型、发起时间区间、page、sort |
| `ProcessTaskQuery` | scope、processInstanceId、processKey、taskName、keyword、时间区间、page、sort |
| `ProcessHistoryQuery` | processInstanceId、可选 nodeId/taskId、时间区间、page、sort |
| `NextApprovalQuery` | taskId、actionCode、actionLabel、comment、formData |
| `NextApproverOptionsQuery` | taskId、scopeKey、目标节点、keyword、page |
| `ProcessBusinessReference` | entityCode、entityRecordId；仅定位现有流程关联，不新增实体读写能力 |
| `ProcessPage<T>` | items、total、pageNumber、pageSize；放在流程公共 model 包，不依赖 core 的分页类型 |

筛选条件采用明确字段和枚举，不支持 SQL 片段或任意列名。时间区间采用 `[from, to)`，契约时间用 `Instant`；适配器明确处理旧存储时区，不依赖机器默认时区猜测。

### 返回模型

| 模型 | 核心字段与用途 |
| --- | --- |
| `ProcessInstanceView` | instanceId、processConfigId、engineDefinitionId、processKey、deploymentVersion、processVersionId、businessReference、startUserId、lifecycle、endType、startTime、endTime |
| `ProcessRoundView` | instanceId、generation、lifecycle、endType、startTime、endTime；一轮一个结果，不能用最新业务记录状态覆盖历史 |
| `ProcessTaskView` | taskId、engineTaskId（本地任务可空）、kind、instanceId、nodeId、taskName、status、assigneeId、createTime、endTime、canClaim |
| `ProcessTaskHistoryView` | historyId、taskId、kind、nodeId、办理人、结果、意见、开始/结束时间、取消原因 |
| `ProcessOperationView` | operationId、requestId、instanceId、taskId、操作编码、操作人、时间、说明 |
| `ProcessActivityView` | activityId、activityType、executionId、active；同一节点可有多个执行分支 |
| `TaskCapabilities` | 动作编码对应 allowed/reasonCode/message，以及允许的加签类型、退回目标等 |
| `NextApprovalPreview` | status、scopeKey、nodes、message；不会以空节点冒充预览成功 |
| `ApproverOption` | userId、显示名称、候选来源及是否可选 |

流程状态拆为生命周期和结束类型：生命周期建议 `RUNNING / SUSPENDED / ENDED`；结束类型 `COMPLETED / WITHDRAWN / TERMINATED`，未结束时为空。不能把业务审批“通过/拒绝”、实体业务状态混入生命周期。历史保留字段缺失时按明确兼容规则处理，不从用户自由文本猜测结束类型。

区分平台流程配置 ID、精确发布版本 ID 和 Flowable definition ID，避免继续用同一个 `processDefinitionId` 名称表达不同标识。

### 命令元数据与结果

```java
public record CommandMeta(String requestId) {}

public record ProcessCommandReceipt(
        String requestId,
        String operationId,
        String processInstanceId,
        String taskId,
        boolean changed,
        boolean replayed) {}
```

- requestId 代表一次业务意图：调用方首次生成，重试必须复用；不同操作步骤使用不同 requestId。
- 操作人、可信来源和追踪上下文由宿主绑定，不让业务请求任意声明。业务原因使用各命令的 reason/comment 字段。
- `TaskCommandResult` 在 receipt 外携带任务处理状态及操作后的实例状态；`AddSignResult` 额外返回 addSignId 和生成的 taskIds；`ManualCcResult` 返回新增知会数量；`ReceiveTaskTriggerResult` 返回被触发的 executionId。
- 命令成功正常返回，失败抛业务异常。不返回 HTTP `Result`、`ApiResponse`、Flowable 对象或 persistence record。
- 处于外层事务时，方法返回表示操作已执行并加入该事务，不表示外层事务已提交。不能把返回结果直接当成发送外部消息的提交确认。

### 错误

使用 `ProcessOperationException` 携带稳定 errorCode、用户可读 message 和必要的目标 ID；原因链保留给服务端诊断，不泄露敏感数据。

基本错误码：`PROCESS_NOT_FOUND`、`TASK_NOT_FOUND`、`TASK_ALREADY_COMPLETED`、`PROCESS_ALREADY_ENDED`、`PROCESS_ACCESS_DENIED`、`PROCESS_ACTOR_REQUIRED`、`TASK_STATE_CHANGED`、`NODE_OPERATION_NOT_ALLOWED`、`FORM_VERSION_MISMATCH`、`NEXT_APPROVER_SELECTION_INVALID`、`VARIABLE_NOT_WRITABLE`、`VARIABLE_SCOPE_UNSUPPORTED`、`IDEMPOTENCY_CONFLICT`。

旧 Service 的 Result.error 必须被适配为失败并保证事务回滚，不能只因为方法正常返回就生成成功回执。未知技术故障继续抛出，不统一吞成 false、空列表或“操作成功”。

## 7. 执行身份、事务与重试

### 同一接口在不同调用场景下使用

| 场景 | 身份与授权来源 |
| --- | --- |
| 请求内的内部服务、自定义按钮、biz Service | 宿主当前用户，通过现有 CurrentActorPort/授权组件取得 |
| 流程动作、监听器 | 平台签发的当前动作执行范围，绑定真实流程、动作和操作人；范围不能由前端参数或 biz 自行伪造 |
| 定时任务、Outbox/异步消费者 | 任务执行器绑定明确的服务身份和允许能力；没有身份不能默认获得管理员权限 |

上述执行范围是宿主的调用边界，不是给每个方法再增加十几个参数。来源、traceId 沿用现有审计上下文；权限不能仅从一个可随意 new 的 `sourceType=SYSTEM_TASK` 推导。普通 Port 不提供 `runAs(userId)` 或跳过检查开关。

流程动作被授权读取当前流程及修改业务变量，不代表可以代替任意办理人审批。跨流程查询和命令必须获得对应范围，任务办理仍受身份与节点策略约束。

异步执行必须显式恢复最小必要身份及追踪上下文，并在 finally 清理；不能假定 ThreadLocal 自动传播。不要把整个 HTTP 请求对象传到后台。

### 事务及并发

- 命令以平台应用服务为事务边界，默认 REQUIRED，参与已有事务；内部引擎、本地任务、操作记录和既有状态同步一起成功或失败。
- 能力查询/预览不提供执行保证；操作时重读任务与节点配置，使用已有引擎乐观锁、记录锁和编排约束处理并发。
- 不在公共接口中暴露缺乏统一含义的数据库 revision。若后续需要比较修改，单独设计基于具体业务状态的令牌。
- 流程动作在 IN_TRANSACTION 中抛错按原失败策略回滚。AFTER_COMMIT 的失败不能撤销原审批事务，应使用幂等重试和现有失败处理机制。
- 审批中的动作回调不得通过公共方法再次完成同一个正在办理的任务；实现时加执行中的任务防重入检查，避免递归推进。
- 外部通知继续使用现有提交后/Outbox 路径，不能夹在数据事务中调用后再声称可整体回滚。

### 幂等

接口设计要求对有副作用的命令记录 `调用范围 + requestId + 命令类型 + 规范化载荷摘要` 和结果。同一键同一命令返回原结果，同一键不同载荷报冲突；失败回滚后不留下成功回执。

当前实体变更已有回执，不代表流程命令也已具备相同能力。后续实施先检查现有流程执行记录是否适合复用；不能复用时需要新增持久化迁移。仅用 JVM Map、随机 UUID 或“任务已结束就返回成功”不能实现可靠重试。涉及流程外部副作用时沿用其独立幂等机制。

## 8. FlowActionContext 的便利方法

保留现有调用体验，但向明确类型和作用域收敛：

```java
context.getVariables();               // 触发时变量快照，保持原有语义
context.getVariable("riskLevel");
context.getLiveVariables();           // 新增：显式读取当前运行态变量
context.setProcessVariable("riskLevel", "HIGH");
context.getProcessInstance();         // 目标类型改为 ProcessInstanceView
context.getActiveTasks();             // 新增：全部活动任务
context.getTriggerTask();             // 当前活动触发任务，已完成可为空
```

上下文内部通过公共读取/变量组件执行，修改变量后更新本次上下文快照。`getVariables()` 不悄悄改成每次读数据库，以免重试或提交后动作的判断发生变化。

`getCurrentTask()` 的“第一条”不能作为自动审批目标。新代码使用任务集合或明确 taskId；实现改造时盘点并更新仓库调用方与示例，不新增一套长期保留的同义契约。

现有 FlowActionRuntimePort 的 Object 返回类型升级会影响实现方和外部已编译扩展，需要明确升级版本、重新编译并验证。在精确发布摘要已固定的扩展上，还需要检查制品摘要及历史版本保留要求；不能把签名变更当作无影响内部修改。

实体数据快捷方法保持现状，本设计不顺带调整其权限或返回行为。

## 9. biz 调用示例

业务模块只注入需要的能力。可直接编译的完整示例见 [ProcessBusinessCustomization.java](examples/ProcessBusinessCustomization.java) 和 [ProcessActionCustomization.java](examples/ProcessActionCustomization.java)。

```java
@Service
@RequiredArgsConstructor
public class ProjectWorkflowService {
    private final ProcessQueryPort processQuery;
    private final ProcessVariablePort processVariables;

    /**
     * 写入供后续路由使用的风险等级。
     * requestId 由本次业务意图产生，重试复用；执行身份由宿主绑定。
     */
    public void updateRisk(String instanceId, String requestId, String risk) {
        ProcessInstanceView instance = processQuery.requireInstance(instanceId);
        if (instance.lifecycle() != ProcessLifecycle.RUNNING) {
            throw new IllegalStateException("当前流程不能更新风险等级");
        }
        processVariables.patchProcessVariables(new ProcessVariablePatch(
                new CommandMeta(requestId),
                instanceId,
                Map.of("riskLevel", risk),
                Set.of()));
    }
}
```

写入入口仍需在事务内重新校验生命周期；示例前置查询只是业务说明，不能代替写时校验。

审批示意：

```java
// 调用方明确指定待办，平台负责判断是否加签任务及执行完整办理链路。
TaskCommandResult result = taskCommands.complete(new CompleteTaskCommand(
        new CommandMeta(requestId),
        taskId,
        "approve",
        new TaskSubmission(comment, null, formPatch, formReference, nextApprovers)));
```

## 10. 现有实现的复用与收口

| 现有位置 | 后续处理 |
| --- | --- |
| `FlowActionRuntimeAdapter` | 提取/复用运行态读取组件；公共结果改为独立投影；修正宽泛异常回退和变量作用域 |
| `TaskListQueryService`、`ProcessTaskService` | 复用候选权限、本地加签、待办投影；抽离不含页面/实体全文的读取能力 |
| `ProcessInstanceService`、`ProcessRoundService` | 复用实例、历史轮次、接收任务定位及访问校验；去除公共契约对 HTTP DTO 的依赖 |
| `TaskActionService`、`TaskAddSignService` | 作为任务办理核心实现，在外层建立唯一命令应用服务 |
| `ProcessTaskController`、`TaskActionController` | 将发布表单提交按钮校验、加签分支与下一审批人约束等业务编排下沉 |
| `TaskOperationController` | 将操作能力合并和人工知会等必需校验下沉，查询与执行共用规则 |
| `TaskService`、`TaskServiceImpl` | 旧入口逐步委托到公共应用服务，避免维持第二套办理/重提逻辑 |
| `ProcessRollbackService` | 先统一退回/重提语义，再导出稳定命令 |
| `ProcessTerminationService` | 复用现有终止规则，将 Result 返回适配为统一异常与回执 |
| `ProcessRuntimePort`、`ProcessRuntimeService` | 保留内部启动前提，不直接升级为任意 biz 可用的完整发起入口 |
| `WorkflowReservedVariables` | 扩展为统一的公共变量读写边界，核对全部实际平台保留键 |

已有规则不通过复制代码实现；允许调整其所属层，使平台内部、HTTP 入口、流程动作和 biz 调用最终共享权威路径。

## 11. 实施顺序与验收

1. 固定接口、独立结果模型、错误和执行身份语义；盘点全部旧入口及外部扩展兼容点。
2. 落地查询和变量接口，接入 FlowActionContext，验证本地加签、并行任务及历史作用域。
3. 收口认领、审批、转办以及撤回、终止、接收任务触发，补齐可靠命令回执。
4. 统一高级任务编排：退回、重提、加签、人工知会和下一审批人选择；让旧 HTTP 入口复用。
5. 增加一个仅依赖 port/spi 的 biz 示例及使用说明，确认无需导入 Flowable、流程实现 Service 或 HTTP DTO。

验收重点：

- 运行中/已结束/不存在/无权限实例；候选组、角色、多轮历史范围。
- 并行任务不丢失，本地加签任务可查询和办理，转办历史不因 taskId 相同被覆盖。
- 流程根变量与任务本地变量不串作用域；null、删除、历史回退与技术失败区别明确。
- 保留变量拒写；结束实例拒写；无身份后台调用不自动扩大权限。
- 普通审批、前/后/并行加签、下一审批人选择和发布表单版本约束行为保持一致。
- 双人并发认领/完成、重复请求、相同 requestId 不同载荷、事务中途失败及回执回放。
- 外层事务回滚时引擎、本地待办、业务同步和命令回执一起回滚。
- 退回后重提不创建新实例；撤回后重新发起新一轮维持原有业务边界。
- Port/SPI 架构检查通过，不新增重复公共类型或框架依赖。

数据库迁移新增 `V122__process_command_receipt.sql`，现有迁移无修改、删除或重命名。部署顺序及外部扩展兼容要求见实施记录。
