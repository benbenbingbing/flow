# OceanBase Oracle 兼容性实测（2026-10-13）

本轮只验证用户提供的 OceanBase 4.4.2.2 Oracle 模式，租户 `zdw`、Schema `ZDW`，使用 OceanBase Connector/J 2.4.19。数据库密码、登录令牌及临时管理员密码没有写入仓库。测试独立启动后端，业务端口 `18084`、管理端口 `19094`，关闭 Flyway、定时任务和 Flowable 异步执行器。

## 结论与边界

发现并修复了初始化脚本、Schema 元数据、保留字、生成键、CLOB 流、空参数、保存点及数字文本转换的真实不兼容。下表记录已实际执行的范围；BaseMapper 扫描不等于每个自定义方法及每种业务输入都已执行。

远端链路仍存在必须处理的问题：一个成功取得 `DBMS_LOCK` 的独立连接空闲 70 秒后被断开，另一连接可以取得同一把锁，原连接回滚报 `08S01 / unexpected end of stream`。实读数据库事务/事务空闲超时均为 24 小时、`wait_timeout` 为 8 小时，无法据此把原因归为数据库事务超时；需排查 OBProxy 或中间网络的会话关闭原因。真实实体结构发布也出现过释放锁失败。长操作的锁保护会受影响，因此当前环境不能直接判为可以无条件切换上线。

此连接问题还实际影响了审批后的首次状态同步消费：执行到数据库时钟读取时出现 `08S01 / Connection reset`，状态同步事务回滚，Outbox 正常标记 FAILED 并记录第 1 次重试。随后只执行一次正常队列重试，真实 `OutboxProcessor` 和 `ProcessStatusSyncOutboxHandler` 成功将事件标记 PROCESSED、同步记录标记 APPLIED；最终 HTTP 读取业务状态为 COMPLETED。没有直接修改业务状态或重置重试次数。重试成功不表示连接断开及锁丢失的根因已经解决。

## 脚本与结构

更新 `docs/sql/oboracle.sql`，按文件头要求在临时执行副本中替换随机签名密钥占位符后，在目标 ZDW 中完整执行：**4148 条语句、0 错误、192 张平台表、79 个触发器、0 个无效对象**。原有 `ZDW_CONNECTION_TEST` 保留。该全量执行发生在下述最后一项组件字段同步之前，最新文件未对已有 Schema 重新全量导入。

- 同步当前表单发布 3 张表、Excel 5 张表，以及交接菜单/权限、加签索引调整；移除已经退役的热修复结构及菜单。
- `PROCESS_UI_RELEASE_BINDING.PINNED_RELEASE_ID` 允许 NULL，适配当前 FORM 发布不绑定旧版 release ID 的行为及 Oracle 空字符串语义。
- `PROCESS_ASSIGNEE_INCIDENT.OPEN_SLOT` 虚拟列扩为 `VARCHAR2(329)`，覆盖表达式声明的最大长度，消除 OBE-12899。
- 移除与唯一索引列序列完全相同的 3 个冗余普通索引，消除 OBE-01408。
- 收尾时同步工作区新出现的 V117 组件结构：全量脚本不再建立 `ENTITY_FORM_NODE.COMPONENT_NAME`，更新保留版本列及扩展类型注释；对应表没有需要转换的初始种子。最新文件为 **4147 条语句**，表及触发器数量不变。变更涉及的两张表及注释共 **23 条语句**用随机表实测通过，并断言退役列不存在、注释一致，随后清理确认；已有远端表未执行删除列或旧数据转换。

本轮 **未新增、修改、删除或重命名任何 Flyway 迁移**，没有运行 Flyway repair，也没有改写历史 `V001`。

## 应用修复

| 实际问题 | 修复方式 |
| --- | --- |
| 驱动 `getSchema()` 抛 `AbstractMethodError`，完整登录名含租户路由 | 同一连接查询 `SYS_CONTEXT('USERENV','CURRENT_SCHEMA')`；空结果直接失败，防止读到其他 Schema |
| 表和列注释元数据为空 | 主连接池及独立 DDL 连接统一设置 `remarksReporting=true` |
| 指定列名返回生成键时报额外参数未绑定 | 统一设置 `useOraclePrepareExecute=true`，验证真实 AUTO 主键回填 |
| 服务端预编译无法发送长 CLOB 流 | 同时设置 `usePieceData=true`，验证 16KB 中文 CLOB 完整读写和比较 |
| 执行前参数元数据为 NULL，Spring 绑定未知类型 NULL 时 NPE | OB Oracle 专用 JdbcTemplate 对未知空值显式绑定 `Types.NULL`；覆盖查询、更新、数组批处理，保留显式类型和 Boot 配置；通用写入器同步修复 |
| `COMMENT` 列及自动生成别名是保留字 | 按数据库方言引用手写 SQL；Oracle/OB Oracle 的 MyBatis-Plus 列及结果别名统一引用 |
| `releaseSavepoint` 抛错误码 17023/SQLState 99999 | 仅容忍已确认的 OceanBase 驱动“不支持释放”异常，保留其他错误及外层事务回滚 |
| 数字文本转换的 NLS 参数报 OBE-12702 | 仅 OB Oracle 使用经实测支持的 NLS 参数形式，保留精度、符号及尾零 |
| 未命名 BPMN 节点写入空字符串，Oracle 将其转为 NULL 后违反 NODE_NAME 非空约束 | 空白名称统一回退为节点 ID，覆盖发布、草稿更新、直接保存及绑定名称；保留有效原名称和非空约束 |

## 验证范围

| 验证 | 结果及实际覆盖 |
| --- | --- |
| 全生产 BaseMapper 读取 | **94/94**；真实 MyBatis-Plus 工厂、每表最多 1 行；包含列投影、逻辑删除、保留字及映射注册。22 个表有返回行，其余空表仅证明 SQL/列结构有效 |
| JDBC 专项回归 | **4/4**；12 组数值边界与 NULL、3 个保存点执行器及外层回滚、主池/DDL 双路径注释、CLOB 流与命名生成键共同工作 |
| 运行时方言综合探针 | **40/40**；DDL、元数据、锁竞争、分页、行锁、有界更新/删除、CLOB 比较/LIKE/正则、错误分类及 RETURNING |
| 动态实体 Mapper | **7/7**；真实 `EntityDataDynamicMapper.xml` 结果映射和生产 Provider，权限参数、中文及特殊 ID、CRUD/NULL、分页/count 一致性、唯一值候选、关联投影、系统实体白名单 |
| 表单/Excel/流程 Mapper 与 JDBC 业务 | **6/6**；当前表单发布、Excel 配置/任务、待办/交接/已发起分页、ProcessTask 手写与 BaseMapper 路径，以及文件登记/异常事件、15 种 BPMN 节点首次保存与草稿更新；CLOB、空值、锁、回滚、AUTO 回填均已实测 |
| 单元与既有回归 | **52/52**；包括 12 个既有 TaskInbox/Flowable 事务用例、21 个节点同步用例及 5 种数据库的 SQL 渲染断言，统一 Maven 执行 |
| 独立后端 HTTP 业务闭环 | **25 个阶段通过**；实体/表单发布、中文与数值 CRUD、分页/搜索/删除、流程发布/发起/待办/审批/历史查询，以及审批后的业务状态同步；引擎和业务记录最终均为 COMPLETED |

常见不兼容静态扫描覆盖所有生产 Mapper、XML、SqlProvider，未发现绕过方言的 MySQL `IFNULL`、`DATE_FORMAT`、`GROUP_CONCAT`、`FIND_IN_SET`、`INSERT IGNORE`、`ON DUPLICATE KEY` 等用法。该扫描仅补充风险排查，不能替代未执行分支的实测。

实际 Mapper 方法覆盖：

- `EntityDataDynamicMapper`：普通/权限/含删除记录的按 ID 读取，`selectByIdForUpdate`，普通/权限/条件的 count 与分页，`selectByCondition`，唯一候选及锁定查询，insert、update、updateCurrentTask（含清空）、逻辑/物理删除。
- `EntityRelationProjectionMapper`：count、selectPage、selectMultiValues；别名、权限范围及多值数量边界。
- `EntityFormPublishedMapper`：insert、selectById、updateById、lockPublication、insertDefault、removeDefaultForm、clearDefault。
- `TaskInboxMapper`：count、countUnready、selectPage；`TaskHandoverMapper`：count、selectPage、selectAllTaskIds、findEligible；`StartedProcessPageMapper`：count、page。
- `ProcessTaskMapper`：completeTask、selectByTaskIdForUpdate、selectByTaskId、selectProgressHistoryByProcessInstanceId、insert（指定/生成 ID）、updateById。
- `NodeConfigMapper`：通过真实流程节点同步服务保存 15 种节点类型，覆盖未提供名称、空字符串、纯空白、有效名称，以及同一节点 ID 的草稿替换更新。
- Excel 使用生产 JDBC Repository：配置增改/发布/停用/恢复/删除、设置增改/发布、字典与接口查询；任务新认领及幂等重放、所有权、进度、文件 ID（含 NULL）、行锁、错误明细及成功/失败收尾。
- 额外业务入口：`SystemEntityReadService.findPage/findById`、`StoredFileAccessService.register`、`AssigneeIncidentRecorder.create`（新增/重放）、带 NULL 的查询和两种数组批处理。

项目唯一 XML Mapper 为 `EntityDataDynamicMapper.xml`，其中定义结果映射，SQL 来自生产 Provider；测试通过真实工厂加载 XML 后执行查询，并断言 Oracle 大写列标签正确转为业务字段名。

最终构建还修复了一处工作区近期修改的编译问题：UI 发布校验跨包调用 `EntityFormNodePropertyPolicy.isBuiltInComponent`，方法已经公开但类仍为包可见；仅将类设为 public 并补充共享规则说明，未改该策略业务逻辑。

## HTTP 验证与清理

HTTP 请求仅访问 `127.0.0.1:18084`。为避免测试后端自动处理其他数据，全局定时调度始终关闭；状态同步使用独立临时 Spring 上下文，仅消费本次流程的 `PROCESS_STATUS_SYNC` 事件，其他认领立即释放，没有调用通知或外发处理器。该上下文已经关闭。

目标实体/表单 `ora_flow_141148`、流程 `ora_flow_141148_bound` 及已审批记录保留，便于复核。另一个验证未命名节点修复的已发布测试流程也保留。独立 CRUD 样本已删除，随机 `ORAP_` 探针表已清理；数据库原有连接测试表保留。

- 流程实例：`587e4909-c6d9-11f1-b4f4-7612d5b69a47`
- 业务记录：`527024a3e4b34d23b2eb421c83a99ed7`
- 状态同步事件：`2109913304410365954`（PROCESSED / APPLIED）

验证后端已正常关闭，`18084/19094` 不再监听。原后端 PID `1120` 仍监听 `8080/9090`，本轮未重启或改动其配置。

## 重复运行

应用切换时选择 `WORKFLOW_DATABASE_VENDOR=oceanbase-oracle`，业务及 DDL 两套连接的 URL/用户名/密码都指向目标 Oracle 租户，`DB_DRIVER_CLASS_NAME` 与 `SCHEMA_DB_DRIVER_CLASS_NAME` 均为 `com.oceanbase.jdbc.Driver`。驱动构建参数使用 `-Pjdbc-oceanbase -Doceanbase.jdbc.version=2.4.19`。三个已验证的驱动属性由生产代码按 vendor 自动设置，无需在 URL 上重复拼接。

本轮验证包通过外置官方驱动 JAR 启动，Flyway 关闭，直接使用上述全量脚本初始化的结构；默认运行配置与原有 `8080/9090` 后端保持不变。

实库测试显式 opt-in，未设置环境变量时跳过。数据库应已通过对应 `docs/sql/oboracle.sql` 初始化；读扫描只查询，其他实库测试创建随机 `ORAP_` 临时表并在 finally 中清理。不要把初始化脚本作为现有数据库的增量升级脚本直接执行。

通过安全方式设置 `FLOW_OB_ORACLE_TEST_URL`、`FLOW_OB_ORACLE_TEST_USER`、`FLOW_OB_ORACLE_TEST_PASSWORD` 后，在 `workflow-server` 目录运行：

```sh
mvn -pl workflow-app -am test -Pjdbc-oceanbase -Doceanbase.jdbc.version=2.4.19 \
  -Dtest=OceanBaseOracleMapperReadDatabaseTest,OceanBaseOracleJdbcCompatibilityDatabaseTest,OceanBaseOracleEntityQueryDatabaseTest,OceanBaseOraclePersistenceDatabaseTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

连接池实测建连需要约 5.6–11.1 秒、连接校验最高约 3.4 秒；本轮独立后端使用 `DB_POOL_CONNECTION_TIMEOUT_MS=30000`、`DB_POOL_VALIDATION_TIMEOUT_MS=10000`。这只能容纳已观测延迟，不能修复前述空闲连接断开及锁丢失。

本机详细验证日志保存在 `/tmp/flow-ob-validation/`：初始化 `oracle-init-batch.log`，最后组件结构同步 `oracle-v117-fixture.log`，全 Mapper `oracle-basemapper-read-piece-final.log`，JDBC `oracle-jdbc-compatibility-vendor-final.log`，运行时 `oracle-runtime-probes-piece-final.log`，单元测试 `oracle-final-unit-tests.log`，构建 `oracle-final-build.log`，HTTP `oracle-http-flow-results.json` / `oracle-http-flow-summary.json`，状态同步首次失败与重试 `oracle-status-outbox-runner.log` / `oracle-status-outbox-runner-retry.log`，长锁连接 `oracle-long-lock.log`。
