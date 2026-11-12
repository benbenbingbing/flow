# 流程连线实体字段赋值

在流程设计器选中顺序流，进入「流程动作」，点击「字段赋值」。选择当前实体的字段，逐项设置「固定值」或「清空」，保存动作后发布流程。也可以在「添加动作」中选择内置「实体字段赋值」处理器。

## 配置范围

- 第一版仅支持当前流程关联实体的已发布普通字段：STRING、TEXT、INTEGER、LONG、DECIMAL、BOOLEAN、DATE、DATETIME、SELECT、RADIO。一次动作可配置 1–50 个不同字段。
- 系统字段（含名称、编号、状态、生命周期、任务摘要和审计字段）不开放赋值；业务状态仍使用连线原有「实体状态」配置。关联、人员、部门、附件、富文本、多值和子表字段由各自业务动作维护。
- 固定值不执行表达式。`${变量名}` 写在文本固定值中会原样保存；没有动态变量、当前时间或当前操作人取值功能。
- 「清空」是显式写入 NULL，与空字符串和未配置不同。必填字段不能清空；文本固定值可以是空字符串，必填文本仍不能留空。
- INTEGER 保存为 JSON 整数；LONG、DECIMAL 保存为十进制字符串，避免浏览器浮点数损失精度。日期使用 `yyyy-MM-dd`，日期时间使用 `yyyy-MM-dd HH:mm:ss`；没有时区转换。
- 单选保存实际 value，不能写入不存在或停用的选项；字典字段读取当前可用字典项。字段长度、小数精度及已发布字段验证规则在保存和运行时校验。

## 执行语义

处理器稳定编码为 `entityFieldAssignmentFlowActionHandler`，只支持 `SEQUENCE_FLOW + TRANSITION_TAKEN + IN_TRANSACTION + ROLLBACK`。服务端在保存、发布与执行时重新检查，不能通过接口改成提交后执行或失败继续。字段候选接口 `GET /api/process-actions/process/{processConfigId}/assignable-fields` 沿用流程设计管理权限，仅返回发布字段投影，不读取业务记录。

动作沿用 `process_action.params_json` 保存配置，随流程发布复制到精确版本。修改草稿或重新发布不改变旧实例的动作参数；运行时仍按实体当前发布结构检查字段，字段已退役、类型或选项不再兼容时明确失败。

```json
{
  "assignments": [
    { "fieldCode": "effective", "operation": "SET", "value": true },
    { "fieldCode": "review_result", "operation": "SET", "value": "PASSED" },
    { "fieldCode": "rejection_reason", "operation": "CLEAR" }
  ]
}
```

每次触发通过 `EntityMutationPort` 提交一次多字段补丁，仅更新列出的字段，沿用统一写入的校验、幂等回执、审计和版本策略。业务意图为 `PROCESS_FIELD_ASSIGNMENT`，来源为 `FLOW_ACTION`。执行记录展示目标实体、记录 ID 和字段清单；值的变更沿用实体审计。动作幂等键派生实体变更键，同一次重放保持不变，循环再次经过连线取得新键。

连线业务状态监听先于流程动作，二者共用流程事务：赋值失败时，任务完成、状态写入及字段写入一起回滚。正常结束、跳转或终止若没有实际经过这条连线，就不会执行该动作。

发布时对启用的内置赋值动作检查并行分支：同一字段写不同值（包含清空与非空值）时拒绝发布；相同值、不同字段、排他分支和同步汇合后的顺序赋值允许。一个连线上的多个动作仍按现有动作顺序执行。此静态检查不分析自定义处理器或远程接口的业务副作用。

本动作只更新实体字段，不同步流程变量；后续网关若要按新值路由，需通过已有流程变量写入能力明确准备变量。当前连线已在动作触发前选中，不能用这次赋值改变本次选路。

## 升级与验证

新增 SQL 迁移 [`V132__entity_field_assignment_action.sql`](../workflow-server/workflow-db-migrator/src/main/resources/db/migration/V132__entity_field_assignment_action.sql)，按 MySQL 兼容编写，可直接执行，也可通过 Flyway 执行。仅在动作目录缺失时补入一条全局可见、默认启用的内置定义，不自动绑定任何流程，不覆盖管理员维护的 ID、名称、范围、启停或逻辑删除设置。没有新增表、字段、索引或约束。七种空库脚本保留等价系统种子；其他数据库的存量升级语法本次不作兼容承诺，需按目标方言调整。

### MySQL 手动升级

连接目标业务库，使用 UTF-8 / utf8mb4 字符集，直接执行上述 SQL 文件的全部内容；也可以在仓库根目录执行以下命令（替换主机、用户和数据库名）：

```sh
mysql --default-character-set=utf8mb4 -h db_host -u db_user -p db_name \
  < workflow-server/workflow-db-migrator/src/main/resources/db/migration/V132__entity_field_assignment_action.sql
```

该文件是存量库增量脚本，需要已有 `process_action_definition` 表及其主键、动作编码和处理器唯一索引。不要将 `docs/sql/mysql.sql` 空库初始化脚本导入存量库。

执行后可查询目录配置：

```sql
SELECT id, action_code, display_name, handler_name, visibility_scope, enabled, deleted
FROM process_action_definition
WHERE handler_name = 'entityFieldAssignmentFlowActionHandler';
```

可重复执行；已有相同动作编码及处理器时不覆盖配置，影响行数为 0 是正常情况。稳定 ID、动作编码或处理器被其他不一致的动作占用时，由主键或唯一索引报错，需要先核对冲突记录。

手动执行只写动作目录，不登记 Flyway 历史。后续正常运行 Flyway 时，V132 会跳过已有目录并登记版本；不需要手工修改历史表或执行 repair。

### 验证入口

- 后端配置及发布：`EntityFieldAssignmentFlowActionHandlerTest`、`EntityFieldAssignmentPublishTest`。
- 真实引擎事务：`EntityFieldAssignmentFlowableTest`，覆盖分支、循环、发布版本及与状态写入共同回滚。
- 迁移及七方言种子：`EntityFieldAssignmentMigrationTest`，使用隔离 H2 MySQL 模式测试实际 SQL，覆盖手动执行后再由 Flyway 迁移、重复执行、管理员自建 ID 及目录冲突，不连接业务库。
- 前端模型：在 `workflow-frontend/workflow-web` 执行 `npm run test:field-assignment`。
- 真实组件浏览器：同目录执行 `npm run test:field-assignment:browser`，使用本地接口桩，无业务库写入。

本次没有部署，也没有在业务数据库执行迁移。实际验证结果及数据库覆盖范围见 [数据库脚本说明](sql/README.md)。
