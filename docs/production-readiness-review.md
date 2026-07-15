# 流程系统生产级代码审查

审查范围：流程配置、发布、实体绑定、流程发起、任务审批、状态映射、流程历史。

## P0

1. 服务端只校验登录，流程和实体配置、发布、删除、绑定接口缺少 RBAC。
   - `workflow-server/src/main/java/com/workflow/config/AuthInterceptor.java:38`
   - `workflow-server/src/main/java/com/workflow/controller/ProcessDefinitionController.java:104`
   - `workflow-server/src/main/java/com/workflow/controller/EntityDefinitionController.java:51`
2. 任务分配给其他用户时仅告警，随后会重新设置审批人，任意登录用户可接管他人任务。
   - `workflow-server/src/main/java/com/workflow/service/TaskActionService.java:69`
3. 遗留 `/api/task` 使用固定 `admin` 身份，绕过当前用户授权。
   - `workflow-server/src/main/java/com/workflow/service/impl/TaskServiceImpl.java:47`
   - `workflow-server/src/main/java/com/workflow/controller/TaskController.java:61`
4. 动态实体详情、更新、删除缺少行级权限，且更新入口允许修改状态、流程实例、提交人等系统字段。
   - `workflow-server/src/main/java/com/workflow/controller/EntityDataController.java:95`
   - `workflow-server/src/main/java/com/workflow/service/EntityDataDynamicService.java:276`
5. Flyway 基线使用 `created_at/updated_at`，运行时部分实体和 SQL 使用 `create_time/update_time`，干净部署存在未知列风险。
   - `workflow-server/src/main/resources/db/migration/V001__business_schema.sql:561`
   - `workflow-server/src/main/java/com/workflow/entity/ProcessDefinitionConfig.java:79`
   - `workflow-server/src/main/java/com/workflow/mapper/ProcessTaskMapper.java:38`

## P1

1. 流程进度、详情、XML、历史和任务详情仅凭 ID 查询，存在实例级 IDOR。
2. 终止流程、删除待办、写日志和更新动态实体状态不是可靠的原子操作。
3. 并发提交 `startProcess=true` 可能为同一业务数据启动两个流程实例。
4. 旧流程实例按 `processKey` 读取最新发布快照，发布新版本后可能展示错误表单。
5. 重新绑定流程会修改既有发布历史，破坏历史不可变性。
6. 发布管线吞掉节点表单和审批配置同步异常，可能出现部署成功但本地配置缺失。
7. 实体 DDL 部分失败后仍可能被标记为发布成功。
8. 会签“一人驳回”只设置变量，没有真正终止其他任务实例。
9. Flowable 任务创建成功但本地待办写入失败时只记录日志，任务可能永久不可见。

详细位置：

- `workflow-server/src/main/java/com/workflow/controller/ProcessInstanceController.java:35`
- `workflow-server/src/main/java/com/workflow/process/runtime/ProcessTerminationService.java:42`
- `workflow-server/src/main/java/com/workflow/service/EntityDataDynamicService.java:336`
- `workflow-server/src/main/java/com/workflow/process/publish/ProcessPublishedSnapshotService.java:27`
- `workflow-server/src/main/java/com/workflow/service/EntityDefinitionService.java:545`
- `workflow-server/src/main/java/com/workflow/process/definition/ProcessDefinitionNodeSyncService.java:73`
- `workflow-server/src/main/java/com/workflow/service/DynamicTableService.java:144`
- `workflow-server/src/main/java/com/workflow/service/TaskActionService.java:262`
- `workflow-server/src/main/java/com/workflow/listener/TaskCreateListener.java:34`

## P2

1. 流程详情历史默认把未知动作标为“通过”，并可能按同名节点错误合并循环历史。
   - `workflow-server/src/main/java/com/workflow/process/runtime/ProcessDetailRuntimeService.java:180`
2. 测试集中于 happy path，缺少越权、IDOR、并发双启动、版本快照隔离、DDL 失败和真实迁移负向测试。
   - `workflow-server/src/test/java/com/workflow/service/TaskActionServiceTest.java:89`
   - `workflow-server/src/test/java/com/workflow/db/SchemaRequiredTablesTest.java:17`

## 上线优先级

1. 先修任务审批越权、遗留 `/api/task`、动态实体 IDOR。
2. 建立服务端 RBAC 和统一流程实例访问策略。
3. 修复 Flyway 兼容迁移并在全新 MySQL 上执行真实启动测试。
4. 增加流程启动幂等、发布一致性和本地待办对账。
5. 补齐安全、并发、版本和故障注入测试后再进入生产发布。
