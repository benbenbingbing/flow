# 数据库空库初始化脚本

本目录根目录的七份数据库脚本用于预先创建的空数据库或空 schema，包含平台表、Flowable 引擎表和系统种子数据，不包含 `biz_*` 动态业务表或业务记录。按目标数据库选择文件，并遵守文件头中的字符集、签名密钥和管理员 Bootstrap 要求。`upgrade/mysql` 下的文件则是明确标注用途的 MySQL 存量库手工升级脚本。

本目录七份数据库文件是独立空库导入脚本，不是 Flyway `B` 基线，也不是存量库升级脚本。采用 Flyway 管理的新库按版本执行全部迁移；存量库继续执行尚未应用的迁移，不能重复导入这些空库脚本。V131、V132、V133 的 MySQL 增量脚本及执行限制见下文。

`sys_user` 的认证模式和密码列注释反映当前应用行为。外部账号初始使用 `EXTERNAL_ONLY`，有权限的重置将认证模式切换为 `EXTERNAL_AND_LOCAL_PASSWORD`。现有列可保存新增模式值，本次只更新空库列用途注释。

## 2026-11-18 同步 V144 合并流程发起时机

同时检查 SQL 和 Java 迁移，原最高版本为工作区已有的 V143；本轮新增 [`V144__merge_process_started_into_start_after.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V144__merge_process_started_into_start_after.sql)，将全部旧 `PROCESS_STARTED` 配置迁到 `PROCESS_START_EXECUTED`（发起后），不按处理器用途转到发起前。`PROCESS_COMPLETED` 改称流程正常结束后；当前20项标准时机（流程7、用户任务节点12、连线1），见[发起边界及顺序](../flow-action-timing-extension.md#v144-合并发起时机)。

本轮只转换业务动作绑定，不改 DDL、字段、索引、约束、种子或退役对象。七份空库脚本仅新增 V144 说明，与本轮修改前逐字比较，结构、注释及种子全部保留；最终结构包含已有 V143 的 `entity_field.options_interface_id`，为 **202张表、2,818字段**。七方言表名及字段清单一致，`entity_field` 32列、`process_action` 25列、`process_action_execution` 44列；历史空库清单测试同步核对这一最终字段数。

验证：相关后端 **377项测试通过，43类，0失败/错误/跳过**。V144 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行真实 SQL，检查草稿/发布/禁用/删除配置、动作身份、发布版本、执行方式、失败策略、参数、顺序及依赖保留，原有发起前/后与正常结束配置不误改，历史执行原文、validate 和再次 migrate。真实 Flowable / Spring / MyBatis / H2 验证普通发起只执行一次、发起后晚于关联/待办同步、调用子流程晚于权限投影、立即结束先发起后再正常结束后、首节点失败不触发、发起后及外层失败共同回滚、独立事务隔离、通知提交后消费及旧入队快照继续执行；项目处理器声明与三份实际随包配置一致。前端时机约束2项、页面配置审计、管理端及 Embed 生产构建通过。

迁移清单：本轮新增 **V144__merge_process_started_into_start_after.sql**；已有 V140、V141、V142 SQL 及 **V143__entity_field_options_interface.java** 沿用工作区内容，没有修改、删除或重命名历史 SQL / Java 迁移。未改 V001、建立基线或执行 repair。未连接业务库，七种数据库均未进行实机迁移或全量导入，H2 兼容模式及静态核对不替代实机验证。

## 2026-11-18 同步 V143 实体字段选项接口

创建前同时核对 SQL 和 Java 迁移，当时最高版本为 V142。新增唯一的 [V143__entity_field_options_interface.java](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V143__entity_field_options_interface.java)，仅向 `entity_field` 增加可空 `options_interface_id`：MySQL、OB MySQL、PostgreSQL、人大金仓使用 VARCHAR(64)，Oracle、达梦、OB Oracle 使用 VARCHAR2(64 CHAR)。已有代码表、主键、索引、约束、种子和退役对象不变。

七种脚本逐方言核对一致：**202 张表、2,818 个字段**，`entity_field` 为 32 列。本次变更新增列无非空约束，默认 NULL，七种脚本均有用途注释。H2 在 MySQL、PostgreSQL、Oracle 兼容模式执行增量迁移、验证及再次 migrate，历史字典列及记录保持不变；七种数据库均未实机升级或完整导入。

本次仅新增上述 Java 迁移，未修改、删除或重命名历史迁移，未改 V001，未建全量基线或执行 repair。

## 2026-11-18 同步 V142 合并引擎任务完成时机

同时检查 SQL 和 Java 迁移后，原最高版本为 V141；本轮新增 [`V142__merge_task_completion_into_node_exit.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V142__merge_task_completion_into_node_exit.sql)。旧 `TASK_COMPLETING` 配置归入 `NODE_COMPLETED`（节点离开前），保留本次审批结果已写入、尚未继续路由的边界，不提前到 `TASK_ADVANCING`（审批流转前，表单保存后）。V142时有21个标准时机（流程8、用户任务节点12、连线1），具体边界及顺序见[时机说明](../flow-action-timing-extension.md#v142-合并引擎任务完成时机)。

本轮不改 DDL、约束、索引、种子或退役对象。七份空库脚本仅新增 V142 说明，均为 **202张表、2,817字段**；`process_action` 25列、`process_action_execution` 44列。逐方言核对表名和字段清单一致，并与 Git 原文件比较：剔除 V140～V142 新增的版本说明后全文一致，字段类型、长度、可空性、默认值、用途注释、主键、索引、约束及种子全部保留。

验证：相关后端 **344项测试通过，39类，0失败/错误/跳过**。V142 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行实际 SQL，检查草稿/发布/禁用/删除配置、动作身份、版本、执行方式、失败策略、参数、排序和依赖保留、旧执行原文、validate 及再次 migrate。真实 Flowable / Spring / MyBatis / H2 测试覆盖审批结果读取、路由写入、普通和顺序/并行会签、或签取消分支、原生跳过、加签最后收口、正反例事务回滚及提交后快照。前端时机约束2项、页面配置总审计、管理端和 Embed 生产构建通过；总审计已同步动态分组标题和退役时机断言。

迁移清单：本轮新增 **V142__merge_task_completion_into_node_exit.sql**；已有 **V140__merge_flow_action_end_timings.sql**、**V141__unify_approval_action_timings.sql** 沿用前轮工作区内容；历史 SQL / Java 迁移修改、删除、重命名均为 **无**。未改 V001、建立基线或执行 repair。未连接业务库，七种数据库未执行实机升级或全量导入，H2 兼容模式和静态核对不替代实机验证。

## 2026-11-18 同步 V141 统一审批办理时机

同时检查 SQL 和 Java 迁移后，原最高版本为 V140；本轮仅新增 [`V141__unify_approval_action_timings.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V141__unify_approval_action_timings.sql)。四种加签专用时机归入审批办理前/后，动作 ID、发布版本、排序、启用状态、执行方式、失败策略和依赖保留；历史执行和已入队快照不变。具体边界和当前顺序见[时机说明](../flow-action-timing-extension.md#v141-统一审批时机)。

本轮无 DDL、种子或退役对象变化。七份最终脚本仅新增 V141 说明，仍为 **202张表、2,817字段**；`process_action` 为25列，`process_action_execution` 为44列。已有 V140 保留原有工作区内容；没有修改、删除或重命名主分支历史 SQL / Java 迁移，没有改 V001、建立基线或执行 repair。

V141 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行实际迁移，覆盖四种旧编码、草稿/发布/删除配置、其他配置字段和历史执行保留、validate 及再次 migrate。所选后端322项测试全部通过（37类，0失败/错误/跳过），含统一审批前后、真实嵌套收口及正反例事务验证；前端约束2项及管理端/Embed构建通过。七份脚本除版本说明外与原文件逐字一致，表名、字段、索引、约束、用途注释和种子均保持原状。

未连接业务库，七种数据库未执行实机升级或全量导入。迁移清单：本轮新增 **V141__unify_approval_action_timings.sql**；已有 **V140__merge_flow_action_end_timings.sql** 保持上一轮工作区内容；历史迁移修改、删除或重命名均为 **无**。

## 2026-11-17 同步 V140 合并流程结束时机

已同时检查 SQL 和 Java 迁移，原最高版本 V139；本次仅新增 [`V140__merge_flow_action_end_timings.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V140__merge_flow_action_end_timings.sql)。迁移将两种旧业务后置编码归入统一的撤回后、流程终止后时机，保留动作身份、发布版本、执行方式、失败策略、排序和依赖；执行审计和已入队快照保持原文。详见[触发边界及顺序](../flow-action-timing-extension.md#v140-结束时机合并及触发边界)。

本次不涉及 DDL、系统种子或退役对象变化，七份空库脚本仅追加 V140 说明，最终仍为 **202 张表、2,817 个字段**；受影响的 `process_action` 为25列、`process_action_execution` 为44列。没有修改、删除或重命名已有 SQL / Java 迁移，没有修改 V001、建立基线或执行 repair。

V140 的3项 Flyway 测试在 H2 MySQL / PostgreSQL / Oracle 兼容模式执行真实迁移，检查配置转换、身份/版本/排序/策略/依赖及审计保留、validate 和再次 migrate。七份脚本逐字核对，除 V140 说明外无任何变化。所选后端303项测试全部通过，覆盖结束时序、正反例、回滚、去重、系统通道和旧队列快照；前端约束2项、管理端及 Embed 构建通过。页面总审计有既有动态标题的静态断言失配，详情及复现命令见时机说明。

未连接业务库，七种数据库未进行实机升级或全量导入。迁移清单：新增 **V140__merge_flow_action_end_timings.sql**；修改、删除、重命名历史 SQL / Java 迁移均为 **无**。

## 2026-11-17 同步 V139 合并业务操作前时机

已同时检查 SQL 和 Java 迁移，原最高版本为 V138；本次新增唯一的 [`V139__merge_flow_action_before_timings.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V139__merge_flow_action_before_timings.sql)。迁移仅更新 `process_action.trigger_timing` 的八种旧配置值，不涉及 DDL、索引、约束或种子。七份空库脚本均同步版本说明，仍为 **202 张表、2,817 个字段**；空库没有 `process_action` 绑定种子需要转换，所有结构、字段语义、已有种子及退役对象清理结果保持不变。

| 旧时机 | 合并后的时机 |
| --- | --- |
| `PROCESS_START_VALIDATING` | `PROCESS_STARTING`（发起前） |
| `PROCESS_WITHDRAW_VALIDATING` | `PROCESS_WITHDRAWING`（撤回前） |
| `PROCESS_TERMINATE_VALIDATING` | `PROCESS_TERMINATING`（终止前） |
| `TASK_SUBMIT_VALIDATING` | `TASK_FORM_SAVING`（审批表单保存前） |
| `TASK_TRANSFER_VALIDATING` | `TASK_TRANSFERRING`（转办前） |
| `TASK_ADD_SIGN_VALIDATING` | `TASK_ADD_SIGNING`（加签前） |
| `ADD_SIGN_SOURCE_VALIDATING` | `ADD_SIGN_SOURCE_SUBMITTING`（加签源任务暂存前） |
| `ADD_SIGN_TASK_VALIDATING` | `ADD_SIGN_TASK_COMPLETING`（加签子任务办理前） |

覆盖草稿、已发布、禁用及已删除配置，保留动作 ID、发布版本、排序和依赖。执行审计及历史载荷不改写；旧环境导出的配置包应在升级后的环境重新导出。运行时不保留旧枚举、别名或双重分发。先停止旧版本写入，执行 V139，再启用新版本；多个动作按合并后的组内排序执行，异常阻断并回滚。业务规则见 [流程动作时机说明](../flow-action-timing-extension.md) 和 [绑定表设计](database-design.md#511-process_action-流程动作绑定表)。

验证：H2 MySQL、PostgreSQL、Oracle 兼容模式执行实际 V139 SQL，逐项检查八种转换、草稿和发布配置、禁用/已删除记录、原有规范值、引擎时机、自定义时机、NULL 值及审计原文；Flyway migrate / validate / 再次 migrate 正常。七份脚本全量表名、字段名一致，并检查新增版本说明以外全部 DDL 和种子逐字保持原状。后端时序及事务、前端配置约束和生产构建的结果见时机说明中的验证记录。

本轮所选后端252项测试全部通过（含 V139 的3项），0失败、0错误、0跳过；前端时机约束2项通过，管理端和 Embed 生产构建通过。复现命令见上述时机说明。

未连接或修改业务数据库，七种数据库未执行实机迁移或全量导入；H2 兼容模式和静态核对不替代实机验证。迁移清单：本任务新增 **V139__merge_flow_action_before_timings.sql**；已有 SQL / Java 迁移的修改、删除、重命名均为 **无**。未改 V001，未使用 repair，未建立基线。

## 2026-11-17 同步 V138 流程动作成功依赖

已同时检查 SQL 和 Java 迁移，现有最高版本为 V137，本次新增唯一的 [`V138__flow_action_dependencies.java`](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V138__flow_action_dependencies.java)。七份空库脚本同步到最终 **202 张表、2,817 个字段**，相对 V137 新增四个可空字段和两个索引；不改变系统种子或退役对象清理结果。V137 已有的发起前 `process_instance_id` 可空语义继续保留。

| 表 | 新增字段或索引 | 用途 |
| --- | --- | --- |
| `process_action` | `predecessor_action_id`，64字符，可空、默认 NULL | 同一绑定位置、时机和 AFTER_COMMIT 方式下的成功依赖；发布时转换为该版本的动作 ID。 |
| `process_action_execution` | `predecessor_execution_id`，64字符，可空、默认 NULL | 关联本次触发中的前置执行，防止跨触发消费其他执行结果。 |
| 同上 | `action_snapshot`、`output_json`，大文本，可空、默认 NULL | 固定入队配置及成功业务输出；原始输出不向执行日志返回。 |
| 同上 | `idx_action_exec_predecessor(status, predecessor_execution_id)` | 查找等待前置成功的记录。 |
| 同上 | `idx_action_exec_replay_success(replay_root_id, status)` | 原始失败的成功人工重放也可满足后继依赖；人工确认失败已处理不视为成功。 |

MySQL / OB MySQL 使用 VARCHAR(64)、LONGTEXT，PG / 人大金仓使用 VARCHAR(64)、TEXT，Oracle / 达梦 / OB Oracle 使用 VARCHAR2(64 CHAR)、CLOB。均保留可空性、默认值、用途注释、已有主键和约束；不新增物理外键。Java 迁移按方言增加字段，并通过元数据支持中断后继续执行；旧记录依赖字段默认 NULL，保持独立执行，不回填原始输出。

同次触发整批原子入队，有依赖的记录初始为 WAITING；前置成功状态与业务输出通过同次租约条件更新提交。轮询读到已提交的成功事实后才以 CAS 转为 PENDING，再进入线程池。失败重试保留前置成功及输出，不因排序或重新发布而改变依赖。参数通过 `{ "$predecessor": "contractNo" }` 明确引用输出字段，不执行表达式。业务阶段顺序由平台保证；排序只保证同组事务内同步调用顺序。

验证记录：

- 真实 MyBatis / H2 / Spring 事务测试覆盖整批回滚、未提交成功隔离、重复触发隔离、过期租约拒绝、并发领取唯一成功、仅重试失败后继、发布快照、原始输出与脱敏日志分离，以及成功人工重放放行后继。
- H2 上验证 V138 的 Flyway migrate / validate / 再次 migrate、迁移直接重入、旧记录保留和大文本输出；按七种 vendor 生成的 DDL 分别在 H2 MySQL / PG / Oracle 兼容模式验证可空性和64字符长度。
- 七份最终脚本核对202张表、2,817个字段清单一致，并检查本次四列的方言类型、长度、默认值、可空性、用途注释和两个索引。受影响表分别为25列和44列，设计文档已补齐此前 Java 迁移增加的字段。
- 前端配置往返及真实 Chrome / Vue 组件验证分组、循环候选过滤、前置结果参数保存和重开回显、组内排序不改依赖；生产构建通过。
- 未连接或修改业务数据库；MySQL、PG、Oracle、达梦、人大金仓及两种 OceanBase 均未进行实机升级或全量导入，H2 兼容模式和静态检查不替代实机验证。

验证命令（后端在 `workflow-server`，前端在 `workflow-frontend/workflow-web`）：

```sh
mvn -o -pl workflow-app,workflow-db-migrator -am -Dtest=FlowActionDependenciesTest,FlowActionDependencyExecutionTest,FlowActionDependenciesMigrationTest,FlowActionBeforeStartMigrationTest,FlowActionExecutionServiceTest,FlowActionExecutionProcessorTest,FlowActionEventDispatcherTest,FlowActionCustomFailureTransactionTest,FlowActionFailurePublicationTest,EntityFieldAssignmentPublishTest,FlowActionOperationPolicyTest,FlowActionOperationExecutionTransactionTest,ConfigMigrationFlowActionImportSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
node src/shared/__tests__/flow-action-dependencies.spec.js
node scripts/flow-action-dependencies-browser-test.mjs
npm run build
```

迁移文件清单：本次新增 **V138__flow_action_dependencies.java**；已有 **V137__flow_action_before_start_execution.java** 保持原有工作区内容。修改、删除、重命名主分支已有迁移均为 **无**，未改 V001，未使用 repair，未建立基线。

## 2026-11-17 同步 V137 发起前动作执行审计

新增 [`V137__flow_action_before_start_execution.java`](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V137__flow_action_before_start_execution.java)，仅将 `process_action_execution.process_instance_id` 从非空改为可空，保留64字符长度、已有值、主键、索引及其他约束，不新增字段、表或系统种子。发起前动作尚无引擎实例，执行日志保存真实 NULL，通过发布版本、流程定义、实体编码及载荷中的实体数据 ID 定位；不使用伪造实例 ID。受影响字段和业务规则已同步到 [执行日志表设计](database-design.md#69-process_action_execution-流程动作执行日志表)。

创建迁移前已同时扫描 SQL 和 Java 迁移，原最高版本为 V136。V137 完成后的结构仍为 **202 张表、2,813 个字段**；同工作区随后新增的 V138 增加四个字段，七份最终空库脚本当前为 **202 张表、2,817 个字段**，继续保留 V137 的可空语义。MySQL / OB MySQL、PG / 人大金仓保持 VARCHAR(64)，Oracle / 达梦 / OB Oracle 保持 VARCHAR2(64)，用途注释均说明发起前为空；默认值仍为空值，已有索引及退役对象清理结果保持不变。

验证范围：

- 本轮所选后端259项测试通过，0失败、0错误、0跳过；其中 V137 迁移测试4项。前端时机策略正反例2项通过，管理端及 Embed 生产构建通过，详细阶段用例见 [流程动作时机扩展](../flow-action-timing-extension.md)。
- H2 MySQL 模式执行实际 V137 Java 迁移，覆盖升级前 NULL 插入被拒绝、升级后 NULL 可插入、旧记录及索引保留、64字符长度、Flyway migrate / validate / 再次 migrate，以及迁移直接重入。
- H2 PostgreSQL / Oracle 兼容模式执行对应 DDL；逐一核对七种 vendor 生成语句和七份最终脚本的列类型、长度、可空性、用途注释，并核对全部表名及字段清单一致。
- 真实 Flowable / Spring / MyBatis / H2 测试覆盖无实例的发起前动作审计、前置拒绝、实际业务阶段顺序、表单及引擎回滚、外层回滚不留通知、提交后可领取及失败重试时保留原快照和幂等键；配置保存及运行时均拒绝把前置动作改为异步或忽略失败。
- 未连接或修改业务数据库；MySQL、PostgreSQL、Oracle、达梦、人大金仓及两种 OceanBase 均未进行实机迁移或全量导入。H2 兼容模式及七方言静态核对不替代实机验证。

验证命令（仓库根目录）：

```sh
mvn -o -f workflow-server/pom.xml -pl workflow-app -am clean test '-Dtest=FlowAction*Test,TaskActionServiceTest,TaskAddSignServiceTest,ProcessRuntimeServiceTest,ProcessTerminationServiceTest,ProcessProgrammingVariableIntegrationTest,TaskCandidateApprovalTransactionIntegrationTest,TaskAssignmentAccessFlowableIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
```

本任务迁移清单：新增 **V137__flow_action_before_start_execution.java**；修改、删除、重命名主分支已有 SQL / Java 迁移均为 **无**。同工作区 V138 为后续独立变更，予以保留。未修改 V001，未使用 repair，未创建基线。

## 2026-11-17 同步 V136 流程用户组成员标签

已同时检查 `src/main/resources/db/migration` 的 SQL 迁移和 `src/main/java/db/migration` 的 Java 迁移，原最高版本 V135，本次新增唯一的 [`V136__group_member_tags.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V136__group_member_tags.sql)。七份空库脚本均为 **202 张表、2,813 个字段**，比 V135 增加两张表、六个字段及一条内置人员解析器目录种子。其他表结构和原有系统种子保持不变。

| 表 | 字段 | 约束、索引与用途 |
| --- | --- | --- |
| `sys_group_tag` | `id`、`group_id`、`tag_name`、`create_time` | 主键 id；组内名称唯一；group_id 外键关联 sys_group。名称50字符，ID64字符；时间非空、默认 CURRENT_TIMESTAMP。流程引用标签 ID，改名不会改变条件。 |
| `sys_user_group_tag` | `user_group_id`、`tag_id` | 两列均为64字符、非空，组合主键；分别外键关联成员关系与标签。反向索引 idx_member_tag_lookup(tag_id, user_group_id) 支持筛选和清理。 |

MySQL / OB MySQL、PostgreSQL / 人大金仓的文本类型使用 VARCHAR(n)，Oracle / 达梦 / OB Oracle 使用 VARCHAR2(n CHAR)，均保留长度、非空、默认值、主外键、唯一约束、索引和用途注释的等价语义。Flyway SQL 沿用现有 MySQL 兼容迁移方式；其他数据库的存量升级语法须按目标方言调整。七份脚本是空库最终结构，不用于覆盖存量结构。

内置 `groupMembersByTag` 注册并启用到 `process_person_resolver_definition`，支持 ASSIGNEE / CANDIDATE / MULTI_INSTANCE。目录种子与实现使用同一稳定编码，不依赖用户手工注册。

使用方式：在“流程用户组 → 分配成员”维护组内标签和成员绑定，可批量设置；成员与绑定一起保存，旧成员接口省略标签时保留未离组人员的绑定。流程设计器逐组选择成员标签，留空为该组全部有效成员，多标签按或匹配，多组取并集、去重。失效或跨组标签必须拒绝，不能退回全组。标签只影响审批选人，不改变角色或数据权限。已生成任务保留候选名单；会签在节点进入时读取当前目录、当轮保持稳定。详细字段与关联见 [数据库设计 9.9](database-design.md#99-sys_user_group-用户用户组关联表)。

本次验证：

- 后端所选 **76 项测试通过**：成员与标签真实 MyBatis/H2 查询、事务回滚、非法/跨组标签、改名保持引用、旧接口保留绑定、离组清理、禁用过滤、标签删除后拒绝条件；发布净化、下一审批人预览/候选/提交重验、旧会签集合隔离及已有用户组回归。
- 真实 Flowable 引擎验证普通审批仅写入筛选后的候选用户、没有整组候选权限；会签在节点进入时读取最新标签结果，忽略启动时旧集合，生成对应人员任务。
- H2 MySQL 模式执行实际 V136 SQL，验证唯一键、外键和解析器种子，Flyway migrate / validate / 重复 migrate 正常。迁移后原有成员关系保留。
- 七份脚本全量核对 202 个表名和 2,813 个字段名一致；V136 新增对象的类型、长度、非空、默认值、用途注释、主外键、唯一键和反向索引等价。原有列和种子均保留，仅增加一条解析器目录种子。
- 前端配置往返回显、失效标签保留、清空恢复全组、旧配置和下一审批人契约测试通过；真实 Chrome/Vue 组件验证标签筛选及预览、目录失效保留条件、成员名单不变时原子保存标签、加载失败禁止保存。管理端及 Embed 生产构建通过。
- SQL / Java 迁移最高版本 V136，无重复版本；Git 差异确认历史迁移没有修改、删除或重命名。未修改 V001，未使用 repair，未创建基线。
- 未连接或修改业务数据库；MySQL、PostgreSQL、Oracle、达梦、人大金仓和两种 OceanBase 均未进行实机迁移或全量导入。七方言静态核对及 H2 测试不替代实机验证。

验证命令：

```sh
# workflow-server 目录；clean 防止 IDE 留下的增量编译产物影响测试。
mvn -o -pl workflow-app,workflow-db-migrator -am -Dtest=SysGroupTagIntegrationTest,SysGroupServiceTest,SysGroupControllerTest,SysGroupMapperSqlContractTest,SysUserGroupMapperSqlContractTest,GroupMembersByTagPersonResolverTest,NextApproverNodeAssignmentCandidateTest,NextApproverCandidateScopeSecurityTest,GroupMemberTagsMigrationTest,GroupMemberTagsFlowableIntegrationTest,RelativeOrgPositionCollectionHandlerTest,RelativePositionPublishSanitizerTest,EntityUserReferenceFieldPublishSanitizerTest,NextApproverOverrideServiceTest -Dsurefire.failIfNoSpecifiedTests=false clean test
# workflow-frontend/workflow-web 目录
npm run test:group
node src/shared/__tests__/assignment-config-v2.spec.js
node src/shared/__tests__/next-approver.spec.js
npm run test:group-tags:browser
npm run build
```

迁移文件清单：新增 **V136__group_member_tags.sql**；修改、删除、重命名已有 SQL / Java 迁移均为 **无**。

## 2026-11-16 同步 V135 用户、组织主表文本扩展

已同时检查 SQL 和 Java 迁移，原最高版本为 V134，本次新增唯一的 V135：[`V135__identity_main_table_extensions.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V135__identity_main_table_extensions.sql)。七份空库脚本均为 **200 张表、2,807 个字段**，相对 V134 仅增加用户、组织主表的 10 个字段，不改变其他表、索引、约束或系统种子数据。

| 表 | 新增字段 | 物理语义及用途 |
| --- | --- | --- |
| `sys_user` | `ext1`～`ext5` | 五个可空、默认 NULL、最多 500 字符的独立文本列，用于保存映射后的来源字段，支持主表查询和关联。 |
| `sys_organization` | `ext1`～`ext5` | 同上，具体用途由接入业务约定，组织父级后到不会改写这些列。 |

MySQL / OB MySQL、PostgreSQL / 人大金仓使用 VARCHAR(500)，Oracle / 达梦 / OB Oracle 使用 VARCHAR2(500 CHAR)，均保留可空性、默认 NULL 和用途注释。新增 Flyway SQL 沿用现有 MySQL 兼容迁移写法，由 Flyway 按版本执行一次，其他数据库存量升级语法须按方言调整；七份脚本是空库最终结构，不用于覆盖存量结构。

保存模型新增可选 `mainFields`，省略整组保留现值，提供时整体替换五列，组内 NULL 显式清空。与业务资料共用来源版本、摘要、事务及必需审计；旧调用省略该组时沿用历史摘要算法。普通管理响应携带五列，请求只读，数据通过来源同步维护。旧记录默认 NULL，不从来源原文自动回填。接入示例及规则见 [身份写入设计](../architecture/identity-write-and-extension-design.md#31-主表文本扩展v135)，字段及关联见 [数据库设计 9.1](database-design.md#91-sys_organization-组织部门表) 和 [9.5](database-design.md#95-sys_user-系统用户表)。

本次验证：

- 所选后端 **152 项测试通过**：身份写入、原文授权、来源权限、组织父链、组织选择器、V135 迁移、biz 接入示例及用户/组织管理回归。新增覆盖五列保存、读取及按列筛选、500 字符边界、逐字段超长拒绝、原文空白和前导零、旧调用保留、显式清空、幂等与版本冲突、旧版本忽略、批次及必需审计失败回滚、组织父级后到、普通管理请求只读。
- H2 MySQL 模式执行实际 V135 SQL，验证两张已有表各增加五个可空列、旧行保持 NULL、原列和数据保留、用途注释，以及 Flyway migrate / validate / 重复 migrate；写入后的字段可直接作为 SQL 查询条件。
- 七份初始化脚本逐项核对十个新增列的类型、500 字符长度、可空性、默认 NULL 和用途注释；全量 200 个表名及 2,807 个字段名一致，`sys_user` 为 21 列、`sys_organization` 为 26 列。移除新增列、对应注释及版本头后，脚本与任务前逐字相同，其他 DDL 和所有系统种子数据保持不变。
- 同时扫描 SQL / Java 迁移，最高版本 V135，无重复版本；Git 差异确认主分支已有迁移没有修改、删除或重命名，`git diff --check` 通过。
- 未连接或修改业务数据库。MySQL、PostgreSQL、Oracle、达梦、人大金仓和两种 OceanBase 均未进行实机迁移或全量导入；七种方言完成静态核对，H2 测试不替代实机验证。

验证命令（`workflow-server` 目录）：

```sh
mvn -o -pl workflow-admin,workflow-db-migrator,biz-project -am -Dtest=IdentityWriteIntegrationTest,IdentitySourceSnapshotAccessTest,IdentityAccessServiceTest,IdentityOrganizationHierarchyServiceTest,OrganizationSelectionPaginationTest,IdentityMainTableExtensionsMigrationTest,ProjectIdentityImporterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -pl workflow-app -am -Dtest=OrganizationManagementPaginationTest,MapperWrapperBehaviorTest,SysUserServiceTest,SysUserServiceOrganizationMembershipTest,SysOrganizationServiceImplPositionCompatibilityTest,SysOrganizationControllerAccessPolicyTest,IdentityDirectoryAdapterHandoverTest,PositionAssignmentServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

迁移文件清单：新增 **V135__identity_main_table_extensions.sql**；修改、删除、重命名已有 SQL / Java 迁移均为 **无**。未修改 V001，未执行 repair，未建立基线。

## 2026-11-15 同步 V134 文件业务引用

已同时检查 SQL 迁移和 Java 迁移，当前最高版本为 V134，无重复版本。新增 [`V134__file_references_use_file_id.java`](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V134__file_references_use_file_id.java)，一次性通过参数化 UPDATE 转换历史附件数据，不新增、删除表或字段，也不改变索引、约束和系统种子数据。七份空库脚本均为 **200 张表、2,797 个字段**，空库没有历史附件需转换；版本说明及文件表 `id`、`storage_url`、`storage_key` 的用途注释已同步。

上传响应统一为 `fileId`、`originalName`、`size`，表单中的附件保存为 `{fileId, name, size}`，支持单文件、数组和附件项分组。预览、下载、删除只接受 `fileId`，后端按文件登记 ID 校验所有者/管理员权限，再解析内部存储位置。`storage_url`、`storage_key` 不属于前端文件契约。

### 历史值转换与上线顺序

- 从 `storage_file_object` 读取精确地址到既有文件 ID 的映射，转换实体元数据中 FILE / IMAGE 对应的物理列，保留单文件、数组、分组、原文件名和大小。
- 同时检查流程表单数据列：`process_task.form_data`、`process_task_add_sign.source_form_data`，以及仍存在时的 `process_draft.form_data`、`process_task_draft.form_data`；只转换文件字段，不全文替换普通文本。未发布的物理列自动跳过。
- 不移动存储对象，不重建文件 ID，不修改归属；未登记的历史地址会报告表、列和记录 ID 并中止，须先核实并补齐登记，不能根据路径推断所有者或丢弃附件。事务数据库中此前转换随失败回滚。
- 历史版本、审计与签名发布快照不做全文替换，避免改变冻结原文及摘要；迁移范围是当前业务附件和流程表单值。
- 本次不保留旧地址运行时兼容。上线时先停止旧版本写入，再执行 V134，随后一起启用新后端和前端，避免旧页面再次保存地址格式。

选择 Java 迁移是为了以同一套 JSON 规则处理分组和嵌套值，并通过 JDBC 执行参数化 SQL，避免维护七套不同的 JSON 更新函数。迁移使用数据库元数据和标识符引用处理方言差异，每批最多读取 200 行；不会创建临时过程、函数或基线。

### 本次验证

- 所选后端测试通过，覆盖上传及并发幂等重放的公开字段、文件 ID 到内部存储位置的鉴权解析、预览下载、删除、Embed 参数及附件必填校验。
- V134 的 4 项真实迁移测试通过：H2 MySQL、PostgreSQL、Oracle 模式下转换多组附件、中文名、流程表单和 205 行分页，Flyway validate / 重复 migrate 正常；未登记地址中止并回滚已转换行。H2 兼容模式不替代对应数据库实机验证。
- 前端文件接口、附件展示及移动上传重试测试通过；真实浏览器验证上传、替换、删除、保存刷新、只读展示、鉴权预览下载、原文件名、Embed 令牌和 403 拒绝。管理端、Embed 和移动端生产构建通过。
- 七份初始化脚本的表名和字段清单一致，均为 200 / 2,797；剔除用途注释后，与修改前相比全部 DDL 和种子 INSERT 保持不变。
- 未连接或修改业务数据库；MySQL、PostgreSQL、Oracle、达梦、人大金仓及两种 OceanBase 均未进行实机迁移或全量导入，本次完成静态核对与 H2 迁移验证。

迁移文件清单：新增 **V134__file_references_use_file_id.java**；修改、删除、重命名已有 SQL / Java 迁移均为 **无**。未修改 V001，未执行 repair，未建立基线。

## 2026-11-13 同步 V133 字典项扩展字段

已同时核对 `src/main/resources/db/migration` 的 SQL 和 `src/main/java/db/migration` 的 Java 迁移至 V133，无重复版本号。七份空库初始化脚本均为 **200 张表、2,797 个字段**，相对 V132 只给 `sys_dict_item` 增加 `ext1`～`ext5` 五列，不增加表、索引、约束或系统种子数据。

新增迁移 [`V133__dictionary_item_extensions.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V133__dictionary_item_extensions.sql)，使用与现有 SQL 迁移一致的 MySQL 兼容语法，为 `sys_dict_item` 增加五个可空、默认 NULL 的 VARCHAR(500) 文本字段，不回填旧项。普通 ADD COLUMN 由 Flyway 按版本执行一次，不提供重复手工执行或逐列跳过逻辑；不要将空库初始化脚本与这份存量增量脚本重复应用到同一结构。其他数据库的存量升级语法需按方言调整。七份空库初始化脚本继续保持等价结构：MySQL / OB MySQL、PostgreSQL / 人大金仓使用 VARCHAR(500)，Oracle / 达梦 / OB Oracle 使用 VARCHAR2(500 CHAR)，每个最多保存 500 字符。

这些字段只为定制代码预留文本位置；通用字典提供填写、长度校验和读取，不增加字典类型的字段配置。查询树、字典缓存及配置迁移包携带全部五个值；旧请求和旧配置包未携带或传 NULL 时保留现值，页面用空字符串明确清空（Oracle 兼容库保存为空值）。字段用途、关联及更新规则见 [数据库设计 13.2](database-design.md#132-sys_dict_item-系统字典明细表)。

### 本次验证

- 后端所选 **21 项测试通过**：新增 `DictionaryItemExtensionsTest` 的 8 项、`DictionaryItemExtensionsMigrationTest` 的 9 项，回归既有字典值查询和接口权限的 4 项。H2 MySQL 模式验证实际初始化脚本的字典表定义、MyBatis 保存与查询、500 字符及原文空白保留、缓存和树形 JSON、旧请求保留、空字符串清空、快捷创建、配置快照导入及旧包兼容，并逐个验证五个字段在所有写入入口拒绝超长内容。直接执行实际 SQL 文件，验证首次升级、Flyway validate 及重复 migrate 不重复增列；单独验证手工执行 SQL 后再执行会报重复列错误，已填写内容保持不变。
- 七种方言的新增列逐项核对类型、500 字符长度、可空性、默认 NULL 及用途注释；七份脚本的 200 个表名和 2,797 个字段清单一致。与修改前比较，去除本次五列后的所有表 DDL 及全部种子 INSERT 保持一致。
- 功能实现阶段管理端与嵌入端生产构建已通过；浏览器使用实际 `Dict.vue` 和接口夹具验证了五字段新增提交、编辑回显、清空、重新新增不残留、旧数据 NULL 回显为空，以及 500 字符输入限制。本轮只替换迁移形式并重跑所选后端测试，不重复运行未修改的前端。
- 本轮未连接 MySQL、PostgreSQL、Oracle、达梦、人大金仓、OB MySQL 或 OB Oracle 的实际数据库实例，也未连接或修改业务数据库；七份脚本完成静态结构核对，H2 验证不替代七种数据库的实机验证。

验证命令：在 `workflow-server` 执行 `mvn -o -pl workflow-admin,workflow-db-migrator -am -Dtest=DictionaryItemExtensionsTest,DictionaryItemExtensionsMigrationTest,SysDictItemValuesTest,SysDictControllerAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false clean test`；前端功能验证命令为在 `workflow-frontend/workflow-web` 执行 `npm run build`。

本次迁移文件清单：新增 **V133__dictionary_item_extensions.sql**；移除本任务新建、尚未合并的 **V133__dictionary_item_extensions.java**，由 SQL 替代；修改、删除、重命名主分支历史 SQL / Java 迁移：**无**。干净构建会移除旧 Java 迁移的编译产物，避免同一版本同时发现 SQL 与 Java。未建立基线、未执行 repair。

## 2026-11-12 补充 V131 MySQL 手工升级脚本

新增 [`upgrade/mysql/V131__identity_source_snapshot.sql`](upgrade/mysql/V131__identity_source_snapshot.sql)，可连接目标业务库后直接执行。该脚本与已提交到主分支的 [`V131__identity_source_snapshot.java`](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V131__identity_source_snapshot.java) 的 MySQL DDL 等价，仅增加 `IF NOT EXISTS` 以支持手工重复执行；已有表及原文数据保持不变，不回填历史资料，也不自动修复已有表的结构差异。

原 Java 迁移作为历史版本保留，不替换、不修改。手工 SQL 放在 Flyway 扫描目录之外，不新增或重复注册版本；手工执行后，原 Java 迁移会跳过已存在的表并正常登记 V131，无需手改历史表、建立基线或执行 repair。

此前交付记录中的 V131 执行均为隔离测试，不代表业务环境已经升级。可在目标环境查询 Flyway 历史并检查实际表：

```sql
SELECT version, script, type, success
FROM flyway_schema_history
WHERE version = '131';

SHOW TABLES LIKE 'sys_identity_source_snapshot';
```

`success=1` 表示 Flyway 已登记成功；无版本记录但表已存在时，也可能是通过空库初始化或手工 SQL 创建，需用 `SHOW CREATE TABLE sys_identity_source_snapshot` 核对实际结构。没有 Flyway 历史表的手工管理环境直接检查实际表即可。

在仓库根目录执行（替换主机、用户和数据库名）：

```sh
mysql --default-character-set=utf8mb4 -h db_host -u db_user -p db_name \
  < docs/sql/upgrade/mysql/V131__identity_source_snapshot.sql
```

### 本次检查与验证

- 同时检查 SQL 与 Java 迁移，最高版本仍为 V132，无重复 Flyway 版本。七份空库初始化脚本保持 **200 张表、2,792 个字段**，无需改变已有 DDL 或种子数据；V131 的 9 个字段、类型、长度、可空性、默认值、主键及用途注释已逐方言与历史迁移核对。
- `IdentitySourceSnapshotMigrationTest` 的 11 项及 `EntityFieldAssignmentMigrationTest` 的 9 项测试全部通过。新增覆盖手工 SQL 与历史迁移、MySQL 空库脚本逐字规范化等价，手工执行及重复执行保留原文，手工执行后由原 Java 迁移登记及 Flyway validate / 重复 migrate。
- 独立 MySQL 8.0.27 实例执行实际手工 SQL，通过信息模式及 `SHOW CREATE TABLE` 核对与 `mysql.sql` 的结构、主键、注释、引擎和字符集一致；验证超过 64 KiB 的中文及 emoji JSON、精确大整数、可空来源版本、过期标记、微秒时间、重复执行及主键冲突，原文数据保留。实例已关闭并删除临时库。
- 本轮未连接其他六种数据库的实例，也未连接或修改业务数据库；其他数据库只核对已有空库脚本，不新增手工方言版本。

本轮文件清单：新增手工升级 SQL **docs/sql/upgrade/mysql/V131__identity_source_snapshot.sql**；修改或删除历史迁移：**无**；新增 Flyway 版本：**无**。

## 2026-11-12 同步 V132 内置实体字段赋值动作

已同时检查 SQL 与 Java 迁移至 V132。七份空库脚本仍为 **200 张表、2,792 个字段**（含 Flowable，不含 Flyway 历史表和动态业务表），本次不改变表、字段、索引或约束，仅向 `process_action_definition` 增加一条等价系统种子：`entityFieldAssignmentFlowActionHandler`，中文名称「实体字段赋值」，GLOBAL 范围、默认启用。

新增迁移 [`V132__entity_field_assignment_action.sql`](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V132__entity_field_assignment_action.sql) 仅补缺失目录，不覆盖已有管理员配置或恢复已删除记录。该文件按 MySQL 兼容编写，可直接在存量业务库执行；重复执行不新增重复记录，目录标识被其他动作占用时由主键或唯一约束中止。手动执行后仍可正常运行 Flyway，由 Flyway 跳过已存在的目录并登记版本，无需改历史表或执行 repair。其他数据库的存量升级语法按目标方言自行调整；七份空库脚本的种子等价语义保持不变。

赋值配置复用 `process_action.params_json`，没有新增存储对象。字段、关联及业务规则见 [数据库设计 5.11–5.12](database-design.md#511-process_action-流程动作绑定表)，使用及手动执行方法见 [流程连线实体字段赋值](../process-entity-field-assignment.md#mysql-手动升级)。

### 本次验证

- V132 改为 SQL 后的 9 项迁移测试通过：H2 MySQL 模式执行实际 SQL 的 Flyway migrate、validate、重复 migrate、直接重试及先手动执行再由 Flyway 登记；覆盖管理员自建 ID、名称、可见范围、启停、删除和更新时间保留，以及 ID、动作编码、处理器、混合冲突和处理器名称大小写冲突的拒绝。
- 在 `/tmp` 的独立 MySQL 8.0.27 实例中，使用 `mysql.sql` 的实际目录表 DDL 执行本次 SQL，验证首次插入、重复执行、管理员配置保留、自建 ID 保留、四类目录标识冲突，以及不区分大小写排序规则下的 Spring Bean 名称冲突，测试通过；实例已关闭并删除临时库。
- 七种方言的新种子逐字规范化比对一致；与任务修改前的七份脚本逐项比较，原有 CREATE TABLE、字段、索引、约束及种子均保留，仅各新增一条系统种子与版本说明。
- 功能实现阶段已有 70 项所选后端测试通过，覆盖配置保存与发布复制、精确数字、显式清空、字段保护、停用选项、并行冲突、真实 Flowable 分支、循环及共同回滚，同时回归既有状态、参数和配置导入逻辑；管理端与嵌入端生产构建通过。前端 3 项赋值模型测试、既有失败策略回归及真实组件浏览器测试通过，覆盖快捷入口、多字段保存与重开、布尔/长整数/清空、非法旧配置保护和加载失败重试。本次仅替换迁移形式，重新执行上述 9 项迁移测试，未重复运行其他功能测试与前端构建。
- 本次未连接 PostgreSQL、Oracle、达梦、人大金仓或两种 OceanBase 的实际实例；七份空库脚本完成静态种子语义核对，H2 不替代这些数据库的实机验证。未连接或修改业务数据库。

本次最终迁移文件：新增 **V132__entity_field_assignment_action.sql**；移除本任务新建、尚未合并的 **V132__entity_field_assignment_action.java**，由上述 SQL 替代；修改、删除、重命名主分支历史迁移：**无**。没有执行 repair 或建立基线。

## 2026-11-12 同步 V131 来源身份最新原文

已同时核对 SQL 与 Java 迁移至 V131，七份空库初始化脚本的最终结构为 **200 张表、2,792 个字段**（含 Flowable，不含 Flyway 历史表和动态业务表）。相对 V130 增加一张 9 字段的 `sys_identity_source_snapshot`，没有新增种子记录。

新增迁移为 [`V131__identity_source_snapshot.java`](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V131__identity_source_snapshot.java)，未修改历史迁移。该表以 `source_key` 为主键，每个来源用户/组织只保留一份原始 JSON，覆盖更新并独立判重。业务更新缺原文时保留旧正文并标为过期；原文写入、来源绑定及必需审计共用事务。

七种数据库均保留相同的 9 个字段、主键、可空性、默认值和用途注释；原文分别使用 MySQL/OB MySQL 的 LONGTEXT、PostgreSQL/人大金仓的 TEXT、Oracle/达梦/OB Oracle 的 CLOB。关联来源绑定采用逻辑关联，无新增外键或二级索引。详细字段及业务规则见 [数据库设计 9.16](database-design.md#916-sys_identity_source_snapshot-来源身份最新原文快照表)，配置与调用示例见 [原文留存方案](../architecture/identity-source-original-data-design.md)。

### 本次验证

- H2 MySQL 模式直接执行实际 V131 的 Flyway migrate/validate/重复 migrate 和迁移重试测试通过，验证 9 字段、可空性、用途注释及无历史回填；七种空库脚本逐项与实际迁移 DDL 比对通过。
- 所选后端回归共 122 项通过，涵盖身份写入、原文读取授权、业务接入示例、真实 YAML 配置/环境变量绑定及架构边界。包含原样保留、重复写入、原文独立更新、旧版本、SERIAL 过期、单条/批次 UTF-8 超限、JSON 严格校验、默认角色/原文/审计及批次事务回滚、父级后到。
- 管理端及嵌入端生产构建通过；原文查看组件的真实浏览器测试通过，覆盖按需加载、文本安全、大整数、未留存/过期、重试、目标切换和权限收回。
- 七种方言的 200 个表名、2,792 个字段清单一致；逐项检查本次新增字段的类型、长度、可空性、默认值、主键和用途注释，系统种子 INSERT 与 HEAD 保持一致。
- 在 `/tmp` 的独立 MySQL 8.0.27 实例中完整导入 `mysql.sql` 和 `obmysql.sql`，在独立 PostgreSQL 15.12 实例中完整导入 `pg.sql`。实查均为 200 表、2,792 字段；原文探针覆盖 20,000 个汉字、大整数、可空版本、默认当前状态、更新及事务回滚，实例已关闭。
- Oracle、达梦、人大金仓、两种 OceanBase 未连接实际实例，完成静态方言及迁移语义检查；在 MySQL 中执行 OB MySQL 脚本不替代 OceanBase 实例验证。未连接或修改业务数据库。

## 2026-11-12 同步 V130 当前职务关系

已同时核对 SQL 与 Java 迁移至 V130，七份空库初始化脚本的最终结构为 **199 张表、2,783 个字段**（含 Flowable，不含 Flyway 历史表和动态业务表）。相对 V129 删除一张 6 字段的批次回执表，以及人员关系表的 7 个字段，共减少 1 张表、13 个字段。

| 对象 | 最终结构和用途 |
| --- | --- |
| `sys_position` | 保留职务字典、稳定编码、启停和单人/多人规则。 |
| `sys_position_assignment` | 保留 `id`、`position_id`、`organization_unit_id`、`user_id`、`revision`、`created_by`、`updated_by`、`create_time`、`update_time` 共 9 字段，只保存当前关系。 |
| 退役字段 | 删除 `is_primary`、`sort_order`、`effective_from`、`effective_to`、`revoked_at`、`revoked_by`、`revoke_reason` 及其区间索引和约束。 |
| `sys_position_assignment_batch` | 删除整张表及批次、幂等回执代码；集合替换可重复提交，不再维护独立任命履历。 |
| 当前关系约束 | 三元组唯一；保留主键、三个外键和 `revision >= 1`，增加按用户、组织查询的两个索引。MySQL 系 datetime(6)，其他方言 timestamp(6)，字符串及数值类型沿用方言约定。 |

升级迁移只复制切换时刻已开始、未结束、未撤销的关系，保留 ID 与维护信息。历史及未来预约均不进入当前表；单人职务存在多名当前人员时中止迁移，禁止猜测负责人。先成功复制再替换旧表，通过暂存表和元数据检测支持非事务 DDL 中断后的继续执行。负责人兼容字段随当前关系保存同步，删除预约生效定时任务。

当前关系保存采用锁定读和集合并发比较，避免 MySQL REPEATABLE_READ 旧快照覆盖其他管理员的设置。旧 BPMN `PRIMARY_OR_ERROR` 兼容读取为 `ERROR`；多人时明确报错，新配置只提供唯一人员或全部人员。已生成任务不自动改派。

### 本次验证

- 七种方言的 199 个表名、2,783 个字段清单一致，逐项检查当前关系字段类型、长度、可空性、默认值、用途注释、主键、唯一键、外键和索引；系统种子 INSERT 与任务开始时逐字一致，仍为 103 条菜单/权限及对应超级管理员授权。
- 在 `/tmp` 创建独立 MySQL 8.0.27 实例，`mysql.sql` 和 `obmysql.sql` 均完整导入，实查两库均为 199 表、2,783 字段，关系表 9 字段及主键、唯一键、三个外键、版本 CHECK 均存在。OceanBase MySQL 脚本按文件头要求在同一会话生成临时签名密钥，外键仍在被引用表建成后添加；此结果不替代 OceanBase 实例验证。
- V130 的 5 项测试通过：H2 验证当前/历史/未来/撤销数据边界、单人冲突、中断后继续及 Flyway migrate/validate/重复 migrate；隔离 MySQL 验证实际复制、重复执行、排序规则兼容、唯一键和外键。
- 后端所选回归共 1,255 项通过：系统管理 340、流程模块 575、应用集成 335、迁移 5。包含当前集合替换、并发冲突、重复保存、归属校验、旧主职策略多人报错及实际 MySQL 锁定读绕过旧快照的验证。全部应用依赖和测试代码完成干净编译。
- 管理端生产构建及审批配置契约测试通过，`git diff --check` 通过。
- 额外整类试跑中，`MySqlIdentifierConversionDatabaseTest` 的两项 Embed 用例因既有测试夹具缺少 `entity_form_published` 未通过；本次涉及的两项目录版本用例已单独复跑通过，没有修改 Embed 逻辑。
- 未连接 PostgreSQL、Oracle、达梦、人大金仓及两种 OceanBase 的实际实例；这些产品完成静态方言核对。未连接或修改业务数据库。

本次新增迁移：`src/main/java/db/migration/V130__simplify_position_membership.java`；修改、删除或重命名历史迁移：**无**。不执行 repair 或 baseline。字段、关联和业务规则已同步 [数据库设计](database-design.md#93-sys_position_assignment-当前组织职务人员关系表)。

## 2026-11-11 空库迁移兼容性复验

当前最终结构仍为 V129、200 张表、2,796 个字段。本次未增加或修改持久化结构及系统种子数据，七份初始化 SQL 保持不变；已重新核对七种方言的表名和字段清单一致。

MySQL 8.0.27 从空库执行历史迁移时，V062 的临时表重复引用及排序规则差异会阻断启动。应用启动入口和独立迁移工具现共用 `MySqlV062CompatibilityCallback`：临时菜单 ID 表沿用 `sys_menu.id` 的字符集/排序规则，授权清理改用单次临时表 JOIN。等价语句成功后继续执行 V062 其余 SQL，原迁移文件和校验和均保留；没有执行 repair、baseline 或跳过迁移版本。

隔离 MySQL 实测已完整执行 V001–V129，并验证盘点根/子菜单和授权被删除、显式全量权限及普通菜单授权保留，之后 `validate` 和重复 `migrate` 通过。其他六种数据库未执行完整迁移，此回调只在 MySQL 激活；详细业务回归见 [测试记录](../testing/wfpack-e2e-regression-2026-11-11.md)。

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
| `sys_user.authentication_mode` | `varchar(32)`，非空，默认 `LOCAL_PASSWORD`；外部账号初始使用 `EXTERNAL_ONLY`。 |
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
