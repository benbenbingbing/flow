# OceanBase MySQL 模式实库验证（2026-10-13）

验证对象为当前工作区代码，以及本机 Docker `oceanbase-ce` 中的 `OceanBase_CE 4.4.2.1`（MySQL 协议版本 `5.7.25-OceanBase_CE-v4.4.2.1`）。本次按 `docs/sql/obmysql.sql` 空库初始化路径验证，不以 Flyway 历史迁移是否通过作为切换结论。

结论：应用能够启动，常用读写和 Flowable 基础执行能够工作；当前实例仍有并发命名锁、TEXT 默认值等运行兼容问题，不能认定可直接完整替换 MySQL。

## 隔离方式与配置

- 未修改本机 `.env`，未停止或重启已有开发服务。
- 验证应用使用业务端口 `18081`、监控端口 `19091`。
- 使用独立测试库和仅获测试库权限的 `flow_ob_check` 账号；密码和验证登录令牌只保存在本机临时目录，不进入仓库。
- 明确设置 `WORKFLOW_DATABASE_VENDOR=oceanbase-mysql`、`SPRING_FLYWAY_ENABLED=false`，使用项目自带 MySQL Connector/J 连接 `127.0.0.1:2881`。
- 业务池维持项目默认 `READ_COMMITTED`。首次启动关闭后台调度，以便逐项隔离业务验证。
- Maven 对当前源码完整打包成功。未将已有大量工作区改动回退或替换。

建议连接参数（业务和结构数据源均配置）：

```text
jdbc:mysql://127.0.0.1:2881/<database>?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true&useInformationSchema=true
```

`useInformationSchema=true` 用于解决本次实测的表注释元数据问题；它不能解决命名锁或 TEXT 默认值问题。若只改连接地址而保留 `vendor=auto`，`jdbc:mysql:` 会被识别为普通 MySQL，部分读锁生成 `FOR SHARE`，本机 OceanBase 不支持该语法。必须显式选择 `oceanbase-mysql`，才能使用已适配的 `LOCK IN SHARE MODE`。

## 初始化脚本与应用接口

修改前脚本成功导入，187 张表、100 个外键，无 SQL 错误；但与当前应用结构不同步。缺少当前表单发布的三张表和 Excel 的五张表，另缺人员交接菜单及加签索引调整，并残留退役 hotfix 表。

初始应用验证库额外导入了现有 Excel 五表结构，因此这轮应用启动不能算“原脚本单独导入后全部功能可用”。在仍缺表单发布表的情况下，实际 HTTP 结果如下：

| 操作 | 实测结果 |
| --- | --- |
| 登录、当前用户、权限读取 | 成功 |
| 实体分页、流程分页、表单列表 | 成功 |
| 创建普通实体、发布动态表 | 成功 |
| 创建表单、读取草稿 | 成功 |
| 发布表单 | 业务响应 `code=500`；缺 `entity_form_publication_lock` |
| 发布带非空默认值的 TEXT 字段 | 业务响应 `code=500`；`Invalid default value for 'note'` |

注意接口错误由业务响应体表达，上述失败请求的 HTTP 状态仍为 200，不能只检查 HTTP 状态码。

## 已确认的运行兼容问题

### 并发 GET_LOCK 没有维持单一持有者

六个独立 JDBC 连接同时执行 `GET_LOCK(同一随机名称, 0)`，所有连接在取得返回值后等待同一个屏障，确保测试期间不会提前释放锁。十轮中八轮出现多个连接同时返回 `1`；首轮六个连接均返回 `1`。`IS_USED_LOCK` 只指向其中一个连接，其余“持有者”释放时返回 `0`。测试连接为 `READ-COMMITTED`。

这是本机当前版本实例的实测结果，不外推至所有 OceanBase 版本/租户。生产 `StandardDatabaseRuntimeDialect.lockPlan` 对 OB MySQL 复用 `GET_LOCK`，`JdbcDatabaseLock` 将返回 `1` 作为进入临界区的依据。因此实体发布、结构任务 worker 和依赖该锁的清理任务存在并发互斥风险；仅验证单连接获取/释放锁会漏掉这个问题。

应在目标生产版本和租户复测，并为 OB 采用经过并发验证的互斥实现，或确认所用版本已修复该行为；不能只吞掉释放异常。

### TEXT / LONGTEXT 非空默认值不兼容

生产 `OceanBaseMySqlSchemaDdlDialect` 继承 MySQL 方言，生成 `TEXT DEFAULT ('value')`。实测 TEXT 和 LONGTEXT 的括号表达式默认值、普通字符串默认值均被拒绝；`DEFAULT NULL` 可用，VARCHAR 默认值可用。

真实实体发布接口已复现该错误。需要在 OB 字段发布校验/方言中明确处理非空长文本默认值，或在写入业务层落实等效语义，不能静默丢弃默认值。

### JDBC 表注释读取影响结构队列验收

表实际创建成功，`information_schema.TABLES.TABLE_COMMENT` 正确，但默认 Connector/J 元数据路径的 `DatabaseMetaData.getTables().REMARKS` 返回空串。`JdbcSchemaMetadata.tables()` 依赖该字段，导致 `SchemaDdlReplayVerifier` 将有注释的 CREATE TABLE 判为未正确执行，结构队列任务无法正常确认。

本次实测在 JDBC URL 加 `useInformationSchema=true` 后，表注释及 CREATE TABLE 验收通过。列、索引、字符集及存储引擎并不是这一失败的原因。

### 非默认 REPEATABLE_READ 的条件风险

显式使用 `REPEATABLE_READ`、先建立旧快照再参与并发更新的测试，出现 `SQLState=25000 / error=6235 / can't serialize access for this transaction`。当前 OB 错误分类没有收录 6235，部分路径表现为 `UncategorizedSQLException`。

该结果不能直接推导默认 `READ_COMMITTED` 的业务路径失败。若部署改为 RR，需要补充事务重试、错误映射和冲突结果读取验证。

## 通过的代表性执行路径

- 生产 Mapper 的 CRUD、分页、排序、权限绑定、多值关联、NULL/布尔/时间值处理等大量实库回归。
- `REGEXP_LIKE`、中文正则、`JSON_TABLE`、`UTC_TIMESTAMP(6)`、`TIMESTAMPADD`。
- `UPDATE/DELETE ... ORDER BY ... LIMIT`、幂等插入、行锁及 OB 方言共享锁。
- Flowable 7.2.0：不更新表结构，部署 `start → admin userTask → end`，启动实例，查询待办，读写中文/布尔变量，完成任务，确认运行实例/任务归零以及流程/任务历史完整。

Flowable 验证为引擎基础闭环，不代表完整应用的所有表单、审批监听器、会签/加签、嵌入和外部集成都已验证。

## 证据和边界

本机过程证据位于 `/tmp/flow-ob-validation/`，包括 `app-start.log`、`http-business.log`、`http-text-default.log`、`probes-suite.log`、`probes-features.log`、`probes-named-lock.log` 和 `flowable-smoke.log`。测试代码及随机独立表放在临时验证目录/库，未替换项目原测试。

真实回归是从现有数据库测试创建临时副本，选择 OB 方言后运行。旧测试中的缺表 fixture、写死 MySQL 的对照 SQL、已经变化的业务断言，以及显式 RR 条件测试必须单独分类，不能把所有红色用例计作 OB 语法不兼容。

本次不会用“可以启动”或“多数用例通过”宣称完整生产兼容；并发命名锁问题仍然影响直接切换的判断。

迁移文件：本次未新增、修改或删除任何 Flyway 迁移。工作区原已有未跟踪的 V115，与本次验证无关；这里只同步独立初始化 SQL。
