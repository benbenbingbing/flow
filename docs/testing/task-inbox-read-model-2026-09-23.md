# 待办/已办列表改造实施记录

日期：2026-09-23，最终验证至 2026-09-24。范围：首页和移动端共用的 `/api/process-task/todo`、`/api/process-task/done`。

## 已实现

- 复用 `process_task_candidate_user` / `process_task_candidate_group`，用 `process_task_id BIGINT` 关联平台主键；保留直接用户与组/角色标识，不展开成员授权。
- 普通未认领任务的 `assignee_id` 为空，认领后保存实际办理人；当前引擎办理人优先，加签仍校验用户明细、父加签及源任务，已办只归实际完成者。
- `process_task` 增加发起人、业务名称/编号/自定义名称/当前节点/状态、两个就绪标记。业务摘要使用 LONGTEXT，`assignee_name` 同时扩为 LONGTEXT，避免混合候选展示超过旧 64 字符限制。
- Flowable 任务与身份事件在原 Spring 事务提交前合并同步，失败回滚；覆盖候选增删、认领/解除认领、转办、完成和取消。逻辑删除后的任务仍清理候选，转办复用原平台主键。
- 实体主记录和关系子记录写入登记摘要刷新，同一记录每个事务只读取一次摘要、执行一次批量 UPDATE，历史已办也保持业务最新值。普通 `updateById` 禁止回写这些投影字段，避免旧状态对象覆盖新摘要。
- 列表先按全部权限及筛选条件选择本页主键，再读取摘要和展示姓名；不读取表单 JSON、不逐条加载历史流程及业务聚合。无发起人筛选的 COUNT 不关联用户目录，已办 COUNT 不关联运行任务。
- 关键词保留 7 个来源字段，转义 `%`、`_`、`!`；MySQL 在统一大小写后按字节匹配，避免默认排序规则将 `cafe` 误匹配为 `café`。日期仍按原任务开始日期筛选，同时间以平台 ID 倒序稳定分页。
- 移动端 immediate watch 与 KeepAlive 激活共用初始化请求；主动刷新仍开启新代次，办理失效后旧响应不能恢复旧列表。

## 迁移与部署

本次唯一新增迁移：

- [V105__task_inbox_read_model.java](../../workflow-server/workflow-db-migrator/src/main/java/db/migration/V105__task_inbox_read_model.java)

未修改、删除或重命名主分支已有迁移，未修改 V001；SQL/Java 迁移最大版本为 105，无重复版本。本次未在 `workflow` 业务库执行迁移或回填，测试写入仅发生于独立临时 MySQL 实例。

升级按以下顺序进行：

1. 核对两张旧候选表的行数与关联。V105 对两表全部预检后才执行 DDL；任一表有旧关系时中止，保留数据，不猜测旧 ID 对应关系。
2. 在维护窗口完成 schema 与全部流程写入节点的升级，再启用新读取。不能让旧节点继续修改已标记就绪的任务；仅依赖引擎 assignee 校验不能发现旧节点对候选集合或业务摘要的修改。
3. 分批回填完成前，某用户可见范围只要还有未就绪摘要，该用户的列表整体沿用旧查询；身份未就绪的候选判断仍使用引擎关系。每轮最多 100 条，每条独立事务，失败保留标记以便后续轮次重试。
4. 若采用混合版本滚动部署，先关闭新读取和回填。全部旧写入节点停止后，在维护窗口将 `process_task` 中未删除行的两个就绪标记重置为 0，再启用回填与读取，避免混合版本期间产生的旧摘要被视为有效。此步骤属于部署数据操作，本次未执行。
5. 就绪后核对具体账号的新旧筛选结果及线上延迟。回切只需关闭新读取，不回退迁移、不删除投影数据。

可配置项位于 [config/process.yml](../../workflow-server/workflow-app/src/main/resources/config/process.yml)：

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `TASK_INBOX_READ_MODEL_ENABLED` | `true` | 开启就绪范围的新查询；`false` 回切旧查询 |
| `TASK_INBOX_BACKFILL_ENABLED` | `true` | 控制历史补齐，不关闭在线事务同步 |
| `TASK_INBOX_BACKFILL_INITIAL_DELAY_MS` | `5000` | 启动后首次回填延迟 |
| `TASK_INBOX_BACKFILL_DELAY_MS` | `10000` | 每轮回填间隔 |

升级后可只读检查剩余量：

```sql
SELECT COUNT(*) AS unready
FROM process_task
WHERE deleted = 0
  AND (COALESCE(inbox_summary_ready, 0) = 0
    OR COALESCE(inbox_identity_ready, 0) = 0);
```

V105 的 DDL 检查支持结构步骤重入，但 Flyway 失败历史记录仍须按运维规范处理；不能把直接重跑 Java 迁移测试当成 Flyway 自动恢复保证，本次未执行 `flyway repair`。

## 验证结果

- 收件箱集成测试：[TaskInboxDatabaseTest](../../workflow-server/workflow-app/src/test/java/com/workflow/process/task/TaskInboxDatabaseTest.java)，真实 Spring + Flowable + MyBatis 共享事务。MySQL 与 H2 各 12 项通过，覆盖混合候选及去重、组/角色停用、认领、候选替换、完成/取消、逻辑删除清理、加签来源、转办主键复用、并发回填、业务摘要回滚、旧状态对象防覆盖、长文本、关键词/日期、稳定分页和回切开关。
- V105 MySQL 迁移测试 4 项通过：两类非空旧关系在 DDL 前阻止、部分 DDL 重入及唯一约束、Flyway 从前置结构夹具执行 V105 后校验通过。
- 相关审批、身份、加签、列表过滤、实体写入/重启、统计及数据库边界回归 108 项通过。
- 移动端 13 项测试通过，包含实际 `Inbox.vue` 在 KeepAlive 首次挂载只发一次列表请求；移动端生产构建通过。

隔离 MySQL 8.0.27 的样本：同一用户 2,000 条已办，分页 10 条，7 次读取的中位数 **9.35 ms**，每次 **3 条 Mapper 查询**；同一业务记录的 2,000 条任务摘要批量更新约 **26.59 ms**。这些是 QueryService 与事务测试结果，包含本地连接开销，不是线上 HTTP 性能承诺，也不能与此前业务库 2 秒以上的请求直接做同比。

该样本全部记录都属于同一用户，主键子查询选择状态索引并排序，外层按主键读取本页行；包含式关键词仍可能扫描用户范围内的记录。最新摘要同步仍会锁定和更新全部关联任务行，极长历史及并发审批需继续观察线上写入延迟。

基础复验命令（H2，无需业务数据库）：

```sh
mvn -q -f workflow-server/pom.xml -pl workflow-app -am test -Dtest=TaskInboxDatabaseTest -Dsurefire.failIfNoSpecifiedTests=false
node --test workflow-mobile/tests/*.test.mjs
npm --prefix workflow-mobile run build
```

MySQL 复验需显式提供 `FLOW_INBOX_MYSQL_TEST=true`、`FLOW_INBOX_MYSQL_ADMIN_URL`（仅允许 localhost/127.0.0.1 的无库名 JDBC URL）、`FLOW_INBOX_MYSQL_USER`、`FLOW_INBOX_MYSQL_PASSWORD`。测试自动创建并删除随机前缀测试库，不读取项目业务库配置；迁移测试类为 `TaskInboxMigrationTest`。

## 已知验证限制

尝试从空库执行完整历史迁移链时，既有 `V062__remove_entity_list_scope_inventory.sql` 在 MySQL 8.0.27 报 `Can't reopen table: 'own_root'`，发生在 V105 之前。没有修改该历史迁移。本次验证通过的是 V105 前置结构升级及运行时行为，不能宣称整个仓库的空库安装已通过。

完整历史链测试保留为显式启用项：同时设置 `FLOW_INBOX_MYSQL_TEST=true` 与 `FLOW_INBOX_FULL_MIGRATION_TEST=true`。普通 V105 测试跳过该历史链用例；目标库必须是测试自动创建的独立库。Oracle、DM、Kingbase、OceanBase 等真实实例未复验，本次真实数据库验证范围为 MySQL。
