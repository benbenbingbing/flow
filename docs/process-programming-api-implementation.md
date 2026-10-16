# 流程公共编程能力实施与使用说明

日期：2026-10-16。接口设计见 [process-programming-api-design.md](process-programming-api-design.md)。实现已落地；全量测试存在下文列出的既有基线问题，不能宣称全量测试全绿。

## 入口与共享实现

biz 模块注入 `workflow-port` 的接口；实现流程动作再依赖 `workflow-spi`。无需导入 Flowable、流程实现 Service 或 HTTP 请求 DTO。

| 入口 | 实现 | 共享业务路径 |
| --- | --- | --- |
| `ProcessQueryPort` | `ProcessQueryAdapter` | 实例读取授权、候选用户/组/角色待办、本地加签有效性、发布版本、审批预览和操作能力 |
| `ProcessVariablePort` | `ProcessVariableAdapter` | 根实例/任务本地作用域读取、保留变量策略、身份校验和原子补丁 |
| `ProcessTaskCommandPort` | `ProcessTaskCommandAdapter` | 发布表单按钮、普通/本地加签/源任务分流、原有 `TaskActionService`、加签、知会和同步服务 |
| `ProcessInstanceCommandPort` | `ProcessInstanceCommandAdapter` | 原有撤回、终止和接收任务校验/执行服务 |

原审批、认领、撤回、终止、接收任务、加签、知会以及退回/重提入口已委托公共命令或共享编排。成功响应保留旧页面格式；错误统一使用公共流程错误码并由全局处理器映射 HTTP 状态。原任务引擎办理、节点表单、下一审批人、多实例汇聚、待办同步和实体活动记录继续由既有服务执行。

HTTP 审批仍要求页面提供表单 ID 与摘要，并校验它声明的记录绑定。Java 调用未传 `formReference` 时，由真实任务的节点绑定解析已发布表单，再执行同一 `submitApproval` 按钮规则；多个绑定不能任意取第一项，必须指定表单。已提供的摘要绝不被当前摘要替换，配置变化仍会明确拒绝。字段提交继续经过原节点表单处理。

## 可编译示例

- [ProcessBusinessCustomization.java](examples/ProcessBusinessCustomization.java)：查询全部活动任务、修改业务变量、审批明确任务。
- [ProcessActionCustomization.java](examples/ProcessActionCustomization.java)：通过动作上下文读取实时变量并写入当前实例。

两个示例均已用只有 `workflow-port` / `workflow-spi` 及 JDK 的类路径编译验证。业务模块可将示例作为构造器注入的应用服务；需要与业务数据库变更一起回滚时，在调用方应用服务开启事务。

## 身份、事务与重试

- 人工查询和任务命令使用平台实际执行身份；不能通过参数指定其他操作人。无身份后台线程不能自动获得审批权。
- 平台签发的流程动作范围只允许本实例查询和业务变量操作。退出或抛错时恢复此前范围，不能跨实例使用，也不代替人工审批身份。
- `CommandMeta.requestId` 表示一次业务意图，重试复用原值，新意图使用新值。最大 200 个 Java 字符；同一调用范围下，相同键换载荷会抛 `IDEMPOTENCY_CONFLICT`。
- 回执与引擎变更加入同一事务。失败不会留下成功回执；外层事务回滚时二者一起回滚。返回方法不表示外层事务已提交。
- 成功重试读取持久化结果，任务已经结束也可重放，不再执行字段保存、推进或通知逻辑。回执按真实用户/动作范围隔离。
- 同一任务在同步动作回调中即使换 requestId 也不能递归办理；加签最终收口时源任务也受此保护。不同线程的竞争仍由数据库约束和 Flowable 乐观锁协调。
- 支持请求体的旧 HTTP 命令新增可选 `requestId`。未提供时按一次新操作处理，无法承诺网络重试的同一意图识别。路径式认领等旧接口仍可使用原请求形式；需要明确重试语义的 Java 调用必须使用公共命令键。

## 具体语义与兼容边界

1. **变量作用域**：流程变量只读写根实例；任务变量只读写真实任务本地作用域。本地加签没有等价引擎变量域，明确拒绝。设置 null 与删除不同，返回值及嵌套集合为只读快照；普通变量写入不更新实体字段。
2. **历史回退**：只有确认运行态已结束才回退历史；技术错误不会伪装成历史空结果。内部身份、绑定、审批结果、多实例控制等保留变量不能经公共补丁覆盖。
3. **查询**：按权限和筛选之后计数分页；返回全部活动分支和有效本地加签。源任务等待加签时保留平台等待状态。任务历史保留独立镜像记录，转办不会因 taskId 相同而被覆盖。命令回执与既有日志是不同粒度的操作历史，使用各自独立 ID。
4. **退回**：不使用任意执行位置跳转。目标必须在发布节点策略的白名单中，实际通过 BPMN 驳回连线生成该目标的新待办后才成功；未到达则整笔回滚。异步离开、加签和未汇聚会签不能保证立即到达，应使用普通 reject 并查询后续状态。旧服务忽略目标的行为已明确收紧，不再报告虚假的“已退回指定节点”。
5. **重提**：限定真实发起人持有的退回待办，继续当前实例，仍经过发布表单和下一审批人校验。历史入口的差异统一为 `action=resubmit`、`approved=approve`，并清理退回标记。实例式旧入口出现多个匹配待办时要求明确 taskId。旧页面兼容读取带下划线的历史退回字段及统一办理使用的 `approved/rejectBy/rejectTime/comment`。
6. **加签结果**：子任务完成不代表主流程推进；源任务可返回 `WAITING_ADD_SIGN`，若加签收口后还需确认下一审批人，则返回 `TODO`。取消原因保存在原操作日志中，未传原因的旧调用沿用加签备注。
7. **接收任务**：同时提供 executionId 与 activityId 时二者必须一致。业务变量写入根实例，与触发引擎一起提交或回滚，不写到偶然选中的执行分支。
8. **时间和性能**：旧表 LocalDateTime 按 `workflow.process.storage-zone` 转为 Instant，默认 `Asia/Shanghai`；其他历史存储时区需按实际配置。新回执以 UTC 写入。实例分页按批次扫描并复用授权，避免错误 total，但大数据量下仍需评估查询成本。

`TaskCommandResult` 的状态包含 `TODO`、`COMPLETED`、`TRANSFERRED`、`WAITING_ADD_SIGN`、`ADD_SIGN_COMPLETED`、`ADD_SIGN_CANCELLED`；应结合 `processLifecycle` 判断实例是否结束，不能仅看任务动作名。操作能力查询只是提示，命令执行始终重新授权和校验。

## 流程动作升级

`FlowActionRuntimePort.CONTRACT_VERSION = 2`。实例、任务返回独立投影，`getActiveTasks()` 代替“第一条当前任务”；新增 `getLiveVariables()`。`getVariables()` / `getVariable()` 保持触发快照优先的既有语义；快捷写入成功后同步更新本次上下文快照。

返回签名变化会影响已编译扩展，外部 JAR 必须重新编译、测试并按现有发布流程生成新制品；仓库内两个 devtools 示例已更新。不能继续强转 Flowable 类型，也不能改写旧发布快照或摘要掩盖签名变化。

动作快捷写变量按本次执行内的调用序号生成稳定请求键，适用于固定写入顺序。依赖实时状态的复杂分支应直接调用变量端口并提供业务定义的稳定 requestId，避免重试时写入顺序发生变化。

## 验证结果

- 新增真实事务集成测试 28 项，分别在 H2 和隔离 MySQL 8.0.27 上通过。MySQL 直接执行实际 `V122__process_command_receipt.sql`，验证真实共享锁/唯一竞争、失败回滚和回执重放；测试实例已关闭，不连接现有业务数据库。
- 集成覆盖：根/任务/分支变量隔离，null 与删除，只读快照，外层回滚，回执失败回滚，重复请求、载荷冲突、并发同键请求，多活动任务/加签查询，动作范围恢复，审批、转办、双请求并发完成、同任务防重入、退回落点及原实例重提、接收任务触发与回滚。
- 表单契约测试覆盖：无摘要仍调用发布按钮校验、保留旧摘要错误、正确使用加签子任务身份、缺失/歧义表单不放行。既有候选身份、会签、加签和下一审批人测试继续复跑。测试中的目录、通知、部分镜像组件使用隔离替身，不能把这些用例当成完整生产环境端到端验收。
- 最后一轮流程广泛回归共 1,075 项，0 失败、0 错误、12 跳过（应用模块 593 项、8 跳过）。全量后端测试共 4,880 项，2 失败、1 错误、298 跳过；随后新增的旧页面退回状态兼容测试已包含在最后一轮流程回归中。全量的三项既有失败在 `git archive HEAD` 的干净副本复现：`DatabaseNamingIsolationTest` 的既有命名、`UiInterfaceExtensionServiceWiringTest` 缺少 `CurrentFormRuntimeContext` 测试 Bean、`CrossModulePersistenceBoundaryTest` 的既有持久化依赖。没有放宽这些测试或修改它们的基线；本次引入的持久化类型引用已通过独立表单引用消除。
- 复现流程回归：

  ```sh
  mvn -o -pl workflow-app -am test '-Dtest=*Process*,*Task*,*FlowAction*,*NextApprover*,*NextApproval*,*Architecture*,*Contract*' -Dsurefire.failIfNoSpecifiedTests=false
  ```

- 全量命令：`mvn -o -pl workflow-app -am test -Djava.awt.headless=true`。部分现有集成用例因环境或既有启用条件跳过；不能声称覆盖了全部外部系统。

## 数据库迁移与部署

- 新增：`workflow-server/workflow-db-migrator/src/main/resources/db/migration/V122__process_command_receipt.sql`。
- 修改、删除、重命名已有迁移：无。
- 创建前检查了 SQL 和 Java 迁移，原最大版本为 V121。未执行 `flyway repair`，未改写初始化迁移。
- 应用启用公共命令之前必须执行 V122；升级后页面办理也依赖回执表。此次仅在隔离测试库执行新迁移，未迁移业务环境。
