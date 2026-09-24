# 待办/已办列表候选表复用与查询改造最终方案

- 状态：已实现，完成针对性验证；尚未应用到业务库
- 日期：2026-09-23
- 范围：首页、移动端待办/已办列表及对应统计；保留现有审批授权与流程语义。

## 核查结论

`process_task_candidate_user`、`process_task_candidate_group` **均可复用**，无需新建 `process_task_actor`。

| 证据 | 已确认事实 |
| --- | --- |
| V001 表定义与当前实体/Mapper | 两表各有 5 个字段，分别按任务与用户、任务与组建立唯一索引，无物理外键。 |
| 实施前源码全库检索 | 生产代码仅有实体和 Mapper 定义；未发现业务服务调用这两个 Mapper。文档与清理 SQL 中另有引用。 |
| V081 | 删除了旧 `process_task_instance`，保留了这两张候选表；不能按旧字段名称推断有效关联。 |
| 本地 `.env` 指向的 MySQL `workflow` 库，只读检查 | MySQL 8.0.27，两表结构与 V001 一致，**各 0 行**，无外键；旧任务实例表不存在；最近成功迁移为 V103。 |
| 适用范围 | 上述行数仅证明本地库现状，不代表其他部署环境；上线前逐环境执行同样检查。 |

当前列表的主要问题是先读取全部记录，再逐条读取历史流程、用户和完整业务聚合，最后在内存筛选分页。候选表复用解决身份关系表达，列表摘要和数据库分页解决主要读取开销，两项必须分别落地。

## 决策

### 1. 两张候选表保持单一职责

- 候选用户表只保存直接候选用户，不保存“实际处理过的人”，也不把候选组展开为全部成员。
- 候选组表保存引擎中的候选组/角色标识。`group_code` 保留当前 ID/编码兼容规则，`ROLE_` 前缀只解释为角色；普通组不能冒充同名角色。
- `user_id` 兼容引擎已有用户 ID/用户名；按当前用户的两种身份匹配，不在迁移中擅自重新解释身份。
- 两表的 `task_instance_id varchar(64)` 改名并改型为 **`process_task_id bigint`**，关联 `process_task.id`，Java 字段改为 `Long processTaskId`。不关联已删除的旧表，也不混用引擎 `task_id`。
- 保留关系主键、`sort_order`、`create_time`；`sort_order` 仅供稳定展示，不决定审批先后，会签顺序由流程控制。
- 保留唯一约束 `(process_task_id, user_id/group_code)`，增加反向索引 `(user_id/group_code, process_task_id)`。关联一致性由统一事务维护。
- `process_task.assignee_id` 收敛为当前实际办理人；普通未认领任务为空，候选名单只在候选表保存。历史已办保留真实完成者，候选人不计入已办。

### 2. 主表增加可检索的列表摘要

已有 `process_name`、`node_name`、流程/业务关联标识、时间和 SLA 摘要继续使用。增加以下字段；业务摘要使用 LONGTEXT，避免静默截断：

| 新增字段 | 用途和更新口径 |
| --- | --- |
| `start_user_id` | 保存历史流程的发起人身份，创建/回填时确定；姓名仍通过用户表获取最新显示名称，保留 ID/用户名兼容与原显示规则。 |
| `business_name`、`business_code` | 列表展示与关键词搜索；与业务主记录当前值同步，不改为任务创建时快照。 |
| `business_data_name` | 保留现有 `TaskVO.dataName` 的独立来源和搜索语义；不能在未证明等价时直接用 `business_name` 替代。 |
| `business_current_task_name` | 业务记录当前节点摘要；不同于本任务的 `node_name`，随业务当前节点更新。 |
| `business_status` | 当前业务状态编码；不同于任务 `status` 和审批 `action`，状态名称按实体配置批量解析。 |

业务摘要由轻量查询读取固定列/既有字段映射，禁止调用递归加载子表的 `findById()`。表单 JSON、子表和完整实体数据不复制到列表摘要，也不在列表 SQL 中读取。

名称、编号、当前节点和业务状态沿用**最新值**语义：业务变更成功时，经 contracts 中的专用摘要投影端口，在同一业务事务内只更新对应 `(entity_code, entity_data_id)` 的摘要字段，覆盖待办和历史已办；不改写历史任务的办理人、动作、结束时间。删除/恢复业务记录同步清空/重建摘要，保持原列表的可见内容语义。需要覆盖正常编辑、导入、状态变更、重启流程及内部运行态更新入口。

### 3. 分页和授权规则

- **普通待办**：`process_task` 联查两张候选表和当前有效用户/组/角色成员关系；保留对 `ACT_RU_TASK` 的轻量关联，验证任务存在并以引擎当前 assignee 优先。已有 assignee 时仅本人可见；未认领时才允许候选匹配。
- **加签待办**：保留现有 TODO 用户明细、ACTIVE 父加签、源任务存活以及实体/实例一致性校验，不以候选表代替加签编排。
- **已办**：只根据实际完成者和 `status='done'` 查询；不联查候选表扩展已办归属。转办仍保留现有列表口径，历史动作通过操作日志追溯。
- 用 `EXISTS` 判断候选关系，避免用户同时命中直接候选和多个组时重复返回；列表与 `COUNT` 共用筛选条件。
- 发起人、业务摘要、优先级、日期、关键词均在数据库分页前筛选。保留现有日期口径、关键词匹配字段、大小写与字面匹配语义；不能将 `%`、`_` 等搜索字符无意解释为通配符。
- 待办按 `create_time DESC, id DESC`，已办按 `end_time DESC, id DESC`；使用项目现有数据库分页适配，返回 DTO/API 不变。
- 提交、认领和转办继续执行现有实时权限及并发检查。候选表不能授予详情或操作的独立权限；发现关系投影不一致时使用原授权查询，不放宽权限。

### 4. 统一同步服务

增加一个任务身份投影服务，按引擎 `task_id` 幂等更新本地主表和候选集合；本地 `id` 保持稳定，转办不能因沿用相同引擎任务 ID 而重复插入。候选集合可在事务内差量更新或完整替换，绝不从旧 `assignee_id` 的逗号串重建混合候选。

| 触发点 | 必须同步的行为 |
| --- | --- |
| 创建、人员解析、下一审批人覆盖、空处理人兜底、分配异常人工修复 | 在最终分配确定后写入完整候选和实际办理人，不能只依赖最早的创建回调。 |
| 认领、转办、SLA 自动转办、候选增删或解除认领 | 同事务刷新实际办理人和候选集合；实际办理人优先于仍保留的候选关系。 |
| 完成、驳回、撤回、终止、多实例剩余任务取消、删除实例 | 同步任务结束状态并移除失效候选；保持真实完成者与审计记录。 |
| 加签创建、激活、挂起、取消、完成 | 维护主表和既有加签表；本人匹配仍必须满足加签有效性。 |
| 组/角色成员增减、停用、删除 | 查询当前组织成员关系即可，不批量展开或重写所有候选用户。 |

身份关系更新与引擎变更必须参加同一事务，失败回滚。当前 `TaskCreateListener` 存在吞异常、`syncTasksFromFlowable` 存在“已存在即跳过”的逻辑，实施时必须修正，增加最终分配后的同步与存量修复。同步覆盖真实监听顺序，用集成测试验证；不能依赖“创建一次就再也不变”的假设。

## 实施与上线

1. **扩展迁移**：实际编写前重新检查 SQL/Java 迁移最大版本（本次仓库为 V104）；新增更高版本迁移，完成候选关联字段调整、摘要字段与索引。非空候选表须先识别旧关联并验证映射，不能直接强转或清空；无法确认的环境停止该次切换并保留原数据。同步更新清理脚本和数据库说明。
2. **先接入写入**：保留原列表读取，接入全部任务身份和业务摘要同步。修正调用方对旧 `assignee_id` 拼接值、`assignee_type` 以及候选显示名称的依赖；核实涉及业务变更的锁顺序，按稳定顺序更新摘要行。
3. **分批回填**：活跃普通任务的候选、办理人从 Flowable 当前状态重建；加签从本地编排表核验。历史已办从历史任务/操作记录核验完成者，不伪造历史候选关系。回填每条独立事务读取摘要；在线业务变更按业务记录合并为一次读取和一次批量 UPDATE。批次幂等、可恢复；加锁复读最新状态，避免旧批次覆盖并发认领、结束或业务编辑。
4. **核对并切换**：相同账号/过滤条件比较新旧任务 ID 集合、顺序、总数和 DTO；覆盖混合候选、组角色变更及并发办理。开启新读取后，仅在当前用户可见摘要全部就绪时自动使用新分页查询；旧查询仍可回切，切换失败不回滚业务数据。
5. **配套优化**：统计改数据库聚合（沿用空 duration 仍计入分母的现有口径）；移动端消除 immediate watch 与 onActivated 首次双请求，保留主动刷新和迟到响应隔离。

反向候选索引以外，还需评估主表办理人/状态/删除标记/排序字段联合索引，以及 `(entity_code, entity_data_id)` 摘要更新索引；最终组合以真实查询的执行计划确定。普通 B-tree 不保证加速包含式关键词搜索，不承诺所有过滤条件固定耗时。

## 验收与代价

- 候选、认领、转办、组角色停用、加签和取消状态结果与原规则一致；已办只属于实际完成者。
- 分页总数、重复关系去重、同时间排序、关键词和日期边界正确；业务摘要在成功变更后符合最新值口径。
- 故障注入验证任务/身份投影和业务/摘要分别同事务回滚；重复事件、重跑回填不会重复或覆盖新状态。
- 每页只返回固定摘要列，关联查询次数不随用户全部历史任务数线性增长；不逐条读取完整业务聚合。用本地同账号数据比较查询数、接口耗时及写入延迟，不预先保证具体毫秒数。
- 保留最新业务摘要会造成同一业务记录关联的历史任务一起更新，存在写放大和锁竞争。这是方案的主要代价，必须测量长流程/多轮流程场景；不要将性能压力从列表无度转移到审批事务。
- 整体为**中等规模的后端改造**，前端接口基本不变；上线前的数据回填、事务一致性和权限回归是主要工作量。本方案不将候选表升级为脱离引擎的独立授权系统。

## 依据

- [V001 候选表定义](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V001__business_schema.sql)、[V081 旧表清理](../../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V081__remove_unused_workflow_tables.sql)。
- [当前列表授权 SQL](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/persistence/mapper/ProcessTaskMapper.java)、[任务身份校验](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskIdentityAccessService.java)。
- [任务写入和补偿同步](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/ProcessTaskService.java)、[创建监听器](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/infrastructure/flowable/TaskCreateListener.java)。
- [任务列表组装](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/api/web/ProcessTaskController.java)、[现有过滤口径](../../workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskListFilter.java)、[实体事务执行器](../../workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/application/EntityMutationTransactionExecutor.java)。

对应决策见 [ADR-0008](decisions/0008-reuse-task-candidates-for-inbox.md)。实现和升级步骤见 [实施验收记录](../testing/task-inbox-read-model-2026-09-23.md)。本次新增 V105 Java 迁移；历史迁移修改、删除均为无。数据库写入仅发生于独立临时测试实例，未修改业务库。
