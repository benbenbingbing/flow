# 运行时数据库适配进度

本文件跟踪当前改造，不代表整个系统已经支持切换数据库。目标是完成原先列出的全部十类运行时依赖，支持 MySQL、Oracle、PostgreSQL、Kingbase、达梦、OceanBase MySQL 和 OceanBase Oracle。历史 Flyway 迁移及部署脚本不在本轮范围内。

## 最终验收（2026-09-23 09:15）

后续启动回归修正（09:36）：用户通过 `start.sh` 启动时，脚本会从 queue 模式后端移除 `SCHEMA_*` 变量；生产 YAML 中三项严格占位符导致 `DatabaseConnections` 提前解析失败。已将 schema-publisher 的 URL/用户名/密码默认值设为空，仅直接结构连接仍校验必填身份，Flyway 配置及脚本隔离不变。新增测试读取生产 YAML 并移除环境继承，修复前复现同样的占位符错误；修复后 queue 无结构身份、仅配置 URL、direct 缺失/完整身份四场景通过。连同连接装配、两种 DDL 执行器及模块边界共 20 项通过，无失败/跳过；尚未替用户重新执行完整启动脚本。迁移文件未改。

本轮约定的十类运行时代码改造和 MySQL 验证已完成。框架能够处理的普通查询、更新和分页优先使用 MyBatis-Plus；复杂 SQL 及实际产品差异保留方言。数据库标准和七产品实现位于 integration，主代码不含 JDBC、MyBatis 或 Spring 执行依赖；业务大文本仍是 String。

最终在全部子 agent 停写后执行 clean 构建和运行时回归：**589 个测试类、3734 项；3723 通过、7 断言失败、3 错误、1 原有跳过**。10 项未通过与此前记录一致，本次无新增适配失败；这不是全仓全绿。39 个 `MySql*DatabaseTest` 类共 **207 项全部通过、无跳过**，该集合也包含绑定/元数据契约断言，不宣称每项都是独立实库场景。

- 本轮新增的普通查询类型绑定、完整长文本比较、多值权限、凭据更新、转办回滚、发布唯一性扫描均通过。分页契约 117 项、权限 LOB 契约 14 项、多值权限单元 12 项、动态表七产品查询契约 7 项、方言长文本/标准处理器 4 项通过。
- 既有未通过：`EntityRelationDefinitionServiceTest` 1 项、`SchemaRequiredTablesTest` 2 项、`EntityFormNodeServiceTest` 5 项、`EntityDataDynamicServiceSubFormTest` 1 项、`ApprovedExpressionMigrationTest` 1 项；`ProcessDefinitionServiceParseTest` 保留 1 项跳过。历史部署迁移、已有 HTTP/逻辑删除架构规则及须先 package 的制品测试仍不在运行时选择器内。
- 最终脚本 `/private/tmp/run_flow_portability_final_regression.py`；日志 `/private/tmp/flow-portability-final-regression.log`。脚本独立统计失败并返回非零，未把 Maven failure.ignore 下的 BUILD SUCCESS 当作全部通过。
- 本轮定向回归曾遇到构建期间类文件替换导致的编译错误及 `NoSuchMethodError`；停写后最终 clean 回归中已消失，相关审计导出与时间线实库测试通过。
- 其他六种数据库按约定仅实现并验证 SQL/绑定契约，没有实库环境。OB Oracle 的 CLOB 有序结果仍缺独立版本证据；大表发布预检和新增数据库往返尚未做吞吐压测。这些限制不属于已验证能力。

最终差异检查通过。新增、修改、删除的 Flyway 迁移文件：**均无**。未提交或部署；其他前端/移动端工作区改动保留。

## 模块边界

按用户确认的边界，`integration` 只定义数据库标准并生成 SQL/调用描述，不建立连接、不执行 JDBC/MyBatis、不管理事务，也不运行队列 worker。

按用户后续明确的原则，常规条件组合优先使用 MyBatis-Plus Wrapper，分页优先使用框架分页插件；Java 业务层的大文本统一为 String，CLOB/NCLOB 读写优先使用 MyBatis 内置处理器。自定义方言仅补充框架和通用 SQL 没有覆盖的实际需求，不继续扩展成另一套数据访问框架。

- `workflow-integration/workflow-database/com.workflow.integration.database.api`：DDL、分页、时钟/元数据/锁、单行插入与事务锁行语法接口，方言选择入口，表/列/索引/默认值/元数据、SQL 与参数模型。接口和七产品方言实现同在本模块，不再经由 `contracts`；主代码无外部依赖。
- `workflow-integration/workflow-database/dialect`、`query`、`schema`：七种产品的语法规则、SQL 生成及纯文本校验。锁描述包括绑定键、调用类型、成功/竞争返回码和专用事务释放要求，描述本身不触发任何执行。
- `workflow-core/com.workflow.core.database`：通用连接、JDBC 时钟/元数据/锁执行及其本地接口，以及沿用 MyBatis-Plus Page 的 OffsetPage 适配。执行器从 integration 取得语法，负责资源关闭、返回值校验和事务边界；分页适配只保存原始 offset，不生成 SQL。
- `workflow-entity`：实体字段和索引定义、结构计划、直接 DDL 执行、结构请求入队与等待。队列端口和实现位于 `entity.data.infrastructure.schema`。
- `workflow-db-migrator/com.workflow.dbmigrator.schema`：结构 worker 和重放验收执行。历史 Flyway 迁移仍不参与本轮修改。
- `workflow-app/com.workflow.config.database`：Spring 数据源、方言和 MyBatis 装配。厂商 JDBC 驱动 profile 属于 app/db-migrator 运行制品，不属于纯方言模块。

调用方向：业务模块直接依赖 `workflow-database` 的公共标准；`contracts` 中不保留数据库方言接口。MyBatis 动态 SQL 使用同模块 `DatabaseQuerySql` 入口，查询方言直接按产品缓存，不再使用跨模块 SPI。

## 当前进度与验证边界

| 原排序 | 类别 | 当前状态 | 验证边界与保留事项 |
| --- | --- | --- | --- |
| 3 | 动态建表和改字段 | 七种 DDL 渲染、直接及队列通道已接入；MySQL 实库已验证 | 非 MySQL 无实库环境；不支持证明的重放失败保留失败状态，不能据错误码假定成功 |
| 2 | 数据库结构读取 | 主表、团队表、物理表解析、系统实体目录已改为元数据端口 | 非 MySQL 无环境，厂商统计查询与驱动行为尚无实库验证 |
| 1 | 驱动和数据源 | 主数据源、独立连接、URL 驱动选择、按产品初始化、Flowable 语法族装配已接入 | 非 MySQL 驱动按 Maven profile 及目标环境版本打包；迁移器启动迁移不在范围内 |
| 4 | 数据库命名锁 | 已接入七产品的锁适配；MySQL 真实并发与跨 DDL 持锁通过 | 非 MySQL 仅实现，无环境实测；OB Oracle 使用只持锁的独立事务，需满足下述产品能力条件 |
| 5 | 分页与标识符 | 普通 Mapper 查询优先改为 Wrapper，外层分页统一交给 MyBatis-Plus；实体动态 Provider、关系图和 Embed 复杂 SQL 通过 IPage 接入插件。121 个 Mapper 的全局复核后仅保留 5 处嵌套分页方言片段；锁定查询保持既有锁范围。此前 54 个 Mapper 的手工分页适配已由本轮框架分页替代，验证进度见下文 | 8 个批量 UPDATE/DELETE 已接入独立写入方言，两处队列主键访问提示已按产品生成；运行期配置迁移的 22 处 Wrapper LIMIT 已处理：13 处唯一查询去掉分页、2 处存在性计数、7 处方言分页和稳定排序；普通 Wrapper 的剩余手工分页也统一交给 MP Page；JDBC 查询及嵌套子查询保留方言分页，非 MySQL 仍无实库结论 |
| 6 | 指定排序规则 | 待办、Embed 共 18 处显式 COLLATE 已移除，授权定向回归通过 | 依赖运行库字段及连接已统一比较规则；Embed 整数实体 ID 转文本已接入查询方言，业务 Mapper 不再硬编码 AS CHAR |
| 7 | 冲突写入与幂等 | 单行插入、事务锁行和唯一冲突规则已实现；Embed 请求占用、流程状态同步，以及版本/会话计数、唯一值门闩、启动协调、结构计划、三类限流、HOTFIX 计数、流程关联占用已接入；回执/版本写入、版本配置、Outbox、审计、抄送、审批异常、文件登记、Embed 断言和授权、组件模板及表单节点已接入保存点恢复或事务锁行 | 运行时显式 INSERT IGNORE 已移除；当前版本配置与 legacy 草稿的四个写入冲突分支已改为基表当前 revision 读取，MySQL 并发与旧快照验证通过；转办中的待办与审计写入失败现统一回滚，不再吞错后继续事务；终止事务分支的厂商分类归第 10 项，非 MySQL 并发语义仍待实库验收 |
| 8 | 日期和类型转换 | 结构队列、队列指标及启动协调使用 UTC 数据库时钟；指标等待秒数改为 Java 计算；实体动态 Mapper、结构状态及 HOTFIX 指标使用 CURRENT_TIMESTAMP；实体动态查询的 LIKE 改为完整模式绑定；唯一冲突样本去掉 CAST，事件状态排序改为 CASE；登录封禁结束时间在 Java 中计算并绑定 | Outbox、流程动作及 SLA 队列，任职/组织、流程关联、任务汇总、Embed 到期查询、文件删除的 UTC 时间已接入方言；NOW 会话时间与审批异常的日期偏移也已接入。任职版本 DATE_FORMAT 已改为 Java 格式化，流程绑定 AS UNSIGNED 已改为 Long 绑定，Embed 整数 ID 转文本已接入七产品方言；包含/前缀搜索和数字布尔投影已完成；固定字符字段的空值判断、待办普通节点/候选前缀及 Embed 首屏游标已处理；应用 MyBatis 与通用 JDBC 写入器统一布尔 0/1 绑定。物理字段规则已按类型判空，开放接口范围键已兼容旧空串和新非空标记，并验证两类租约释放隔离。唯一值候选已去除字段侧 CAST/LOWER/TRIM，普通文本采用保守预筛并由 Java 完整比较，TEXT 使用原生全量读取；应用动态 Map 已按 JDBC 类型读取完整大文本。受控 SQL、团队身份、本人/部门/结构化规则和委托均已绑定；按字段类型处理比较值，匿名查询明确拒绝，列表计划及关系图保留 NULL 参数。未声明类型的 MyBatis NULL 已改为标准 SQL NULL，独立判空参数显式声明类型；任职区间使用日期列直接比较，首尾相接与最大边界已完成 MySQL 回归。结构化权限及普通列表的非文本 LIKE 已接入方言并完成 MySQL 回归；LOB 完整比较、多值侧表权限、普通比较按可信字段类型绑定已补齐，验证见最新收尾记录 |
| 9 | JSON SQL | 版本配置的 JSON_VALID/SET/REMOVE/CAST AS JSON 已改为 Java 投影；MySQL 对照通过 | 原始文档仍一次 JOIN 读取，优先级和回退属于实体业务模块，无需数据库方言 |
| 10 | 错误处理 | 结构 worker 使用结构证明；统一错误方言已接入共享 JDBC/MyBatis、独立 DDL/队列及全局响应；幂等执行器复用完整异常图分类，MySQL 实库通过 | 未确认的厂商码保守返回 UNKNOWN；达梦目前细分已确认的唯一、引用和长度错误，其他错误保持失败；其他六库无实库证据，最终审计保留保守失败策略；不将未知厂商码或任意异常静默认定为已成功 |

## 方言选择及实现边界

`workflow.database.vendor` 默认为 `auto`，按主 JDBC URL 判断，也可配置：

`mysql`、`oracle`、`postgresql`（别名 `pgsql`）、`kingbase`、`dm`、`oceanbase-mysql`、`oceanbase-oracle`。

OceanBase 使用 `jdbc:oceanbase:` 时必须显式指定租户模式；使用 MySQL/Oracle 驱动的 OceanBase URL 也应显式配置，避免误认为原生产品。未知配置会失败，不会默认为 MySQL。

七种 DDL 实现按 MySQL、PostgreSQL、Oracle 语法族复用，保留独立产品类供后续差异覆盖：

- MySQL：内联索引、注释、字符集和 `ON UPDATE`。TEXT 非空默认值按 MySQL 8 表达式默认值生成。
- PostgreSQL / Kingbase：独立索引、`COMMENT ON`、分步 `ALTER COLUMN`，以受控触发器维护审计时间，删除表时清理辅助函数。Kingbase 使用内置 `plsql` 语言和 `sys_catalog` 元数据，锁采用 `sys_try_advisory_lock`，不要求额外创建 PostgreSQL 名称的兼容函数/视图。
- Oracle / 达梦 / OceanBase Oracle：大写引用标识符、独立注释和索引、`MODIFY`、审计触发器。Oracle 按 12.2+ 标识符规则及默认 `MAX_STRING_SIZE=STANDARD` 上限实现；不静默降低大于 38 的精度或大于 4000 的 VARCHAR 长度。
- Oracle 系列触发器在 UPDATE 未显式指定审计时间时刷新；PostgreSQL 在行实际变化且审计时间值未改动时刷新。两者与 MySQL 对“显式赋原值 / 无实际变化更新”的边界行为不完全相同，未来获得环境后需实测确认。
- MySQL DDL 字面量沿用启用反斜杠转义的 SQL mode；`NO_BACKSLASH_ESCAPES` 还需要专门处理，不能把现有实库结果推广到该模式。

本轮没有非 MySQL 数据库环境。这些实现尚未做对应产品实库验证，也不能据此宣称整个系统已经能够在七种数据库上运行。

参考：

- [Oracle 类型限制](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlqr/Data-Types.html)
- [Oracle 触发器](https://docs.oracle.com/en/database/oracle/oracle-database/21/lnpls/plsql-triggers.html)
- [PostgreSQL 触发器](https://www.postgresql.org/docs/17/sql-createtrigger.html)
- [Kingbase SQL 参考](https://help.kingbase.com.cn/v9/development/sql-plsql/sql/index.html)
- [达梦数据定义](https://eco.dameng.com/document/dm/zh-cn/pm/definition-statement.html)
- [OceanBase Oracle 触发器](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000000510832)

## 驱动、连接及发布锁

主数据源由 `DatabaseDataSourceConfiguration` 装配，`spring.datasource.hikari.*` 的池配置继续生效。驱动默认跟随 JDBC URL，也可用 `DB_DRIVER_CLASS_NAME`、`SCHEMA_DB_DRIVER_CLASS_NAME` 明确指定；方言由 `WORKFLOW_DATABASE_VENDOR` 控制。MySQL 的字符集初始化只在 MySQL 语法族应用，其他数据库不执行 `SET NAMES`。显式配置 `spring.datasource.hikari.connection-init-sql` 会覆盖默认初始化。

`DatabaseConnections` 提供普通身份和结构发布身份的独立连接，均直接创建物理会话。主业务池、发布锁、DDL 执行连接互不复用；关闭发布锁句柄会关闭物理会话，不会把仍持有锁的连接归还业务池。专用 DDL URL 必须与选定语法族相符。

非默认驱动的 Maven profile 定义在 app 和 db-migrator 运行模块：

| Profile | 制品 | 版本选择 |
| --- | --- | --- |
| `jdbc-postgresql` | `org.postgresql:postgresql` | Spring Boot 管理 |
| `jdbc-oracle` | `com.oracle.database.jdbc:ojdbc11` | Spring Boot 管理 |
| `jdbc-kingbase` | `cn.com.kingbase:kingbase8` | `-Dkingbase.jdbc.version=目标驱动版本` |
| `jdbc-dm` | `com.dameng:DmJdbcDriver18` | `-Ddm.jdbc.version=目标驱动版本` |
| `jdbc-oceanbase` | `com.oceanbase:oceanbase-client` | `-Doceanbase.jdbc.version=目标驱动版本` |

Maven profile 在构建时使用 `-P` 选择。厂商驱动版本由目标环境决定，不硬编码猜测值；离线/内部制品须按上述坐标提供。MySQL 仍沿用现有驱动依赖。Oracle/PostgreSQL profile 已通过 Maven 模型验证，未下载和连接这些数据库。

Flowable 的 Spring 引擎配置在初始化前统一选择语法族：MySQL/OB MySQL → `mysql`，PostgreSQL/Kingbase → `postgres`，Oracle/达梦/OB Oracle → `oracle`。显式 `workflow.database.flowable-type` 可覆盖；已有 H2 测试使用 `h2`。本改造不改动独立迁移器的历史迁移流程。

发布锁条件：

- MySQL：`GET_LOCK/RELEASE_LOCK`，Java 计算的锁名与原有 SQL `SHA2` 锁名相同，支持新旧版本滚动期间互斥。
- PostgreSQL：会话级 `pg_try_advisory_lock/pg_advisory_unlock`。
- Kingbase：`sys_try_advisory_lock/sys_advisory_unlock`，不依赖额外 PostgreSQL 兼容函数。
- Oracle/达梦：`DBMS_LOCK.REQUEST(..., FALSE)`，显式 RELEASE。需有 DBMS_LOCK 使用权限；达梦需已提供该系统包，其文档注明 MPP 不支持该包。
- OB MySQL：使用支持 GET_LOCK 的版本（官方 V4.3.1 文档已列出）；较早版本缺少能力时明确失败，不降级成本地锁。
- OB Oracle：官方文档注明部分版本仅支持 `release_on_commit=TRUE`，因此锁在专用连接的独立事务持有，业务和 DDL 绝不使用此连接，释放时回滚这一个事务。该模式不能使用会自动回收/提交锁连接的代理。
- 普通锁竞争返回忙；NULL/SQL 异常不伪装成竞争。获取失败关闭连接，释放失败仍关闭会话，句柄重复关闭无副作用，线程重复发布不覆盖原句柄。

依据：[PostgreSQL advisory locks](https://www.postgresql.org/docs/15/functions-admin.html)、[达梦 DBMS_LOCK](https://eco.dameng.com/document/dm/zh-cn/pm/dbms_lock-package.html)、[OceanBase GET_LOCK](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001379158)、[OceanBase Oracle REQUEST](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001975494)、[Kingbase 兼容函数说明](https://help.kingbase.com.cn/v9.1.1.30/PDF/KingbaseES%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98%E6%89%8B%E5%86%8C.pdf)。

## 验证记录（持续更新）

### 纯方言模块边界调整

本轮从 `clean` 开始重新构建并执行定向回归，**628 项通过、零失败、零错误、零跳过**。其中数据库纯语法 8、Outbox 1、管理 11、worker 包装 3、实体 56、流程 44、Embed 12、应用 493。该集合与历史记录有重叠，不累计相加。

其中 **32 个 MySQL 实库场景**覆盖：动态建表及改字段 2、发布锁并发及跨 DDL 持锁 2、结构队列竞争/重放 6、分页插件 1、实体查询 7、跨模块分页/行锁 6、服务查询/时钟指标 5、版本 JSON 对照 3。全部使用随机测试表并清理。Spring 配置装配、MyBatis 的 234 个动态 SQL 参数场景、模块路径及架构边界也在本轮通过集合内。

架构检查明确禁止方言模块依赖 contracts、core、业务模块、JDBC、Spring 或 MyBatis；业务调用方不得依赖具体方言实现。移除的旧包经过 Maven clean，不以残留 class 掩盖引用错误。app/db-migrator 的 PostgreSQL/Oracle 驱动 profile 另外通过离线 Maven 模型校验，未连接非 MySQL 数据库。

本轮测试选择器和安全连接脚本在 `/private/tmp/run_flow_database_boundary_tests.py`，完整输出为 `/private/tmp/flow-database-boundary-tests.log`；连接参数从本地环境文件读取，输出不包含凭据。

以下为先前阶段验证记录，其中待办描述代表当时状态；后续处理以最新收尾及最终验收为准。各轮选择器存在交集，不累计成总数，也不把定向通过当作全仓库或七库兼容性完成。


上一阶段合并回归共 **180 项通过，零失败、零跳过**（数据库模块 19、worker 包装 3、实体模块 51、Embed 8、应用模块 99）。该集合包含前述 DDL/锁/队列、排序规则移除、版本 JSON 和 MyBatis 分页的验证；仍不包含下文单独记录的既有 HTTP 架构规则失败。

本轮查询方言定向回归 **105 项通过，零失败、零跳过**（数据库模块 5、实体模块 23、应用模块 77）。其中 `MySqlEntityQueryDatabaseTest` 为 7 个新增 MySQL 实库场景，另包含已有分页插件实库场景。这一集合与此前 180 项有重叠，不直接相加为累计用例数。

`DatabaseMybatisConfiguration` 统一选择 MySQL、PostgreSQL、Kingbase、达梦、Oracle 12c 分页实现；OB 分别采用 MySQL/Oracle 分页实现。审计字段自动填充仍留在应用配置。新增 MySQL 分页实库测试验证了有序第二页、末页、总数和页数；此配置不会自动修复手写 SQL 中的 LIMIT。

### 运行时查询方言与实体读取

`DatabaseQueryDialect` 提供物理标识符、保持大小写的结果别名、MyBatis/命名参数分页及 JDBC 分页。MySQL/OB MySQL 生成 `LIMIT offset, limit`，PostgreSQL/Kingbase 生成 `LIMIT limit OFFSET offset`，Oracle/达梦/OB Oracle 使用 `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY`。JDBC 分页返回 `BoundSqlStatement`，同时携带 SQL 与正确顺序的参数；不允许调用方把匿名问号传给只返回字符串的接口后自行猜测参数顺序。

- Spring 服务注入查询接口；MyBatis Provider 通过 `ProviderContext.databaseId` 和 integration 的选择入口获取无状态适配器。`DatabaseIdProvider` 与 DDL 使用同一产品配置，不使用全局“当前数据库”变量，也不在配置缺失时回退到 MySQL。
- `EntityDataSqlProvider` 的四种分页、全部动态表/列引用以及 `EntityRelationProjectionSqlProvider` 已接入；条件、权限和 IN 值仍采用绑定参数。LIKE 在 Java 中构造包含模式后绑定，避免三参数 CONCAT；唯一候选的 CAST 尚属第 8 项剩余工作。
- 按主键读取的动态实体和系统表引用查询不再附加冗余 LIMIT 1。分页按原有排序加主键打破平局；逻辑删除和权限谓词仍在分页前应用。
- `SystemEntityReadService` 保留字段白名单、敏感字段排除与权限规则，由查询方言生成分页与参数顺序。
- `PermissionSqlBuilder` 的 HAS_TODO 表名通过注入的查询接口引用；任务记录 ID 仍然绑定，不重新引入 CONVERT。
- `EntityDataRow` 在动态实体的 MyBatis 结果映射中归一化物理列名大小写，不处理请求参数或 JSON 业务键。关系图使用显式引用的固定结果别名。Mapper XML 放在 Mapper 的同包资源路径，可由普通 MyBatis 和 MyBatis-Plus 加载。
- MySQL 实库覆盖分页/末页/空页/同时间排序、权限与 count 一致、IN/空 IN/LIKE、特殊待办 ID、保留字字段、NULL 更新、锁定读取、逻辑及物理删除、关系图多值上限、系统实体字段白名单，以及 MyBatis-Plus 工厂装配。夹具使用大写 ID/ORDER 列证明返回键归一化生效；全部使用随机表名并清理。

分页语法依据：[MyBatis ProviderContext](https://mybatis.org/mybatis-3/apidocs/org/apache/ibatis/builder/annotation/ProviderContext.html)、[PostgreSQL LIMIT/OFFSET](https://www.postgresql.org/docs/17/queries-limit.html)、[Kingbase SELECT](https://bbs.kingbase.com.cn/kingbase-doc/v9.4.12/development/application-develop-guide/reference/sqlserver/query/SELECT/select_clause.html)、[Oracle 行数限制](https://docs.oracle.com/en/database/oracle/oracle-database/18/sqlrf/SELECT.html)、[达梦数据查询](https://eco.dameng.com/document/dm/zh-cn/pm/check-phrases)、[OceanBase Oracle 分页](https://www.oceanbase.com/docs/enterprise-oceanbase-database-cn-10000000000355231)。非 MySQL 仍无对应实库验证。

本轮定向命令（实库环境变量同下文）：

```sh
cd workflow-server
mvn -o -pl workflow-app -am \
  -Dtest=MySqlEntityQueryDatabaseTest,MySqlDatabasePaginationTest,MySqlDatabaseConfigurationTest,EntityDataSqlProviderTest,EntityDataSqlProviderCandidateFilterTest,EntityDataSqlProviderHistoricalAccessTest,EntityRelationProjectionSqlProviderTest,SystemEntityReadServiceTest,HasTodoPermissionBindingTest,PermissionSqlBuilderTest,DataPermissionEngineTest,CurrentProcessTaskAssigneeLookupTest,EntityListScopePolicyPreviewTest,DatabaseAdapterBoundaryTest,ContractPackageArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 普通 Mapper 与服务层分页

54 个 Mapper 中的 **117 个普通查询、119 处分页子句** 已改用 `DatabaseQuerySql.page`。它是 MyBatis 标准动态 SQL 表达式入口，由当前工厂 `_databaseId` 选择适配器；不安装 SQL 全局重写器，不自行解析 SQL。`${...}` 仅展开代码内固定的方言模板，分页值继续通过模板中的 `#{...}` 绑定。覆盖流程待办/已发起/抄送、发布历史、版本数据集、管理目录、Embed 和 Outbox 过期租约发现查询。

- `DatabasePaginationMapperContractTest` 自动发现这 117 个查询，分别检查填充与空可选条件，共 **234 项**；校验模板展开、参数映射及绑定值没有作为 SQL 文本注入。
- Mapper 定向回归 **451 项通过，零失败、零跳过**（Outbox 1、管理 8、数据库 5、实体 29、流程 1、Embed 12、应用 395）。包含 7 个实体查询、6 个跨模块分页/行锁及 1 个分页插件 MySQL 实库场景。
- SLA 任务、流程任务、加签主键、加签生成任务及 mutation receipt 的 5 个锁查询，在核实主键/唯一索引后去掉多余 `LIMIT 1`，保留 `FOR UPDATE`。MySQL 两连接验证目标行阻塞、另一行可写、回滚后释放。锁测试明确强制回滚仅执行 SELECT 的 MyBatis 会话。
- 有唯一约束保障的人员解析器、数据范围方案/版本、HOTFIX release、文件地址/上传幂等及结构计划单条读取也去掉多余 LIMIT。数据权限、逻辑删除及幂等条件保留。
- 审计导出/操作时间线、办理人异常列表/重试扫描、结构操作最新记录、HOTFIX 观察记录、多值用户读取及字典选择使用注入的查询接口；原有上限保留，相同排序时间增加主键排序。
- `DynamicTableService.scanUniqueConflicts` 按字段最多返回 5 个冲突样本，JDBC 读取原生字符串/数字，不再依赖 `CAST AS CHAR`。多值关系的动态显示列使用查询方言引用。

服务层定向回归 **145 项通过，零失败、零跳过**（管理 3、实体 28、流程 43、应用 71），与前述集合有重叠，不直接累计。新增 `MySqlServiceQueryDatabaseTest` 的 5 个实库场景验证：每字段 5 条冲突样本与原生小数；200 人有效关系及第 201 人拒绝；500 条办理人异常筛选/排序；10000 条审计导出与 500 条时间线及旧 operationId 兼容；队列指标的 UTC 到期边界、空队列和未来时间。测试表随机命名并清理。

`AsyncQueueMetrics` 使用带状态条件的 COUNT/MIN 子查询，去掉两处 LIMIT、UTC_TIMESTAMP 和 TIMESTAMPDIFF。一次刷新读取一个数据库 UTC 时间，作为绑定参数供两张队列使用，再由 Java 计算非负等待整秒。两个查询均成功后发布本轮值，部分失败则保留上一轮；不以应用节点时钟判断到期。

本阶段定向命令：

```sh
cd workflow-server
mvn -o -pl workflow-app -am \
  '-Dtest=*Mapper*Test,!ProcessLogicalDeleteMapperSqlTest,*SqlProvider*Test,*Permission*Test,CurrentProcessTaskAssigneeLookupTest,ProcessCcFlowableIntegrationTest,EntityTransitionStatusFlowableTest,EntityFormFieldEventCleanupIntegrationTest,MySqlQueryFragmentTest,MySqlRuntimePaginationDatabaseTest,MySqlEntityQueryDatabaseTest,MySqlDatabasePaginationTest,DatabaseAdapterBoundaryTest,ContractPackageArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -pl workflow-app -am \
  '-Dtest=AsyncQueueMetricsTest,MySqlServiceQueryDatabaseTest,MySqlRuntimePaginationDatabaseTest,*DynamicTable*Test,MySqlDynamicSchemaDatabaseTest,*EntityMultiValue*Test,EntityUserReferenceAdapterTest,EntitySchemaOperationTransactionIntegrationTest,UiHotfixGovernanceServiceTest,AssigneeIncidentIdempotencyTest,EmptyAssigneePolicyFlowableIntegrationTest,SystemAuditQueryServiceUnifiedTest,*PersonResolver*Test,*EntityListScope*Test,*StoredFile*Test,DatabaseAdapterBoundaryTest,ContractPackageArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

剩余的 `LIMIT ... FOR UPDATE` 不能使用普通分页接口直接替换；批量队列写入还涉及并发领取和锁顺序，需和幂等、日期及租约适配一起处理。运行期配置迁移的 Wrapper 查询尚未改造；这与本轮排除的历史 Flyway/部署脚本不是同一范围。

扩大 Mapper 回归时还发现既有 `ProcessLogicalDeleteMapperSqlTest` 失败：它要求 `ProcessDefinitionConfigMapper.selectAnyByIdForBindingUpdate` 带 `deleted=0`，而该未修改方法的源码注释和当前实现明确要求包含已删除记录，用于保护流程绑定。已对照 HEAD 确认为既有不一致；未修改该业务行为或弱化测试，定向通过集合单独排除这一类。

### 幂等占用：语法与执行分离

`integration/api/DatabaseInsertDialect` 定义单行插入生成和唯一冲突分类；同模块实现为七个产品生成标准 INSERT 与引用标识符，不接受 SQL 表达式值，不依赖 MySQL upsert 的影响行数。厂商错误规则只接受明确的唯一约束错误，不能将整个 SQLSTATE 23 类都当作重复请求。

`core/database/JdbcIdempotentInsert` 使用调用方 Spring 事务绑定的连接执行。事务内先建立保存点；重复键后只回滚该语句，保留之前的业务工作。保存点恢复失败、错误链含其他错误、死锁或连接异常继续抛出；不自行提交。接口说明明确排除延迟唯一约束、触发器额外写入的唯一冲突，以及锁行/计数器初始化，后两类需要专门保留锁语义。

已接入的业务入口：

- `MyBatisEmbedIdempotencyAdapter`：初始请求占用改用 JDBC 执行工具，MyBatis 保留查验、重试接管和 fencing 更新；请求摘要冲突、重放和独立事务边界保持不变。
- `ProcessStatusSyncOutboxHandler`：审计占用、状态副作用和审计确认同事务；重复逻辑事件跳过副作用，失败回滚后可重投。创建/确认时间从数据库 UTC 时钟读取并绑定，去掉该 Mapper 的 UTC_TIMESTAMP。

这两处原有 `INSERT IGNORE` 不再忽略非法数据：非空、外键、检查约束和长度错误会失败。这里只保留“唯一冲突代表已有记录”的业务用途。

定向 **34 项通过**；随后扩大到 Embed 全部现有测试、流程状态同步及相关 Flowable 场景，共 **350 项通过、零失败、零错误、零跳过**（纯方言 3、流程 1、Embed 314、应用 32）。两个集合重叠，不累计。新增 6 个 MySQL 实库场景覆盖绑定值/NULL、主键和逻辑唯一键、非法数据、保存点保留先前工作、外层回滚、六线程唯一赢家、useAffectedRows 两种驱动选项、Embed 的摘要冲突/重放/fencing、流程状态同步的回滚重投和逻辑键并发去重。另有 3 个故障注入场景验证保存点失败、恢复失败及混合错误链不被吞掉。

执行脚本与完整输出：`/private/tmp/run_flow_idempotent_regression_tests.py`、`/private/tmp/flow-idempotent-regression-tests.log`。首次定向运行发现 app 的 Mockito 使用 subclass mock maker，无法模拟 final 执行工具；该工具改为普通公共类后重跑并扩大回归通过，未改动业务断言。

唯一冲突规则依据：[MySQL 错误码](https://dev.mysql.com/doc/mysql-errors/8.0/en/server-error-reference.html)、[Oracle ORA-00001](https://docs.oracle.com/en/error-help/db/ora-00001/)、[PostgreSQL 事务重试说明](https://www.postgresql.org/docs/16/mvcc-serialization-failure-handling.html)、[Kingbase 完整性约束错误](https://help.kingbase.com.cn/v8.6.7.12/admin/reference/ref-errcodes/Class23.html)、[达梦异常处理](https://eco.dameng.com/document/dm/zh-cn/pm/dm8_sql-exception-handling.html)、[OceanBase MySQL 重复键](https://en.oceanbase.com/docs/common-oceanbase-database-10000000000829728)、[OceanBase Oracle 错误码](https://www.oceanbase.com/docs/enterprise-oceanbase-database-cn-10000000000944894)。其他产品仍没有实库验证。

### 事务锁行、计数器与限流

`DatabaseInsertDialect.rowLock` 返回纯数据 `DatabaseRowLockPlan`，包含初始化语句、唯一键锁定查询及插入竞争恢复标记。MySQL/OB MySQL 使用 no-op ON DUPLICATE KEY UPDATE，PostgreSQL/Kingbase 使用指定唯一键的 no-op ON CONFLICT；Oracle/达梦/OB Oracle 使用仅插入缺失行的 MERGE，再执行 FOR UPDATE。MERGE 不修改 ON 条件中的键列。

实际执行仅在 `core/database/JdbcLockedRow`：要求连接加入调用方 Spring 事务，逐个绑定值，在目标唯一键上检查恰好一行。不会自行提交，也不根据 upsert 的影响行数判断成功。MERGE 的首次插入竞争使用保存点恢复后重新锁定；其他唯一索引冲突导致目标键不存在时仍失败。保存点或事务恢复失败不授予锁。调用方必须有即时主键/唯一约束，且自行保持多行锁顺序；不适用于有额外写入副作用的 UPDATE 触发器。

接入范围和保留的业务规则：

- 唯一值门闩按稳定键排序后锁定，保留字段 sentinel 和具体值门闩的命名空间；锁行不删除。
- 版本号计数器持锁后通过标准 CASE 追平历史最大值，六个并发请求分配不同版本，不能用初始值覆盖既有计数器。会话计数器同样不重置 active_count/lock_version，限额和 Launch 栅栏仍属于 Embed。
- 启动协调持锁执行版本动作，动作与完成记录同事务；失败可重试，已完成版本不再执行。创建及完成时间取数据库 UTC 时钟后绑定。
- 结构计划按 entity_id/plan_hash 复用，原计划及状态证据不被初始值覆盖。MySQL 实测暴露了可重复读旧快照问题：取得锁后，以及状态无实际变化时返回前，都必须使用当前锁定读取，避免将已提交计划误判为不存在。
- 登录失败保持账号后客户端的锁顺序，以显式 CASE 按本次失败后的次数判断封禁；先赋封禁结果，再更新次数和窗口，不依赖 MySQL 对先前赋值的隐式读取。窗口边界仍用 `<`，过期窗口只重置次数，不提前清除有效封禁。
- 开放接口拒绝配额时回滚本次计数；Embed 的 REQUIRES_NEW/noRollbackFor 规则继续保留拒绝尝试的计数，持久化故障则回滚整次配额和租约操作。二者没有合并为一个改变业务语义的通用限流器。
- HOTFIX 观察指标独立提交；总数/失败数在持锁后递增，成功请求保留最近失败摘要，指标身份不变。失败写入回滚，不影响已提交观察记录。

该方案比单条厂商 upsert 多了初始化验收和业务更新的数据库往返；例如单个限流桶由 upsert + 读取变为初始化 + 锁定验收 + UPDATE + 读取。当前验证覆盖正确性，尚未做吞吐压测。

定向 **85 项通过**后，增加 HOTFIX 实库场景并扩大到全部 Embed、登录认证、开放接口安全、相关实体及流程测试，最终 **451 项通过、零失败、零错误、零跳过**，覆盖 86 个测试类。集合包含 **26 个 MySQL 实库场景**：既有单行幂等 6、事务锁行 8、启动/结构/HOTFIX 6、限流 6；这些场景均使用随机隔离表并清理。保存点恢复分支用 MySQL 重复 INSERT 验证执行器行为，不把它算作 Oracle/达梦/OB MERGE 实测。

执行脚本与输出：`/private/tmp/run_flow_locked_counter_regression_tests.py`、`/private/tmp/flow-locked-counter-regression-tests.log`。架构边界检查仍通过；integration 主代码无 JDBC、Spring、MyBatis 或 contracts 依赖。此集合与历史定向结果重叠，不累计相加，也不表示下文记录的范围外失败已消失。

语法依据：[Oracle MERGE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/MERGE.html)、[Oracle 并发 MERGE 唯一冲突](https://asktom.oracle.com/ords/f?p=100%3A11%3A%3A%3A%3A%3AP11_QUESTION_ID%3A9547017300346937928)、[PostgreSQL INSERT/ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html)、[达梦 MERGE](https://eco.dameng.com/document/dm/zh-cn/pm/insertion-deletion-modification.html)、[OceanBase Oracle MERGE](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001166608)。非 MySQL 仍仅实现，缺少对应环境验证。

### 首行锁定、读守卫与流程关联占用

`DatabaseQueryDialect.firstForUpdate` 只生成单表首行锁定 SQL，要求排序包含非空唯一键作为平局规则。四处调用覆盖 SLA 未结束暂停、未结束加签、最近发布版本和最近流程关联。MySQL/OB MySQL、PostgreSQL/Kingbase 使用 LIMIT，达梦使用 TOP；Oracle/OB Oracle 在候选键子查询中限制首行，再在外层锁定基表并重复资格条件，避免对不可更新视图加锁。接口不承担队列 SKIP LOCKED 领取，也不依赖缺失行的 gap lock 防止插入。

另外两处查询依据现有唯一约束直接移除 LIMIT：版本幂等键使用 V046 的 entity_code/record_id/idempotency_key，Embed provider 使用 V067 的 type/issuer_uniqueness_key/subject_namespace。未改写这些历史迁移。相关锁定 Mapper 禁用查询缓存并刷新会话缓存，确保同一事务内经过 JDBC 写入后仍执行真实锁定读取。

实体定义的读守卫通过同一个纯查询接口生成：MySQL/PostgreSQL/Kingbase 使用 FOR SHARE，OB MySQL 使用 LOCK IN SHARE MODE，Oracle/达梦/OB Oracle 使用 FOR UPDATE。后者可能串行化同一实体定义的读守卫；OB MySQL 官方也说明部分版本使用写锁模拟共享锁。当前 MySQL 实库已验证多个读守卫可同时持有，而修改方必须等待。

`ProcessRuntimeService` 通过事务锁行执行器占用流程关联，以候选 ID 是否仍为已锁行 ID 判断本次是否创建，不使用 upsert 影响行数。相同流程定义复用 ACTIVE 实例，不同定义或已有 PENDING 占用保持原有冲突语义；关闭后才进入下一代。生产调用方先在同一事务中插入/更新实体根记录，以根记录锁协调不同代的创建。真实引擎调用仍属于流程业务模块，integration 不执行任何操作。

清理构建产物后，定向回归 **726 项通过、零失败、零错误、零跳过**，覆盖 95 个测试类。其中 **40 个 MySQL 实库场景**包含新增首行锁定/共享读守卫 5、流程占用 3，以及既有单行幂等 6、协调操作 6、事务锁行 8、运行时分页 6、限流 6。新增场景覆盖排序平局、资格过滤、锁等待/释放、缓存刷新、唯一约束、六线程流程单一创建、下一代创建和失败回滚。流程测试使用真实数据库事务及 Mapper，Flowable 引擎入口为模拟实现，其事务内写入用于证明关联与副作用一同回滚，不将其描述为完整引擎集成测试。

本轮增量构建曾因 target/classes 中存在含 `Unresolved compilation problem` 的占位字节码而失败；统一 clean 后重编及测试通过，无需修改对应审计服务源码。脚本与输出为 `/private/tmp/run_flow_first_lock_regression_tests.py`、`/private/tmp/flow-first-lock-regression-tests.log`。此集合与历史结果重叠，不累计相加。其他六种数据库没有环境，仍未实测。

语法依据：[Oracle SELECT](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html)、[Oracle 首行基表锁定](https://asktom.oracle.com/ords/f?p=100%3A11%3A0%3A%3A%3A%3AP11_QUESTION_ID%3A3844846184454)、[达梦查询语句](https://eco.dameng.com/document/dm/zh-cn/pm/check-phrases)、[Kingbase SELECT](https://help.kingbase.com.cn/v8.6.7.24/development/sql-plsql/sql/SQL_Statements_10.html)、[OceanBase 共享锁限制](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001031688)。

### 写入失败后的事务恢复

`core/database/JdbcWriteAttempt` 包装调用方已有的单次 Mapper/JDBC 写语句，保留主键生成、字段填充和返回的影响行数。Spring 事务内使用同一数据源连接建立保存点，失败后先恢复再返回异常；厂商唯一约束分类仍由 integration 的纯规则提供。只有完整 SQL 错误链均属于唯一冲突时才统一为 DuplicateKeyException，混合连接错误、保存点创建/恢复失败不能被吞成幂等成功。驱动不支持显式释放保存点时交由外层事务结束清理，不自行提交。

该入口要求同步、立即执行的单条写入，不适用于批处理、DDL、异步执行或跨事务代理的回调。保存点不能取消 Spring 已设置的 rollback-only，也不能撤销 Java 对象/缓存修改，因此捕获必须发生在事务代理边界内部，重放查询也必须执行真实当前读取。未加入 Spring 事务的调用仅翻译错误，按自动提交使用。

本轮接入：

- 实体变更回执：创建失败恢复后按业务键读取既有结果，摘要冲突及 PENDING 状态继续拒绝。MySQL 六线程实测发现原 FOR UPDATE 会将重复 INSERT 持有的共享锁升级并相互死锁；重放只读，改用方言读守卫，并禁用查询缓存。其他产品沿用各自读守卫规则。
- 实体版本写入：保留计数器锁及旧 Pod 竞争下的有限重试，只回滚本次 INSERT；版本配置的普通及兼容草稿创建同样先恢复再查询版本冲突信息。
- 普通 Outbox 发布：重复逻辑事件保持原有身份与载荷，后续业务写入仍可提交。失败事件重投还需要 UPDATE，使用既有 JdbcLockedRow 按 topic/event_key 初始化并锁定后再改状态，避免同类共享锁升级问题；新事件和重复重投均有并发实测。
- 审计消费及失败审计：重复事件不阻断消费者后续写入；失败审计的 REQUIRES_NEW 仍独立于外层业务回滚。

新增 6 个 MySQL 实库场景覆盖普通 Mapper 的唯一/非空失败、保存点前后写入、外层回滚、自动提交、六线程回执重放、摘要冲突、失败后重试、Outbox 保留 ID/载荷及并发重投、审计消费和独立事务。7 个故障注入场景覆盖保存点创建/恢复/释放失败、混合错误链、厂商错误翻译、不支持显式释放以及错误数据源。测试表使用随机名称，借用 V001 的列/索引和 V004/V064 的指定 ALTER 定义补齐当前结构，未在业务表执行迁移或种子数据，也没有改动历史迁移文件。

定向 **85 项通过**后，扩大到相关版本、审计、Outbox、Embed 及前一阶段数据库适配回归，最终 **887 项通过、零失败、零错误、零跳过**，覆盖 128 个测试类，包含 46 个 MySQL 实库场景（新增 6、此前 40）。脚本与完整输出为 `/private/tmp/run_flow_write_attempt_regression_tests.py`、`/private/tmp/flow-write-attempt-regression-tests.log`。集合与历史验证重叠，不累计相加；也不代表其他六库或全仓库已验证通过。

审计同时确认：唯一值占位、实体绑定等捕获后直接抛业务异常结束事务的入口，无需为了继续执行而添加保存点；其厂商异常分类仍归第 10 项。该阶段识别出的剩余继续执行分支在下节继续接入；UiComponentTemplateService、EntityFormNodeService 已在后续“组件模板与表单节点冲突恢复”阶段处理。仍需复核版本配置等冲突结果重读是否使用当前数据。

事务依据：[PostgreSQL 保存点恢复](https://www.postgresql.org/docs/17/sql-rollback-to.html)、[Spring 事务传播与 rollback-only](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)、[MySQL 重复键共享锁](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)。仅 MySQL 有实库证据，其他产品未实测。

### 抄送、审批异常、文件与 Embed 冲突恢复

`ProcessCcService.createCcRecordIfAbsent` 在自己的 REQUIRED 事务边界内恢复并捕获重复键，按 unique_key 的当前读确认目标记录确实存在后返回 false。运行时只有 true 才发布通知，不再在外层捕获内部事务已抛出的 DuplicateKeyException。历史逻辑删除记录仍持有幂等键，继续阻止重复通知。通知存储自身的重复键/其他失败必须穿透，让新抄送与业务工作一起回滚；严格创建入口的原有行为保留。

`AssigneeIncidentRecorder` 只包装开放事件 INSERT，竞争失败后使用 open_slot 唯一键和读守卫取得当前记录；任务/实例空值分支生成固定 IS NULL 条件，避免空字符串比较和未知类型空参数。开放槽键仍沿用已有业务约束的“任务优先，否则实例，否则固定占位”格式。创建审计放在冲突捕获之外，审计失败回滚整个新事件。REQUIRES_NEW 留存阻断证据、REQUIRED 关闭节点事件的事务语义均保留。`AssigneeIncidentService` 的处置请求占用同样恢复唯一冲突，不增加新的事务边界；独立调用保持自动提交，调用方已有事务时加入该事务。

文件登记加入保存点，冲突后通过读守卫重放首个已提交对象，保留所有者、摘要不同及已删除对象的拒绝规则。Embed 断言 JTI 插入同样恢复后返回已占用，保留真实表的摘要、到期及外键约束；授权创建由持久化仓储恢复保存点，管理服务仍持有 View 锁，并用当前锁定读取返回冲突版本。

新增 **8 个 MySQL 实库场景**覆盖：六线程抄送只通知一次且竞争者仍能提交；通知失败回滚；六线程开放事件只生成一个事件及审计；审计失败与独立事务；六线程处置只调用一次引擎入口；文件首个结果重放、摘要拒绝及外层回滚；断言唯一赢家与回滚重试；授权冲突后的继续读取和提交。引擎入口与通知外部依赖使用替身，数据库、Mapper、Spring 事务代理和事务内副作用为真实执行，不称为 MySQL 上的完整 Flowable 引擎测试。

扩大回归最终 **985 项通过、零失败、零错误、零跳过**，覆盖 143 个测试类，包含 **59 个 MySQL 实库场景**。集合包含前序数据库适配及本轮相关 Flowable、文件、Embed 测试，与历史结果不累计相加。脚本与输出：`/private/tmp/run_flow_remaining_conflict_regression_tests.py`、`/private/tmp/flow-remaining-conflict-regression-tests.log`。

新增表夹具的外键父表也使用随机名称，引用未登记父表时在建表前失败，所有 schema 级约束名单独隔离。清理按子表到父表逆序执行并保留外键校验。本轮测试曾因父表/约束名隔离不完整、测试替身与夹具字段缺失、正向清理外键表而失败，修复后扩大回归通过；清理失败留下的五张确认为本轮创建的随机表已单独清除。没有在业务表执行迁移，也没有新增、修改或删除历史迁移文件。

### 组件模板与表单节点冲突恢复

`UiComponentTemplateService` 的快照 INSERT 接入 `JdbcWriteAttempt`，唯一冲突后先恢复保存点，再按已有父模板锁读取当前数据。`selectByIdForUpdate` 禁用二级缓存并刷新一级缓存，快照创建失败时整个保存事务仍回滚，不保留之前的模板名称等更新；正常版本创建继续由父模板锁串行化。

`EntityFormNodeService` 的单节点 INSERT/UPDATE 同样恢复失败语句，再识别方言唯一错误。移除通过异常文本匹配 `uk_entity_form_node_active_key` 的判断，改用当前读确认目标表单内确有另一个活动节点占用该编码。主键冲突、没有对应占用节点、读取到自身，以及外键/CHECK 等非唯一错误均不伪装成节点编码冲突。当前读不分页，绕过缓存，MySQL 使用共享锁以避免重复插入后升级独占锁；其余数据库沿用既有读守卫方言。捕获后仍抛业务冲突，调用方事务按原语义整体回滚，不把业务失败变成成功提交。

新增 `MySqlUiConflictDatabaseTest` **6 个实库场景**，覆盖六线程节点编码竞争及当前节点返回、失败请求先前写入回滚、主键冲突保持原异常、CHECK 失败保持数据库异常、节点修改和表单修订共同回滚、组件模板版本冲突以及六线程同内容版本复用。测试使用生产 Mapper、实际 Spring 事务代理和随机表；只在写入前设置屏障以固定竞争窗口。所有测试表在结束时清理。

扩大回归 **1056 项通过、零失败、零错误、零跳过**，覆盖 150 个测试类，包含 **65 个 MySQL 实库场景**。脚本 `/private/tmp/run_flow_ui_conflict_regression_tests.py`，日志 `/private/tmp/flow-ui-conflict-regression-tests.log`。其他六库未连接实测，与历史通过数不累计相加。

本轮先运行了完整 `EntityFormNodeServiceTest`，复现下文已记录的 5 个旧子表单失败；扩大通过集合选择其余 35 个用例（含新增主键冲突反例），未修改那 5 项业务规则，也未把定向通过称为全仓库通过。初版实库测试另有两处预期/数据问题：MySQL CHECK 3819 经当前驱动翻译为 UncategorizedSQLException，现验证保留其 SQL 错误码；节点修改用例最初使用无 props 的 TEXT 节点，触发既有属性校验空值问题，事务验证改为带初始 label 的 SECTION 节点。没有改动该显示属性校验逻辑。

该阶段识别的 `GlobalExceptionHandler` 英文报错解析及终止事务入口的统一厂商分类，已在下节接入。批量 DML 分页、队列索引提示、剩余日期/类型转换仍未完成。

本阶段迁移文件新增、修改、删除均为 **无**。integration 数据库模块仍无 JDBC、Spring、MyBatis 或 contracts 依赖。

### 统一数据库错误分类与框架接入

`workflow-database/api/DatabaseErrorDialect` 与 `DatabaseErrorKind` 定义纯错误标准，输入仅 SQLState 与厂商码；`StandardDatabaseErrorDialect` 按七种已选产品分类，不接收 JDBC 异常、不读异常文本、不建立连接。原 `DatabaseInsertDialect.isUniqueViolation` 复用该规则，避免插入恢复与普通 Mapper 使用两套唯一码表。连接状态优先；事务中止状态不能与唯一错误混淆，MySQL Connector/J 的 1205 锁超时即使返回 40001 仍保留锁超时类别。

`core/database/DatabaseExceptionClassifier` 遍历 cause、nextException 与 suppressed，并处理循环引用。只有全部 SQL 错误均明确属于唯一冲突时才允许幂等重复，混合或未知错误不会被根异常里的一个重复键覆盖。`JdbcWriteAttempt`、`JdbcIdempotentInsert`、`JdbcLockedRow` 复用此遍历，覆盖语句关闭失败附加在重复键异常上的情况。

`DatabaseSQLExceptionTranslator` 将分类接入 Spring 异常体系，未知错误保留框架的非唯一分类；默认翻译若只根据首个异常判成重复键，不允许推翻完整异常图的 UNKNOWN 判断。应用装配将同一翻译器交给 Boot 的共享 JdbcTemplate 和 MyBatis SqlSessionTemplate，保留 Boot 的超时/fetchSize 设置与 MyBatis 的执行器类型。专用 DDL、结构入队和 worker 的自建 JdbcTemplate 也使用同一产品规则。捕获后立即抛业务异常的既有入口由框架统一获得 DuplicateKeyException，无需逐个添加保存点。

`GlobalExceptionHandler` 不再解析 MySQL 英文报错、猜测约束名或截取原始 SQL，而是返回统一的重复、必填、缺少默认值、长度、关联、CHECK、数值范围、并发或通用失败提示。底层无法确认的错误保留通用失败，不猜测字段；业务层已有字段身份及业务冲突响应继续保留。通用数据库响应沿用原 HTTP 200 / body code 500 契约，业务冲突仍由原专用处理器返回 409。

本阶段新增 **6 个 MySQL 实库场景**，使用真实 Boot JDBC/MyBatis 装配，验证 JDBC 与 Mapper 的各类约束失败、保存点和外层回滚、实际唯一值预留服务的业务冲突、MVC 返回、真实锁等待超时以及真实死锁的一方提交/一方回滚。故障测试另覆盖 mixed/cause/next/suppressed/循环异常图和资源关闭失败；8 个 MVC 切片显式提供测试错误规则，不装配数据库。

扩大回归 **1196 项通过，零失败、零错误、零跳过**，覆盖 175 个测试类，包含 **92 个 MySQL 实库场景**。集合补回动态 DDL、结构队列、数据库锁、实体查询和 JSON 投影等受底层翻译器影响的回归，与历史通过数不累计相加。运行器 `/private/tmp/run_flow_error_translation_regression_tests.py`，日志 `/private/tmp/flow-error-translation-regression-tests.log`。随机测试表已按夹具关闭流程清理，未访问现有结构发布队列；迁移文件新增、修改、删除均为 **无**。

初次实库执行发现 1205 对应 40001 的实际驱动映射，修正后加入固定反例与实库回归；唯一值测试最初误从 V001 读取建表定义，已改为只读取 V050 的目标表结构，没有修改或执行历史迁移。其他六种数据库未实测。达梦当前只细分已确认的唯一、引用和长度码，其余错误以 UNKNOWN 保持失败，不声称已穷举厂商错误或所有错误出口。

规则依据：[MySQL 错误目录](https://dev.mysql.com/doc/mysql-errors/8.4/en/server-error-reference.html)、[PostgreSQL SQLState](https://www.postgresql.org/docs/17/errcodes-appendix.html)、[Kingbase 错误代码](https://bbs.kingbase.com.cn/kingbase-doc/v9/admin/reference/ref-errcodes/errcodes.html)、[Oracle 错误目录](https://docs.oracle.com/en/database/oracle/oracle-database/19/errmg/ORA-00910.html)、[OceanBase 官方错误码定义](https://github.com/oceanbase/oceanbase/blob/master/src/share/ob_errno.def)、[达梦唯一错误示例](https://eco.dameng.com/document/dm/zh-cn/pm/dbms_errlog-package)、[达梦引用错误示例](https://eco.dameng.com/community/article/20240605101715GWKJ0IW1BWQP9U7FWU)、[达梦长度兼容示例](https://eco.dameng.com/community/article/1d8e1c3094e8ad17e154882cd3e121b3)。框架接入遵循 [Spring SQLExceptionTranslator](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html) 与 [MyBatis SqlSessionTemplate](https://mybatis.org/spring/apidocs/org/mybatis/spring/SqlSessionTemplate.html)。

### 有界批量写入与队列租约

`DatabaseMutationDialect` 与其七产品实现放在 `workflow-database`。接口描述单表限量更新/删除及完整主键访问提示，不依赖 MyBatis/JDBC。业务表名、资格条件、赋值和清理策略仍在各模块的 Mapper Provider 中，其他模块通过 `api` 调用。

- MySQL / OB MySQL 保留原生 `ORDER BY ... LIMIT`，避免改成同表子查询后改变锁定扫描或触发 MySQL 1093。PostgreSQL / Kingbase / Oracle / 达梦 / OB Oracle 在单条 DML 内按稳定排序取得有限主键集合，并在外层再次检查原资格条件。
- 完整主键的每一列必须出现在排序中；支持断言回放的 `(provider_id, jti_digest)` 复合主键。调用方必须提供非空唯一主键，不得更新主键。值使用命名/MyBatis 绑定，禁止匿名参数和多语句片段；这是受信源码接口，不是任意 SQL 解析器。
- 并发改变候选状态时允许少于批次数量，零行不证明整个队列已空；由后续调度继续发现。没有引入 `SKIP LOCKED`、额外连接或应用节点时钟，也没有改变 worker 的状态流转、owner/token 条件和重试策略。
- 已接入 Embed 的 6 个批量维护方法、OpenAPI 请求租约清理和 Outbox 批量领取。OpenAPI 清理新增 `expires_at, lease_id` 稳定排序；Embed 敏感上下文擦除用固定 64 位零摘要替换 `REPEAT()`，原合法 JSON 和摘要长度约束保持有效。
- `DatabaseRuntimeDialect` 增加 DML UTC 时间及加秒数表达式，`DatabaseRuntimeSql` 为 MyBatis 提供固定片段。Outbox 与流程动作队列的领取、续租、完成、失败、回收全部接入。PG/Kingbase 使用语句开始时间，避免 `CURRENT_TIMESTAMP` 固定在长事务开始时间；独立读取数据库时钟的原有 `utcNowSql()` 保持实时取值。
- 两处原 `FORCE INDEX (PRIMARY)` 由方言按产品返回；MySQL 语法族保留提示，其他产品只保留主键等值更新及状态/过期条件。正确性依赖这些条件及数据库行锁，不依赖优化器提示。

新增 `MySqlBoundedMutationDatabaseTest` 的 **8 个实库场景通过**：复合主键清理和精确上限、Launch 过期与回滚、Context 擦除 CHECK/Session 外键保护、幂等回执保留、请求租约稳定分批、六个 Outbox 执行者并发领取互不重叠的 10 条记录、跨时区 UTC/租约回收/旧 token 拒绝、流程动作队列完整租约生命周期。全部使用随机隔离表并清理，不读取或消费真实业务队列。

本阶段第一组定向测试 **42 项通过，0 失败、0 错误、0 跳过**。 扩展回归于 2026-09-22 13:35 完成：**185 个测试类、1224 项测试，0 失败、0 错误、0 跳过**，其中 **100 个 MySQL 实库场景**。首轮扩展回归暴露测试误将 DATETIME(0) 更新时间与 DATETIME(6) 租约时间的整秒差要求为精确 120；已按现有字段精度检验微秒差，并保留独立 UTC 对照，修正后全组通过。没有修改历史表定义。非 MySQL 产品仍没有实库证据；有界候选语法和时间表达式按产品文档实现，不把 MySQL 并发测试结果推广到其他数据库。

语法依据：[MySQL 限量 UPDATE](https://docs.oracle.com/cd/E17952_01/mysql-8.4-en/update.html)、[PostgreSQL 批量更新](https://www.postgresql.org/docs/17/sql-update.html)、[Oracle 行数限制](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/ROWNUM-Pseudocolumn.html)、[PostgreSQL 时钟语义](https://www.postgresql.org/docs/17/functions-datetime.html)、[达梦时间函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)、[OB Oracle 时间间隔](https://en.oceanbase.com/docs/enterprise-oceanbase-database-en-10000000000867733)。

### 剩余数据库时间函数

13 个 Mapper 和两个 JDBC 服务中的 `UTC_TIMESTAMP`、`NOW`、`DATE_ADD/SUB` 已改用 `DatabaseRuntimeDialect` / `DatabaseRuntimeSql`；产品语法保留在 integration 的纯实现中。日期值和重试秒数继续使用原有参数绑定，没有把连接、事务或业务规则移入 integration。

- UTC 场景包括 SLA 事件领取/成功/失败/回收/取消、任务 SLA 汇总、流程关联、组织负责人投影、任职定义/周期/撤销、Embed 应用及凭据有效期、文件逻辑删除。
- 抄送已读、加签完成、任务完成、编码序列更新时间和数据委托有效期保留原会话时区墙钟语义，使用独立的 `currentTimestampExpression()`，不统一替换为 UTC。MySQL 仍为无小数参数的 `CURRENT_TIMESTAMP`，与原 `NOW()` 一致；PG/Kingbase 使用语句时间按会话时区转为无时区值，Oracle/DM/OB Oracle 使用 `LOCALTIMESTAMP`。
- 审批异常最近 30 天统计、普通任务和多实例节点入口的重试分别使用固定负秒数或单个 JDBC 秒数绑定。偏移参数不重复、不重排，仍由数据库时钟计算；重试次数、退避算法和幂等请求逻辑保持原样。
- SLA 事件候选按 `trigger_at, create_time, id` 稳定排序。未更改原成功/失败确认的 owner/token 条件，也没有为此次语法替换改变处理器的事务或回收规则。
- H2 的审批异常业务集成测试已移除对 `DATE_ADD` 的 SQL 文本替换；现在直接执行生产方言返回的 `TIMESTAMPADD`。它仍是 H2 业务回归，不能作为 MySQL 实库证据。

首轮定向 **54 项全部通过**，其中 `MySqlRuntimeTimeDatabaseTest` 的 7 个 MySQL 实库场景验证了：三个会话时区中的 UTC/本地时间及正负偏移与原 SQL 对照；SLA 六执行者竞争、重试、旧租约确认和终态取消；任职版本/撤销守卫；任务 SLA 与流程关联；本地时区的委托有效期、抄送和编码序列；两条审批异常重试分支、30 天统计及请求重放；文件删除隔离和事务回滚。随后补充了第 8 个 Embed 应用/凭据到期实库场景，纳入扩展回归。 最终扩展回归 **199 个测试类、1332 项测试，0 失败、0 错误、0 跳过**，其中 **108 个 MySQL 实库场景**；新增的 8 个时间场景全部通过。

这些变化没有解决剩余 `DATE_FORMAT`、字符/数值转换、Oracle 的空字符串及布尔投影、字符串拼接等差异。最终兼容性审计仍需覆盖这些调用点，不能以已消除的几个函数名称证明整库可移植。

时间语义参考：[Oracle 会话时区 LOCALTIMESTAMP](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/LOCALTIMESTAMP.html)、[PostgreSQL 语句时钟与时区](https://www.postgresql.org/docs/17/functions-datetime.html)、[达梦日期时间函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)。

### 任职版本格式与整数标识转换

- 任职目录一次聚合读取职务版本、组织更新时间、任职数量及最大版本/更新时间，由 `PositionDirectoryRevisionRow` 在 Java 中生成既有版本字符串；保留六位微秒、无任职时间的 `0`、无记录/缺失必要事实的 null。Mapper 的字符串接口保持不变，不把目录业务协议放进方言模块。
- 流程绑定的普通查询和锁定查询先通过 `ProcessDefinitionBindingKey` 规范为正数 `Long`，以 `jdbcType=BIGINT` 绑定活动索引键。支持前导零和外围空格；非法、零、负数及超过 Long.MAX_VALUE 的输入无匹配，避免旧 MySQL CAST 将 `12abc` 等宽松转换为其他流程。仍返回全部历史重复绑定，由原调用方检查基数，锁定顺序仍为 id。
- `DatabaseQueryDialect.integerIdentifierText` 仅接受整数标识列或 alias.column，逐段校验、引用。MySQL/OB MySQL 使用 CHAR，PostgreSQL/Kingbase/达梦使用 VARCHAR(64)，Oracle/OB Oracle 使用 VARCHAR2(64)。接口与七种产品模板同在 integration，仍不包含连接、执行或事务代码。Embed 的列表和表单关联转换整数一侧，保留字符串外键的匹配规则，避免隐式数值比较丢失大整数精度或接受错误的前导零外键。

本轮定向 **84 项通过，0 失败、0 错误、0 跳过**。新增 `MySqlIdentifierConversionDatabaseTest` 的 **7 个 MySQL 实库场景**覆盖旧 SQL 与 Java 版本协议逐项对照、版本各组成事实变化和回滚、V001+V073 的真实生成列和别名绑定、大整数与非法输入、第二物理连接验证全部重复绑定受锁和回滚释放、整数转文本的有符号边界及 NULL、Embed 资源状态/归属/发布版本及错误外键拒绝。只复制指定表和增量结构到随机测试表，测试后清理。

本轮定向运行脚本：`/private/tmp/run_flow_identifier_conversion_tests.py`，日志：`/private/tmp/flow-identifier-conversion-tests.log`。当前新增、修改、删除的迁移文件均为 **无**。

最终扩展回归 **205 个测试类、1396 项测试，0 失败、0 错误、0 跳过**，其中 **115 个 MySQL 实库场景**；2026-09-22 14:36 完成。扩展脚本为 `/private/tmp/run_flow_identifier_conversion_regression_tests.py`，日志为 `/private/tmp/flow-identifier-conversion-regression-tests.log`。与上一阶段测试重叠，不累计相加；仍属于定向回归，不代表全仓测试或全部适配已完成。

实现参考：[Oracle CAST](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CAST.html)、[达梦类型转换函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)。非 MySQL 仅完成实现，没有对应实库验证。

剩余审计明确包含：唯一值候选的文本/LOB 归一化及剩余空字符串语义（包含搜索和布尔 SELECT 投影已在后续阶段处理）；其他动态引用、异常出口及冲突后读取（该阶段发现的运行期配置迁移 22 处 Wrapper 分页已在下一阶段处理）。其中版本配置在插入冲突和乐观更新失败后仍调用普通读取，需要复核 MySQL 可重复读事务中的最新 revision 提示，尚未在本阶段宣称完成。

### 配置迁移的 Wrapper 查询

运行期配置导入、导出、回滚和环境映射服务原有 **22 处 LIMIT 1** 已处理，范围是 `workflow-migration` 的运行时业务，不包含部署迁移器或历史 Flyway 文件。

- **13 处唯一查询无需分页**：字典编码配合通用 Mapper 的逻辑删除条件；资产类型/发布历史；导入包 checksum；来源类型/编码环境映射；资产类型/业务键/范围基线；接口类型/编码/版本/未删除。依据 V001、V036 的实际唯一索引，未新增或修改约束。
- 接口导入保存原来仅按类型和编码任取一条，现在补上导入包携带的实现版本（缺省为 1），避免跨版本覆盖。指定版本不存在时按既有保存接口创建该版本，已有版本仍使用其 revision 检查；没有版本信息的旧引用按版本、主键降序稳定解析。
- **2 处依赖存在性判断**改为通用 Mapper 的 selectCount > 0，不加载任意扩展，不输出数据库原生布尔表达式。
- **7 处首条查询**注入 integration 的 `DatabaseQueryDialect`，使用已有七产品分页实现。资产最新版本、回滚历史及基线目标按 source_version/id 降序；旧接口引用按 version/id 降序。原业务条件、参数绑定和逻辑删除过滤保留。
- `workflow-migration` 显式依赖纯方言模块；没有把查询执行、Spring 或 MyBatis 代码加入 integration。执行继续由原服务和 Mapper 负责，原事务边界不变。

本轮 **26 个测试类、132 项定向测试通过，0 失败、0 错误、0 跳过**，2026-09-22 15:05 完成。包含配置迁移现有导入导出/签名/回滚/映射/版本引用测试、查询片段与架构边界，以及新增 `MySqlConfigMigrationQueryDatabaseTest` 的 **7 个 MySQL 实库场景**：同版本稳定首条、历史和回滚范围、禁用映射与类型隔离、完整/分范围基线、字典删除副本与重复导出、接口导入的版本精确定位、同名多类型/版本的依赖与旧引用解析、重复 checksum 返回已有批次。一个场景覆盖多个相邻路径，不把每个断言计为独立实库测试。

实库夹具复制 V001 指定表及 V021/V036/V088/V091 的指定增量结构到随机表，未执行其中的种子、回填或清理业务数据语句，测试后清理。首次运行修正了夹具的包内访问、final 类构造和目标实体依赖，最终全部通过；没有通过关闭场景或放宽业务断言消除失败。

运行脚本：`/private/tmp/run_flow_config_migration_query_tests.py`；日志：`/private/tmp/flow-config-migration-query-tests.log`。本阶段只复验受影响链路，未重复上一阶段全部 1396 项，不将两组有重叠的结果相加。当前新增、修改、删除的迁移文件均为 **无**。

### 搜索模式、数字布尔结果与组织路径

- **19 个查询**将三参数 CONCAT 包含搜索改为 MyBatis bind 生成完整模式，再以 VARCHAR 参数绑定；参数为 null 时模式仍为 null，原有 `%`/`_`/反斜杠通配行为保留。覆盖抄送、SLA、任职/用户、Embed 目录和 UI 引用候选。多值关系的显示文本 LIKE 同样在 Java 中组装模式，EQ 保留原始值。
- 组织后代查询和路径更新的 **2 处前缀拼接**也改为绑定完整模式。NULL 原路径不匹配任何记录；NULL 新路径显式写 NULL，避免 Oracle REPLACE 将其解释为删除子串。逻辑删除范围及调用方事务保持不变。
- **13 个 COUNT 布尔查询、1 个历史版本存在性查询、5 个 Embed 就绪列/锁占位列**改为 CASE 返回 0/1 或常量 0，由既有 MyBatis Boolean 映射读取。版本存在性仍在内层最多取一行，不以全表 COUNT 替代快速存在性判断。
- **7 处 excludeId 可选排除条件**改为在 MyBatis 中判断，空串或 null 表示新增查重，不把空串判定交给数据库。Embed 三种状态更新把 revoked 请求先变为整数，再在 SQL 中比较 1；原乐观版本条件、撤销人/时间写入及清空规则保留。
- 职务的流程引用总数改为 UNION ALL 的派生表聚合，避免 Oracle 的 SELECT 必须带 FROM 限制；UI 草稿文档用 LENGTH(TRIM(...)) 判断非空，避免 LOB 与空串直接比较。以上采用通用 SQL 或应用侧参数准备，不新增不必要的厂商接口，也未向 integration 引入执行逻辑。

本轮 **45 个测试类、535 项定向测试通过，0 失败、0 错误、0 跳过**，2026-09-22 15:33 完成。其中 **22 个 MySQL 实库场景**：原时间场景 8、跨模块分页 6、新增表达式场景 8。`MySqlQueryExpressionBindingTest` 逐一验证 19 个查询在 NULL/空串/数字文本/引号/反斜杠/中文下的完整模式和 VARCHAR 参数映射；新增实库测试覆盖七类可选排除查重、启动账号/子菜单/角色关系/流程及历史存在性、旧 MySQL CONCAT 搜索结果对照、三源引用汇总、JSON 文档与空白候选、Embed 就绪与锁占位映射、三类撤销状态及乐观版本、组织层级边界和回滚。多值关系、用户组织、接口引用、Embed 和 MyBatis 动态查询既有回归包含在通过集合中。

首次选择器意外包含 `PositionManagementMigrationTest`，触发已记录的 V062 `Can't reopen table` 历史部署迁移问题（9 个错误），未到达应用测试。失败日志保留在 `/private/tmp/flow-query-expression-initial-tests.log`；按本轮运行时范围排除该部署测试后执行上述完整定向集合，未修改历史迁移来掩盖失败。最终脚本为 `/private/tmp/run_flow_query_expression_tests.py`，日志为 `/private/tmp/flow-query-expression-tests.log`；这些是定向结果，不代表全仓库通过。当前新增、修改、删除迁移文件均为 **无**。

实现依据：[Oracle CASE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CASE-Expressions.html)、[Oracle 空字符串与 NULL](https://docs.oracle.com/en/database/oracle/oracle-database/18/sqlrf/Nulls.html)。其他六库没有实库验证。

后续仍须处理权限/流程状态中的非空字符串条件、Embed keyset 起始游标、唯一值候选的文本/LOB 比较。另已确认 PostgreSQL DDL 将布尔字段存为 SMALLINT 以配合 0/1 谓词，而当前 MyBatis/JDBC 装配未提供 Boolean 写入值的统一数值绑定，需继续核对并实现；本阶段只解决 SQL 布尔表达式，不能据此宣称布尔存储适配完成。幂等冲突后读取和其他异常出口的最终审计仍待完成。

### 固定字符空值、巡检游标与布尔参数绑定

本轮继续处理运行时 SQL 的空串差异和写入类型约定：

- 菜单权限、实体编码、实体流程计数、旧版流程状态规则、待办记录 ID/办理人及 Embed 租约释放使用 `NULLIF(字符列, '') IS [NOT] NULL`。仅作用于已确认的字符列；不把数值或 LOB 字段套用到这条规则中。MySQL 的 PAD SPACE / NO PAD 比较行为由列排序规则保留。
- 待办普通节点判断显式允许 `node_type IS NULL`，避免 Oracle 中 `COALESCE(node_type, '') != 'ADD_SIGN'` 漏掉普通任务；组名前缀改为 `SUBSTR(..., 1, 5)`。候选用户/组/角色、已认领任务、真实引擎任务、加签以及实体坐标限制继续保留。
- Embed 两个巡检查询在 MyBatis 中判定空的首屏游标，后续页仍使用 Grant/用户复合键和原批次上限；活跃 Session 的资格筛选、聚合和缺失 Counter 的 LEFT JOIN 不变。职务可选组织类型以 VARCHAR 绑定并通过 NULLIF 判空，保留原 MySQL 对纯空格和制表符的不同处理。
- `integration.database.api.DatabaseScalarValues` 定义七产品业务布尔列的 0/1/null 编码，主代码仍无任何数据库执行依赖。`NumericBooleanTypeHandler` 与 `DatabaseMybatisConfiguration` 在应用 Mapper 解析前装配默认、BOOLEAN 和 BIT 参数处理；非空值写数值，已知 Boolean 的 NULL 使用 INTEGER 类型。JDBC 幂等插入和事务锁行在 core 的参数绑定器中使用相同编码。
- 该布尔约定与现有 DDL 的 TINYINT(1)/SMALLINT/NUMBER(1) 对齐；不会递归转换 JSON，也没有修改数据源或 Flowable 的独立 MyBatis 工厂。未声明 Java/JDBC 类型的动态 NULL 参数仍属于后续审计，不能把已知 Boolean 的 NULL 验证推广到所有 NULL 参数。

验证使用 `/private/tmp/run_flow_empty_boolean_tests.py` 从 `clean` 编译后运行，输出保存在 `/private/tmp/flow-empty-boolean-tests.log`。本轮 **26 个测试类、467 项通过，0 失败、0 错误、0 跳过**，包含 **52 个 MySQL 实库场景**，其中 **10 个新增**：

- `MySqlEmptyStringQueryDatabaseTest` 6 个：两类排序规则和菜单授权对照、流程状态及计数、真实待办候选/角色前缀/认领/办理人规则、复合游标首末页和缺失 Counter、租约释放及回滚、组织类型可选参数。
- `MySqlNumericBooleanDatabaseTest` 4 个：普通 BaseMapper 与已知 Boolean NULL、动态 Map 字段与真假查询、批处理及 primitive/BIT 参数和回滚、JDBC 幂等/锁行及事务恢复。测试在真实 JDBC 调用上检查 setter，遇到 setBoolean 或以 Boolean 传入 setObject 会失败，同时验证 JSON 文本保持原样。
- 既有 MySQL 表达式 8、批量更新 8、限流 6、幂等插入 6、事务锁行 8、异常翻译 6 个继续通过；应用装配、权限、待办和模块边界回归也在此集合中。

验证中保留了三次问题记录：首次增量构建读到 target 中的错误编译产物，clean 后消除（`flow-empty-string-stale-classes.log`）；测试观测器最初只统计 setInt，已补上 Spring 的 `setObject(value, INTEGER)`（`flow-empty-boolean-binding-audit.log`）；既有 Outbox 测试遇到 DATETIME(0) 把恢复时间舍入到下一秒，现先验证与数据库当前时间的差小于一秒，再在两秒内等待实际可领取，生产队列逻辑未改动（`flow-empty-boolean-recovery-rounding.log`）。上述日志均位于 `/private/tmp`。没有删除用例或通过放宽业务资格条件掩盖失败。

该阶段尚未完成的类型化 FIELD 判空和开放接口范围键已在下一阶段处理；唯一值候选文本转换、冲突后当前版本读取和所有异常出口仍需收尾。匿名用户的 `create_by = ''` 权限兜底也需结合访问规则复核。

依据：[Oracle NULLIF](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/NULLIF.html)、[MySQL NULLIF 与比较规则](https://dev.mysql.com/doc/refman/8.4/en/flow-control-functions.html)、[MyBatis 类型处理器与 NULL 绑定](https://mybatis.org/mybatis-3/configuration.html)。其他六种数据库按要求未做实库测试。迁移文件新增、修改、删除均无。

### 类型化字段判空与开放接口范围键

`DatabaseQueryDialect.emptyValuePredicate` 根据可信字段元数据生成 EMPTY/NOT_EMPTY 条件：数字、布尔和日期只将 SQL NULL 视为空；字符和大字段按 Java `Character.isWhitespace` 的字符集合判断空白，检查完整内容，不截断到 VARCHAR。数值 0 和布尔 false 不再受到 MySQL 隐式字符转换影响。缺失字段类型和不合法列名拒绝验证，权限 SQL 保持拒绝访问。

开放接口租约新增固定范围标记 `open-api-application-v1`。纯参数编码继续为 MySQL、OB MySQL、PostgreSQL 写空串，为 Oracle、OB Oracle、Kingbase、达梦写非空标记；查询兼容旧空值和新标记。MySQL 保留旧写入格式以支持新旧节点并行。开放接口释放只操作应用范围，Embed 释放只操作 `embed-runtime-grant-v1:` 范围，避免新增非空标记后发生跨用途释放。

2026-09-22 16:44 完成 clean 后定向回归：**33 个测试类、512 项通过，0 失败、0 错误、0 跳过**，包含 **59 个 MySQL 实库场景**。新增 `MySqlTypedEmptyPermissionDatabaseTest` 4 项，覆盖完整 Java 空白字符、三类排序规则、超过 4000 字符的大字段、数值/日期/布尔、真实权限 Mapper 及拒绝访问；新增 `MySqlRequestScopeDatabaseTest` 3 项，覆盖新旧范围兼容、跨用途隔离、事务回滚、并发限额与释放重获。全部使用随机测试表并清理。脚本和输出：`/private/tmp/run_flow_typed_empty_scope_tests.py`、`/private/tmp/flow-typed-empty-scope-tests.log`。与先前集合重叠，不累计相加。

其他六种数据库仍无实库验证。达梦正则处理 CLOB 受产品的 `CLOB_MAX_CALC_LEN` 等能力配置约束，不能据 MySQL 通过推断其他产品的大字段上限。该阶段没有实现虚拟多值字段的集合判空，也没有完成其他权限比较、动态 NULL 参数、唯一值候选归一化及冲突重读的最终审计。

截至 16:44 的进度核对，剩余工作归并为四组（随后两项的进展见下节）：唯一值候选 CAST/LOB 与归一化；权限表达式、动态引用和 NULL 参数边界；冲突后当前读取与异常出口；综合回归、架构检查及交付记录。迁移文件新增、修改、删除均无。

### 唯一值候选、完整大文本读取与版本冲突提示

`EntityDataSqlProvider.selectFormUniqueCandidates` 不再执行 `LOWER(TRIM(CAST(... AS CHAR)))`。数据库预筛必须返回 Java 规则可能冲突的超集：

- 普通可打印 ASCII 使用显式 `[aA]` 字母对和逐字符转义，不依赖数据库 locale 的 LOWER。Unicode 和控制字符行全部纳入候选，再由既有 `FormUniqueRulePolicy` 执行 trim、ROOT 小写、条件唯一、忽略空值及完整值比较。中文等数据占比较高时返回的候选可能增加；这是保证不漏报的代价，尚未做吞吐压测。
- 正则结构由业务层固定生成，通过 VARCHAR 参数绑定；SQL 语法由 integration 的 `DatabaseQueryDialect.regularExpressionPredicate` 按产品提供。模式最多预筛 64 个 ASCII 字符，保持在 Oracle 的 512 字节上限内；只是放宽候选前缀，原始数据和最终比较不截断。新增稳定的 id 排序，保留逻辑删除和排除自身条件。
- TEXT 使用完整原生读取，与原有数字、布尔、日期的 Java 比较路径一致。未知、虚拟或不支持的字段类型在 SQL 前失败，不能猜成字符串。普通预检无锁；权威终检保留 gate 后的当前锁定读取。
- MyBatis 源码核对发现：动态 Map 的 Object 属性遇到厂商 CLOB 类时可能回退到 `getObject`。应用 `largeTextBindings` 为 Object/String 的 CLOB、NCLOB、LONGVARCHAR、LONGNVARCHAR 明确注册已有的 `PortableLargeTextTypeHandler`，在结果集关闭前读完并关闭字符流。处理器扫描也限制在这四类 JDBC 类型，不把任意 Object 或二进制值转成文本。integration 未增加执行依赖。

版本配置的当前配置/legacy 草稿，在首次 INSERT 唯一冲突和 CAS 更新失败后，共四个分支改用 `findCurrentRevisionForConflict`。该查询只读配置基表 revision，禁用缓存并使用已有方言读守卫，避免对 JOIN 文档投影加锁。MySQL 使用共享当前读取，避免重复 INSERT 后的共享锁升级死锁；抛出的业务冲突仍结束原事务。其他业务异常出口仍待最终审计。

2026-09-22 17:35 完成 clean 后合并定向回归：**27 个测试类、190 项通过，0 失败、0 错误、0 跳过**，包含 **52 个使用 MySQL 的数据库场景**。本阶段新增 12 项：

- `MySqlFormUniqueCandidatesDatabaseTest` 4 项：全部可打印 ASCII/trim 控制字符、Unicode 大小写、四类排序规则（含 Turkish）、参数及正则转义、NO_BACKSLASH_ESCAPES、长值前缀和尾部差异、条件唯一/空值/自身/删除行、六线程 gate 后旧快照下的唯一赢家。
- `MySqlVersionConflictReadDatabaseTest` 6 项：四种真实 INSERT/CAS 竞争、六线程重复插入后无锁升级、缓存及逻辑删除。独立连接真实提交赢家，验证冲突提示 revision=9，输家事务未覆盖配置或写入 release。
- `MySqlLargeTextMappingDatabaseTest` 2 项：真实 MySQL 长文本/NULL/标量和回滚；真实 MySQL 查询外加测试代理模拟 CLOB 元数据与对象，验证完整读取及字符流关闭。后者是驱动返回差异模拟，**不属于 Oracle/达梦等数据库实测**。

表单唯一规则、预检、占位/gate、既有实体查询、幂等异常恢复、版本投影与服务、应用装配、布尔绑定、类型化判空、架构边界继续通过。各轮有重叠，不累计。最终脚本与日志：`/private/tmp/run_flow_unique_version_lob_tests.py`、`/private/tmp/flow-unique-version-lob-tests.log`。中间两个新增测试夹具问题已修正：条件节点误用小写（`flow-form-unique-condition-fixture.log`）；Customizer 需要 MyBatis-Plus 配置而非普通 MyBatis 配置（`flow-unique-version-lob-fixture-compile.log`）。这些失败记录均保留，生产规则未为测试放宽。

语法依据：[Oracle POSIX 正则规则](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Multilingual-Regular-Expression-Syntax.html)、[Oracle 模式长度](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Pattern-matching-Conditions.html)、[MySQL 正则](https://dev.mysql.com/doc/refman/8.4/en/regexp.html)、[PostgreSQL 正则](https://www.postgresql.org/docs/17/functions-matching.html)、[达梦函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)、[OceanBase Oracle 模式匹配](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001378508)。PostgreSQL 正则要求可用的确定性排序规则；各产品仍需满足其正则能力与版本条件，其他六库按要求未做实库测试。

当前剩余：权限表达式与动态引用、未声明类型的 NULL 绑定、其余异常出口，以及最终综合回归和交付审计。迁移文件新增、修改、删除均无。

### 权限绑定、类型化比较与参数传递

受控 SQL 的用户属性和团队身份先完成绑定（17:52 的 125 项定向测试通过，含 21 项 MySQL 实库场景），随后普通权限规则、列表本人兜底及委托范围也接入同一查询参数容器。

- `PermissionSqlFragmentCompiler` 将 userId/username/deptId/orgId 作为 VARCHAR 参数；缺失属性保留 NULL。受众查询通过查询方言补齐 Oracle/达梦/OB Oracle 所需的 DUAL，实际 JDBC 执行仍在 entity。biz 表别名及列按产品引用，跳过字符串字面量；拒绝引号内占位符、未绑定问号和 `${...}`。配置中的其他 SQL 仍是目标数据库原生表达式，不伪装为跨产品通用 SQL。
- `PermissionSqlBuilder` 的本人/提交人/当前办理人/部门/部门树、状态限制、结构化字段与嵌套组均使用绑定参数。关系、受控 SQL、TEAM、待办和多名委托人共享唯一参数名，含 NULL 的旧键也不会被覆盖。扩展提供者增加接收共享参数容器的重载，兼容只返回固定 SQL 的既有实现。
- 数值、布尔、日期和时间比较按可信字段类型转换并显式绑定 INTEGER/BIGINT/DECIMAL/DATE/TIMESTAMP。拒绝整数溢出、小数转整数、无效日期、非 0/1 布尔及带时区却用于无时区字段的文本；不会依赖 MySQL 将坏值隐式变为零。配置校验与执行共用转换，损坏的 DENY 规则由引擎拒绝全部。
- LIKE 模式完整绑定，使用固定 `ESCAPE '!'` 转义 `!`、`%`、`_`；部门 ID 的通配字符不再扩大后代范围。字段标识符由查询方言引用，包含保留字列。IN 中的 NULL 保留三值逻辑。
- `EntityDataDynamicService` 的自定义列表计划现在同时传递 SQL 和参数；无用户或用户记录不存在时明确拒绝，不能借空创建人的历史数据获得访问。关系图授权和投影复制使用允许 NULL 的只读 Map；权限结果合并先检查所有键，NULL 也是已占用的值。

2026-09-22 18:31 完成 clean 后合并定向回归：**24 个测试类、169 项通过，0 失败、0 错误、0 跳过**，其中 **39 项 MySQL 实库场景**。新增 `MySqlStructuredPermissionDatabaseTest` 的 8 项覆盖身份及状态范围、两种 SQL 模式、保留字、特殊字符与长文本尾部包含、大整数/小数/日期精度、NULL IN/NOT IN、部门树边界、允许/拒绝/委托参数合并、自定义扩展、自定义列表参数传递和匿名访问。已有受控 SQL、类型判空、流程状态、待办、唯一值候选、分页与锁、关系图、范围方案及架构检查同时通过。测试集合有重叠，不累计为总用例数。

验证方式同时得到修正：当前 Connector/J 9.7.0 在连接建立后执行 `SET SESSION sql_mode`，可能仍用旧模式转义客户端预编译的文本参数，真实 `SELECT ?` 曾回传多余反斜杠；先前同事务第二轮查询可能命中 MyBatis 缓存，不能证明新模式有效。现在两种模式分别通过连接 URL 的 sessionVariables 建连，断言当前模式及参数回传，再执行实际查询。受控 SQL 与唯一值候选的旧测试也已改用新方式，本轮通过记录替代先前对第二种模式的间接证据。

新方式暴露并修复了正则参数的产品差异：NO_BACKSLASH_ESCAPES 下客户端驱动可能发送二进制字符串，MySQL REGEXP_LIKE 拒绝此参数。MySQL/OB MySQL 方言仅对模式参数作 UTF-8 字符转换，并显式采用 utf8mb4_bin，使其字符比较稳定且避免 CAST 与列同为 IMPLICIT 时出现混合排序规则错误；被比较的业务列不转换、不截断。该专有规则封装在 integration 的 MySQL 正则分支，业务 Mapper 仍只调用标准接口；原先待办和 Embed 的 18 处硬编码 COLLATE 未恢复。四种列排序规则（含 Turkish）及两种连接模式均完成实库复测。

最终脚本与日志：`/private/tmp/run_flow_structured_permission_tests.py`、`/private/tmp/flow-structured-permission-tests.log`。中间的预览断言、二进制模式参数、CAST 隐式排序规则失败分别保存在 `flow-structured-permission-preview-assertion.log`、`flow-structured-permission-binary-pattern.log`、`flow-structured-permission-pattern-collation.log`，没有删除边界用例或放宽授权范围来取得通过。

参考：[PostgreSQL LIKE ESCAPE](https://www.postgresql.org/docs/18/functions-matching.html)、[MySQL 正则拒绝二进制参数](https://dev.mysql.com/doc/refman/8.4/en/regexp.html)、[MySQL 字符类型转换](https://dev.mysql.com/doc/refman/8.4/en/cast-functions.html)、[Connector/J 连接属性](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html)。

当前仍未完成：非文本字段 CONTAINS 的显式类型转换、Oracle 系列 LOB 的等值/集合/有序比较与大参数绑定、虚拟多值字段规则边界、其余动态写入 NULL 类型、其他异常出口和最终综合验收。Oracle SQL 对 LOB 支持范围与普通字符列不同，依据 [Oracle LOB SQL 支持表](https://docs.oracle.com/en/database/oracle/oracle-database/19/adlob/SQL-semantics-and-LOBs.html)，不能用 MySQL TEXT 的通过结果代替这些实现。其他六库没有实库验证。迁移文件新增、修改、删除均无。

### NULL 参数绑定与任职时间边界

应用 `DatabaseMybatisConfiguration.nullParameterBindings` 将未声明类型的空参数默认值设为标准 SQL NULL，已有显式 JDBC 类型和数字布尔处理器仍优先。只装配应用 MyBatis 工厂，不修改独立 Flowable 工厂；integration 仍无执行依赖。通用 JDBC 写入器继续使用 Spring 的 NULL 元数据处理，没有另加猜测字段类型的分支。

- 流程级动作的空元素、根表单节点的空父节点、Embed 首屏游标和身份提供方的空 issuer 显式绑定 VARCHAR；发布配置的可选 releaseId、流程状态保留值、清空当前任务字段也明确类型。动态实体更新已有的 NULL 字面量路径和插入省略空值的语义保持不变。
- 任职区间不再用日期列与日期字符串/参数混合的 COALESCE。实库复现表明，旧表达式将 `2026-02-01 00:00:00.000000` 与 `2026-02-01 00:00:00` 按文本比较，误把相接区间判为重叠；直接比较日期列得到正确的不重叠结果。现在结束参数为空时绑定原最大时间，日期列直接比较时间参数；开放存量区间、微秒边界、排除自身及撤销/组织隔离保留。
- 单任职模式的重叠统计同步使用日期列直接比较和有类型的最大时间。该查询补齐 MyBatis script，组织类型切换检查使用普通注解可执行的 `<>`，避免将 XML 实体原样发送到数据库。两条检查均新增实库覆盖。

2026-09-23 06:06 完成 clean 后定向回归：**12 个测试类、69 项通过，0 失败、0 错误、0 跳过**，其中 **38 项 MySQL 实库场景**。新增 `MySqlNullBindingDatabaseTest` 的 8 项包括：动态 Map 与显式 JDBC 类型优先级；批量/Wrapper 清空及回滚；当前任务清空；JDBC 幂等插入与锁行初始化；空元素/父节点/游标的查询范围；空 issuer 查询及锁定；任职首尾相接、微秒重叠和开放边界；单任职模式的同组织重叠统计及组织类型过滤。测试代理只观察真实 PreparedStatement 的 setter，不能仅凭 MySQL 容忍 OTHER 就计为类型正确。

同轮保留应用实际工厂装配、数字布尔、权限绑定、实体动态 SQL、分页与行锁、任职服务以及架构边界回归。所有实库测试使用随机隔离表并清理；其他六库仍按用户要求没有实库测试。各轮集合有交叉，不累计为总数；最终综合验收仍未完成。

脚本与最终日志：`/private/tmp/run_flow_null_binding_tests.py`、`/private/tmp/flow-null-binding-tests.log`。旧源码断言与新夹具问题记录保存在 `flow-null-binding-annotation-assertion.log`、`flow-null-binding-fixture-visibility.log`、`flow-null-binding-initial-boundaries.log`；日期比较的实际诊断值保存在 `flow-null-binding-date-coercion.log`。没有通过放宽业务断言消除日期边界失败。

依据：[MyBatis-Plus NULL 绑定说明](https://baomidou.com/reference/question/)、[MyBatis jdbcTypeForNull 配置](https://mybatis.org/mybatis-3/configuration.html)、[MySQL 日期与字符串比较规则](https://dev.mysql.com/doc/refman/8.4/en/type-conversion.html)。

当前剩余分为三组实现与两组审计/验收：非文本字段 CONTAINS；Oracle 系列 LOB 比较和大参数绑定；虚拟多值字段规则；其他异常出口及遗漏 SQL 审计；最终综合回归与交付检查。迁移文件新增、修改、删除均无。

### 非文本包含查询与普通列表的字段类型

integration 增加 `DatabaseQueryDialect.patternValueExpression(column, SchemaType)`，只渲染可信列的文本匹配表达式。普通字符和大文本保持原列；MySQL/OB MySQL 对非文本列使用不指定长度的 CAST AS CHAR，保留原生 LIKE 的完整十进制、定点尾零和时间小数秒。PostgreSQL/Kingbase 的数值转 TEXT，日期/时间使用明确格式；Oracle/达梦/OB Oracle 的数值使用按精度生成的格式并固定数字分隔符，日期/时间不依赖默认日期格式。时间文本的小数秒位数仍随产品时间类型表示，本接口不做展示格式或尾零归一化；其他六库尚无实库证据，不能宣称所有格式细节已经一致。

`PermissionSqlBuilder` 保留完整 SchemaType，使小数精度不会在字段解析时丢失；CONTAINS/NOT_CONTAINS 统一使用方言表达式，LIKE 模式继续绑定并保留权限规则的字面量通配符语义。NULL 输入值的既有编译行为、SQL NULL 的三值逻辑和组内否定保持不变。

普通列表有独立的 LIKE 生成路径。`EntityQueryConditions` 用普通 Map 视图传递条件值，同时携带由发布字段构造的可信列元数据，元数据不作为请求参数键暴露。`EntityDataDynamicService` 的分页及不分页入口向全部六条条件查询/计数路径传递此对象；Provider 使用真实物理列名和类型，绑定键继续使用原字段编码。普通列表 LIKE 保留已有通配符语义。虚拟/独立多值字段没有主表列，普通字段入口不猜测其类型，多值 EXISTS 路径仍由原服务负责。旧的内部纯 Map Mapper 调用继续使用原行为，其他比较运算的类型化绑定和其余动态引用仍属于最后审计范围。

2026-09-23 06:22 完成 clean 定向回归：**12 个测试类、90 项通过，0 失败、0 错误、0 跳过**，其中 **32 项 MySQL 实库测试**。`MySqlScalarPatternDatabaseTest` 的 6 项中，5 项使用真实 MySQL，1 项为纯元数据检查（实库数已排除）。新增验证涵盖有符号整数极值、65 位定点数、30 位极小小数、尾零、零小数秒/微秒、两种连接 SQL 模式、注入样式及通配符参数、NULL 与删除行、嵌套权限、六条条件查询、发布字段到分页服务的传递，以及虚拟/未发布字段和伪造元数据的拒绝。普通权限、类型判空、唯一值候选、实体查询与架构边界同时通过。

脚本和最终日志：`/private/tmp/run_flow_scalar_pattern_tests.py`、`/private/tmp/flow-scalar-pattern-tests.log`；只包含权限入口的前一轮通过记录保留为 `flow-scalar-pattern-permission-pass.log`。MySQL 测试仍只操作随机隔离表。各轮不累计为总用例数，综合验收未完成。

语法依据：[Oracle 数值转换](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/TO_CHAR-number.html)、[PostgreSQL 格式化函数](https://www.postgresql.org/docs/18/functions-formatting.html)、[Kingbase 格式化函数](https://help.kingbase.com.cn/v8.6.7.24/development/sql-plsql/sql/Function.html)、[达梦格式化函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)、[OceanBase 数值转换](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001107844)。

后续继续处理 Oracle 系列 LOB 比较及大参数、虚拟多值权限规则、其他动态参数与异常出口、最终综合验收。优先复用 MyBatis/MyBatis-Plus 已有能力，额外方言仅覆盖框架不能自动处理的现有 SQL。迁移文件新增、修改、删除均无。

### Wrapper 查询与框架大文本处理器

按用户指出的实际查询收敛实现：

- `EntityListScopeDelegationMapper.findActiveByToUserId` 使用 LambdaQueryWrapper 构造用户、启用状态、全局/指定实体分组及字段投影，少量自定义 SQL 保留数据库时间谓词。该自定义入口显式添加 `deleted=0`，不依赖 BaseMapper 自动过滤；Wrapper 只在服务端构造，不接受请求 SQL。
- `EntityListScopeBindingMapper.findByEntityCode` 同样改为 LambdaQueryWrapper，创建时间排序由实体字段引用生成。已删除草稿的物理清理保留明确的 DELETE，避免与 BaseMapper 的逻辑删除语义混淆。
- 委托查询继续使用现有 `DatabaseRuntimeSql.currentNow(_databaseId)`，由当前工厂选择时间片段。审查发现固定 CURRENT_TIMESTAMP 会令 PostgreSQL 的长事务时间退化，因此已恢复 PostgreSQL/Kingbase 的 statement_timestamp；MySQL 的零小数秒和起止相等边界不变。七产品 SQL 渲染有契约覆盖，真实执行仍只验证 MySQL。
- 删除自定义 `PortableLargeTextTypeHandler`。应用配置为 CLOB、NCLOB、LONGVARCHAR、LONGNVARCHAR 分别注册框架的 ClobTypeHandler、NClobTypeHandler、StringTypeHandler、NStringTypeHandler；动态 Map 的 Object 字段使用相同处理器，业务层取得完整 String。未修改普通 Object、字符串或二进制映射。前述“唯一值候选、完整大文本读取”阶段的自定义字符流实现已由本节替代。
- 这里处理的是 Java/JDBC 类型映射；实际查询中的 LOB 比较能力仍按需求核对，不为 CLOB 再创建业务类型或自研读取框架。

验证覆盖全局/指定实体、NULL/空串、用户与逻辑删除隔离、禁用委托、开始/结束相等及一微秒偏差、三个数据库会话时区、特殊字符参数、绑定排序和物理草稿清理；字符串覆盖长中文、补充字符、尾部完整性、NULL、回滚和动态 Map 标量类型。模拟 LOB 元数据的场景同时提供对应国家字符读取接口，属于映射测试，不是 Oracle/达梦实库验证。

本节最初定向回归 54 项通过、无失败/错误/跳过（2026-09-23 06:39），该结果在后续全局 Mapper 替换之前。回归脚本：`/private/tmp/run_flow_framework_reuse_tests.py`；首次测试夹具的 NCLOB 接口差异保存在 `/private/tmp/flow-framework-reuse-national-character-fixture.log`。本轮 Flyway 迁移新增、修改、删除均无。

### 全系统 Mapper 优先使用框架

按用户明确要求，本轮检查全部 121 个 Mapper，不局限于截图中的实体列表。普通单表查询、计数、存在判断、排序、投影、MAX、条件更新和适合的删除已优先使用 Wrapper；现在 79 个 Mapper 使用 Wrapper，其余主要包含复杂查询或协议 SQL。

- 普通首行/列表分页使用 MP Page。`workflow-core` 的 `OffsetPage` 仅覆写 offset，支持原接口任意偏移及 limit=0，并关闭多余 COUNT；SQL 由 app 装配的官方分页插件生成。
- 动态实体和关系投影的 Provider 继续负责安全的动态表、字段、权限及业务条件，分页由新映射入口的 IPage 交给框架。复杂 JOIN 保留原 SQL；公共业务方法名及调用参数保持不变。
- 手工分页仅保留流程已办查询中 4 处用户身份子查询，以及 Embed 修复检查的 1 处内层聚合分页；外层分页插件不能替代这些内层限制。
- 保持逻辑删除与物理清理、包含已删除历史的读锁、唯一键锁、租约和版本 CAS。普通 CAS 仍是一个带旧版本条件的 UPDATE，显式 set 保持 NULL 清空行为。
- 权限集合的 DISTINCT 仍由数据库执行，避免 Java Set 改变大小写和重音字符的去重口径。跨表计数移到对应表的 Mapper，保留原有历史关联与组织 OR 分组范围。
- 子 agent 分实体、流程、管理/Embed/开放接口执行和交叉复核；由主 agent 统一编译、跑测试。测试改为调用真实 Mapper 默认方法，独立测试工厂使用生产分页插件，并补全 BaseMapper 全列投影所需的隔离测试表字段。

最终统一回归于 **2026-09-23 08:34** 完成：**578 个测试类、3664 项；3653 通过、7 失败、3 错误、1 原有跳过**。本轮新增的失败已全部消除。34 个 `MySql*DatabaseTest` 类合计 **189 项全部通过、无跳过**；其中包含大文本模拟元数据及纯元数据断言，不把该总数宣称为 189 个不同的真实数据库场景。分页契约 117 项、权限数据库行为 22 项、开放接口 Wrapper 4 项、模块数据库边界 3 项均通过。

剩余 10 项是此前已记录的既有问题：`EntityRelationDefinitionServiceTest` 保留运行时字段规则 1 项，`SchemaRequiredTablesTest` 历史迁移文本 2 项，`EntityFormNodeServiceTest` 旧组合规则 5 项，`EntityDataDynamicServiceSubFormTest` 流程完成状态 1 项，`ApprovedExpressionMigrationTest` 未批准表达式 1 项。`ProcessDefinitionServiceParseTest` 保留原有 1 项跳过。历史部署迁移测试、既有架构 PUT/DELETE 不一致、逻辑删除历史读锁不一致及要求先打包的制品测试不在本次运行时选择器内；完整排除名单保留在脚本中。未通过修改这些业务规则或不可变迁移来消除失败。

统一回归脚本：`/private/tmp/run_flow_wrapper_runtime_regression.py`；最终日志：`/private/tmp/flow-wrapper-runtime-regression.log`；上一轮适配失败日志：`/private/tmp/flow-wrapper-runtime-regression-round2.log`。脚本独立统计测试失败，Maven 的 BUILD SUCCESS（为收集全部失败而开启 failure.ignore）不代表全仓绿色。本轮 Flyway 迁移新增、修改、删除均为 **无**。

### Wrapper 收尾审计及补齐

以下五项已实现，统一回归记录见最终验收段。此前各阶段的“尚未完成”属于当时记录，以本节和顶部进度为准。

1. 普通 EQ/NE/GT/LT、IN/NOT_IN 与起止范围复用严格标量转换，按已发布字段元数据绑定整数、定点数、布尔、日期和时间；未指定操作符的字符串继续表示 LIKE。唯一值预检传入同一可信字段元数据，不改变原数据容器。
2. 多值权限按真实侧表查询，区分主表标量、引用/字典集合与无存储组件；目标实体、字段、逻辑删除条件同时生效。成员沿用现有 trim/去重协议。无效字典值保护整条规则，保留 SQL UNKNOWN，避免嵌套条件及 DENY 取反扩大权限；集合有序比较明确拒绝。
3. 大文本继续使用 Java String 和 MyBatis 内置 ClobTypeHandler。Oracle/DM/OB Oracle 的等值、集合和有序比较使用完整 DBMS_LOB.COMPARE，不截取前缀；其余产品保持原生比较。发布唯一值预检通过完整列间比较选取每组最小 ID，避免 LOB GROUP BY 及 MySQL max_sort_length 导致的错误分组；最多返回五组完整计数。该发布预检包含关联子查询，大表性能尚未压测。
4. 开放接口凭据最近使用时间改用标准单表 UPDATE + IN 子查询，保留应用与凭据双重 ACTIVE 条件。
5. 转办的引擎分配、原待办完成、新待办和审计记录属于同一事务；后两步失败不再吞错，缺失任务也明确失败。故障注入通过真实 MySQL 验证提交/回滚，Flowable 操作由同事务状态夹具模拟，不宣称运行了真实引擎集成场景。

最终审计另收敛了审计导出/时间线及运行期配置迁移的 9 处 Wrapper 手工分页，保持原先 10000/500/1 条限制和稳定排序，使用 MP Page 并关闭 COUNT。七产品字段修改的发布风险均按实际 DDL 模板识别为 HIGH，新增字段仍为 MEDIUM。121 个 Mapper 中 79 个使用 Wrapper；普通 Wrapper 不再通过 .last 拼分页。JDBC 与嵌套查询仍由方言描述必要的分页语法。

长文本语义依据：[Oracle DBMS_LOB](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_LOB.html)、[达梦 DBMS_LOB](https://eco.dameng.com/document/dm/zh-cn/pm/dbms_lob-package)、[MySQL max_sort_length](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html)。[OceanBase 发布说明](https://en.oceanbase.com/docs/common-oceanbase-database-10000000000870425)确认完整/部分 LOB COMPARE；OB Oracle 有序返回值目前按 Oracle 兼容契约实现，尚无独立文档和实库证明。七产品 SQL/绑定契约测试不等同非 MySQL 实库测试。

### 结构队列与重放

`QueuedSchemaDdlExecutor` 负责等待实体模块的队列端口；实际入队位于实体基础设施 `JdbcSchemaChangeQueue`，worker 与重放验收位于 db-migrator 的 `JdbcSchemaChangeWorker`/`SchemaDdlReplayVerifier`。历史迁移流程未更改。队列表结构和 `ddl_statement` 原始 SQL 格式保持不变。

- `active_hash` 的现有唯一约束负责活跃请求去重；完成后可重新提交相同 DDL。入队连接独立提交，不加入等待它的业务事务。
- worker 采用候选查询、带旧 token/状态/到期条件的原子 UPDATE 领取；JDBC `setMaxRows` 和稳定游标代替 MySQL `LIMIT ... FOR UPDATE SKIP LOCKED`。第一批被会话锁占用时继续扫描后续候选。
- 每个请求另持独立会话锁，避免租约过期但旧 DDL 仍执行时重叠重放。续租和 ACK 均校验 owner、token、RUNNING 及未过期租约。
- 达到重试上限的过期请求先检查是否只是 ACK 丢失；可以证明已落地则完成，否则失败并清除活跃去重键，不执行第六次 DDL。
- UTC 时钟在数据库侧读取，租约和退避时间在 Java 中计算再绑定；不依赖各应用节点时钟，不在业务 SQL 中拼接 `DATE_ADD`。
- 普通 DDL 限定业务表；非 MySQL 的审计程序必须匹配固定方言模板。PostgreSQL/Kingbase 的审计触发器按原计划的 DROP/CREATE 组合重试。
- 重放验证覆盖生成的建表、加列、建索引及删除，比较列顺序、类型/精度、默认值、可空性、索引列顺序/唯一性、注释及 MySQL 审计属性。`IF NOT EXISTS` 返回成功后也验收实际结构。
- 未识别语法或元数据不足时不证明成功；尤其非 MySQL 程序在最后一次尝试后丢失 ACK，仍可能保守进入失败，需后续实库核查。
- MySQL 时间小数精度与扩展列属性从 `information_schema` 读取；表达式文本默认值通过只读 `SHOW CREATE TABLE` 解析，避免 `COLUMN_DEFAULT` 的额外转义和中文表示问题，绝不执行元数据表达式。

时钟和元数据依据：[PostgreSQL 日期函数](https://www.postgresql.org/docs/17/functions-datetime.html)、[Kingbase 时钟函数](https://help.kingbase.com.cn/v8.6.7.12/development/develop-transfer/kes-vs-oracle/kes-vs-oracle-2.html)、[达梦 UTC 函数](https://eco.dameng.com/document/dm/zh-cn/pm/function)、[OceanBase UTC 函数](https://en.oceanbase.com/docs/common-oceanbase-database-10000000001784465)、[MySQL 列元数据](https://dev.mysql.com/doc/refman/8.0/en/information-schema-columns-table.html)、[MySQL 表达式转义问题](https://bugs.mysql.com/bug.php?id=104294)。

定向测试命令：

```sh
cd workflow-server
mvn -o -pl workflow-app -am \
  -Dtest=MySqlSchemaDdlDialectTest,DynamicTableServiceTest,DynamicTableServiceColumnDefinitionTest,DynamicTableServiceMultiValueTest,EntityRecordTeamServiceTest,JdbcSchemaDdlExecutorTest,EntitySchemaOperationTransactionIntegrationTest,SystemEntityCatalogServiceTest,ModulePackageLayoutTest,ContractPackageArchitectureTest,DatabaseAdapterBoundaryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

第一阶段共 39 项通过。第二阶段补充数据源装配、Flowable 配置、锁失败清理和真实 MySQL 会话竞争，合并回归共 52 项通过（零失败、零跳过）。原有 HTTP 架构规则失败仍单独记录，不属于通过集合。

第三阶段加入结构队列、重放校验及命名规则回归，合并 70 项通过（零失败、零跳过）。其中新增 6 个 MySQL 队列实库场景，使用随机队列表和目标表，不消费现有业务队列；覆盖并发去重/执行、错误结构、索引顺序、崩溃恢复、最后一次尝试、旧租约 ACK 和前一批被占用后的继续扫描。完整实体、团队和多值表同时验收重放。

移除显式排序规则后，`ProcessTaskMapperIdentitySqlTest`、`ProcessTaskMapperTodoScopeTest`、`CurrentProcessTaskAssigneeLookupTest`、`EmbedManagementMapperContractTest` 共 65 项通过。授权场景使用原有 H2 夹具直接执行当前 SQL，不再删除 COLLATE 后才测试；这一组不是 MySQL 实库结果。

### 版本配置 JSON 投影

`EntityVersionConfigMapper` 一次 JOIN 读取当前文档和所属 active release，`CurrentVersionDocumentProjection` 在 Java 中检查完整 JSON、应用 contract version 并移除根节点发布状态字段。没有新增逐行数据库查询。数据库 `(entity_code, deleted)` 唯一约束保证单条读取无需 `LIMIT 1`。

- 有效且归属当前配置的 release 优先，无效/不存在的 release 原样回退当前文档，legacy draft 不参与。
- 根对象的 schemaVersion 跟随 contract version（NULL 时为 1）；仅移除根节点 status/migrationState/activeReleaseId/activeReleaseVersion，嵌套同名字段保留。
- JSON null、数组、标量保留原语义；尾随垃圾与越界数值不能覆盖当前配置。超过 100 层仍明确失败，不静默使用旧配置。
- 单条、运行时列表和管理列表共享同一投影；运行时过滤 NULL 文档，管理列表保留占位行的 id/revision；源读取对象不被原地修改。

相关 44 项测试通过：41 项投影、Mapper 与版本服务/滚动升级桥测试，3 项真实 MySQL 测试。实库测试使用真实 MyBatis 映射与 Mapper 默认方法，对照原 MySQL JSON 表达式，验证无效 JSON、null、数组、标量、重复键、数值越界、超深文档、外部 release 和删除行隔离。测试通过随机表名隔离，不读取或修改真实配置数据。

语义参考：[MySQL JSON 修改函数](https://dev.mysql.com/doc/refman/8.0/en/json-modification-functions.html)、[Jackson 完整输入校验说明](https://github.com/FasterXML/jackson/discussions/143)。

真实 MySQL 测试：先设置 `FLOW_MYSQL_TEST_URL`、`FLOW_MYSQL_TEST_USER`、`FLOW_MYSQL_TEST_PASSWORD`，再执行：

```sh
cd workflow-server
mvn -o -pl workflow-app -am \
  -Dtest=MySqlDatabaseLockTest,MySqlDatabaseConfigurationTest,MySqlDatabaseLockDatabaseTest,EntitySchemaPublishLockTest,EntitySchemaPublishConnectionLockTest,FlowableDatabaseConfigurationTest,MySqlSchemaDdlDialectTest,MySqlDynamicSchemaDatabaseTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

动态表的两个实库场景覆盖：多种字段类型与默认值、中文和单引号/反斜杠、表注释、主键与索引、自动更新时间、字段添加/修改/删除、DECIMAL 精度比较、重复发布、多值唯一约束、团队表、最终删除。测试使用随机前缀表并清理，不修改现有业务表。缺少连接环境时实库测试明确跳过，跳过不计为实库通过。

另外两个锁实库场景验证：六线程竞争只有一个赢家；另一连接提交、回滚、执行 DDL 后仍不能取得锁；旧 SHA2 锁名与新实现相互排斥；释放后可再次获取。单元测试覆盖 NULL/异常/竞争时清理连接和释放失败时关闭物理会话。

扩大运行原有 `ArchitectureBoundaryTest` 时，`HTTP_APIS_ONLY_USE_GET_OR_POST` 在三个未修改的接口上失败：`EmbedSessionController.logout` 的 DELETE、`EntityVersionConfigurationController.save/saveCurrent` 的 PUT。记录为现有架构规则与实现不一致；没有为使测试变绿而修改无关接口。

扩大回归时遇到的另外两项范围外问题：

- 历史迁移 `V062__remove_entity_list_scope_inventory.sql` 在 Testcontainers MySQL 8.4 上报 1137 `Can't reopen table: 'own_root'`，使多组迁移测试无法继续。该文件与 HEAD 一致；停止这组重复迁移后，运行时回归排除迁移器根测试包的 26 类历史迁移测试，保留 `SchemaChangeWorkerTest`。
- `EntityRelationDefinitionServiceTest.rejectsReservedRuntimeDataKey` 用 `title` 验证保留键，但未修改的生产保留键集合包含 `name`、不包含 `title`；测试和服务都与 HEAD 一致。保留该失败，不改动无关字段语义。

最终扩大运行时检查共执行 **3564 项**，原次运行 **9 项断言失败、4 项错误、1 项跳过**；这不是全绿结果。该次使用继续收集模式让失败模块之后的测试也运行，外层脚本按失败数返回非零，不能把 Maven 的 BUILD SUCCESS 当作测试通过。

- 其中与本次有关的 2 项 `EmbedPersistenceSqlContractTest` 原本检查注解源文本中的 LIMIT/XML 比较符，已改为验证 MyBatis 实际渲染结果及参数映射。该类 **14 项复测全部通过**。
- 剩余 10 项失败来自本轮未修改的业务/迁移代码与测试：关系保留键 1 项；`SchemaRequiredTablesTest` 的迁移文本断言 2 项；`EntityFormNodeServiceTest` 旧子表单兼容预期与当前组成关系校验冲突 5 项；`EntityDataDynamicServiceSubFormTest` 的 BACKLOG/APPROVED 状态预期 1 项；`ApprovedExpressionMigrationTest` 的非 approved 表达式保留预期 1 项。已核对相应失败路径文件无本轮差异，没有为消除失败而改动这些业务规则。
- 另 1 项 `ProductionArtifactPackagingIT` 因通配选择器在 test 阶段提前选择打包检查而失败，缺少 `target/workflow-server-1.0.0.jar`。它需要 package 后执行，不计为数据库适配失败，也未计入通过集合。
- 1 项既有 `ProcessDefinitionServiceParseTest` 跳过。原已排除的 HTTP 架构规则、逻辑删除规则及历史迁移测试仍不属于通过集合。

本轮直接改动范围的两个定向集合（451 项与 145 项）均零失败、零跳过，后续 Embed 的 14 项复测也通过；各集合存在交叉，不相加作为累计用例数。尚未完成的锁定分页、批量写入、幂等、其他日期转换和通用异常适配继续保持待办状态。

本次新增、修改、删除的 Flyway 迁移文件：均无。
