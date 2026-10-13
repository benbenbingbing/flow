# OceanBase MySQL 模式实库验证（2026-10-13）

## 结论与验证范围

本次直接使用 `docs/sql/obmysql.sql` 初始化本机 Docker OceanBase，再运行当前工作区后端、生产 Mapper 和 JDBC Repository。验证对象是 `OceanBase_CE 4.4.2.1`，协议版本 `5.7.25-OceanBase_CE-v4.4.2.1`；使用项目自带 MySQL Connector/J 和显式 `OCEANBASE_MYSQL` 方言。Flyway 历史迁移不是本次兼容判断依据。

已同步初始化结构，并修复实测发现的锁、元数据和 Mapper 映射问题。合法字段配置下，应用启动、实体发布、表单发布、Excel 配置读写及 Flowable 基础执行可以完成。TEXT/LONGTEXT 非 NULL 数据库默认值仍是当前 OB 的能力限制，现在在发布前给出 HTTP 400 配置提示，不再发送不兼容 DDL，也不静默改变字段类型或默认值。

覆盖以真实执行记录为准，不将缓存命中、只生成 SQL、约束拒绝或空集合提前返回算作 SQL 执行成功；也不把 Mapper SQL 冒烟验证等同所有动态分支和全部业务流程均已覆盖。

## 环境隔离与切换配置

- 未修改 `.env`，未停止或重启用户原有开发服务。
- 验证后端使用 `18081`，监控端口 `19091`；所有导入、写入、并发和队列验证只操作独立 `flow_ob_*_20261013` 库。完成后已关闭验证实例，确认两个测试端口均释放，Docker保持运行。
- 验证账号仅获独立库权限，测试密码/令牌不入库。仅以 root 读取版本/创建验证库及复核随机命名锁问题。
- 最终应用关闭 Flyway 与 Flowable 自动更新结构，确认初始化脚本本身可满足启动；启用后台调度进行观察。
- 最终应用 JDBC URL 没有添加 `useInformationSchema=true`，用以证明元数据修复不依赖额外驱动参数。

```text
WORKFLOW_DATABASE_VENDOR=oceanbase-mysql
SPRING_FLYWAY_ENABLED=false
FLOWABLE_SCHEMA_UPDATE=false
DB_TRANSACTION_ISOLATION=TRANSACTION_READ_COMMITTED
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:2881/<database>?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true
DB_USERNAME=<application-user>
DB_PASSWORD=<application-password>
SCHEMA_DATASOURCE_URL=<同库的结构发布连接地址>
SCHEMA_DB_USERNAME=<schema-user>
SCHEMA_DB_PASSWORD=<非空结构账号密码>
```

结构数据源使用同类连接参数及独立授权；默认 direct 发布模式必须配置非空结构密码。新事务锁使用业务身份的独立连接，已有数据库须先建立 `sys_database_lock` 并给业务账号授予 INSERT、SELECT，不能只给结构账号授权。不能只改地址而保留 `vendor=auto`：`jdbc:mysql:` 会选择普通 MySQL 方言，可能生成本机 OB 不支持的 `FOR SHARE`；OB 专属分支已经使用 `LOCK IN SHARE MODE`。

## 初始化脚本同步

原脚本能导入，但只有187表，与近期代码不同步；实测发布表单时报缺少 `entity_form_publication_lock`。

本次更新：

- 增加 `entity_form_published`、`entity_form_publication_lock`、`entity_form_current_default` 及发布锁固定种子。
- 增加5张 `entity_excel_*` 配置、发布和任务表。
- 补人员交接菜单及超级管理员授权。
- 加签人员组合索引改为普通索引，保留生成任务ID唯一约束。
- 删除已退役的3张 hotfix 表和相关权限种子；保留仍被代码使用的审计字段。
- 增加 `sys_database_lock`，用于 OB 专用事务互斥。

最终脚本在全新 `flow_ob_final_20261013` 导入：**193张表、99个外键、零SQL错误**。表名逐一匹配；当前94个 `@TableName` 均存在；新增发布/Excel表的类型、NULL、默认值、主键及索引与当前结构逐项一致。交接授权、发布锁种子、退役数据清理均核验通过。

## 修复及回归证据

| 问题 | 处理及结果 |
| --- | --- |
| OB 并发 GET_LOCK 返回多个成功者 | 改为独立连接中未提交唯一键 INSERT 仲裁，NO WAIT，释放时回滚并关闭；20轮×8连接均严格1赢家，竞争者不等释放便返回，业务提交/回滚/DDL不释放锁，关闭后可重获，持久锁标记为0 |
| Connector/J 丢失表注释 REMARKS | 仅 OB MySQL 的表列表改读当前库 information_schema；强制 useInformationSchema=false 仍正确返回中文/引号/反斜杠注释，CREATE验收通过，真实注释不一致仍被拒绝 |
| TEXT/LONGTEXT 非 NULL DEFAULT 被数据库拒绝 | 在生成完整发布计划前校验；支持普通VARCHAR默认值、无非空默认值的大文本，不再半途执行失败或静默丢值 |
| SysOrganizationMapper.selectParentsByPath 没有绑定语句 | 改为Java拆分路径、绑定IN参数并恢复路径顺序；保留逻辑删除过滤，空路径不生成非法IN或全表读取 |
| Excel 清理截止时间被 JVM/JDBC 时区二次转换 | 改为与作业写入一致的 UTC LocalDateTime 绑定；原来25小时任务未过期的问题已修复，Asia/Shanghai、UTC、America/New_York 均验证25小时应过期/23小时应保留 |
| OB RR 序列化冲突6235被归为未知SQL | 在OB MySQL错误方言中分类为事务并发失败，保留原始异常，不当作唯一键成功；不修改其他产品相同数字错误码含义 |

GET_LOCK 问题用无 Spring/MyBatis 的6连接 JDBC 屏障最小复现，并在测试账号及root均重复出现，因此不是框架代理造成的误报。该结论仅针对本次版本实例。

新锁有**24小时事务/空闲上限**，释放时检查标记；这不是执行过程中的 fencing。任务必须在上限内完成。升级到新OB锁实现时，必须先停止旧 GET_LOCK 发布器及 worker，再启动新版本，两种锁机制不能混跑。MySQL 保留原有命名锁行为。

显式 `REPEATABLE_READ` 的旧快照并发场景可能返回6235，这是与MySQL当前读不同的条件风险；本次使用默认 `READ_COMMITTED`。错误分类修复不意味着已经实现RR下所有完整业务事务的自动重试。

## 实际应用与引擎执行

最终独立构建JAR重新启动后，再次验证登录、用户/权限、实体/流程/表单列表、当前表单运行配置及Excel配置读取，全部成功，启动和请求日志零错误。

使用真实认证后的接口验证：登录、当前用户/权限、实体和流程列表；创建实体及动态表发布；创建表单、草稿读取、覆盖发布、当前运行配置读取；Excel方案生成、保存、发布、默认方案配置保存/发布及引用查询。初始化脚本补齐后，表单发布由缺表失败变为成功。

新增 Excel JdbcTemplate 路径另跑26项实库回归（22项作业事务/文件处理、3项文件仓储、1项三时区边界），全部通过；覆盖幂等重试、失败回滚、错误明细分页、真实XLSX解析/导出、任务过期与文件登记删除。权限、运行计划解析和实体写入入口部分使用测试替身，因此不等同完整HTTP导入/导出业务验收。清理修复前存在“25小时任务仍未过期”的2项失败，修复后均通过。

Flowable 7.2.0 在禁用schema更新的条件下，完成 `start → admin userTask → end` 的部署、启动、待办查询、中文变量读回、任务完成与布尔变量更新；确认运行实例/待办均归零，流程/任务历史存在且已结束。它验证的是引擎闭环，不代替所有审批监听器、会签、嵌入和外部集成场景。

最终在独立临时目录完整编译全部20个Maven模块并打包，32项定向单元/接口/实库测试全部通过（零失败、零错误、零跳过）。独立目录避免本机IDE生成的临时编译产物干扰结果；未清理用户工作区target。新增OB实库回归分别覆盖生产Mapper执行、组织路径/发布配置/游标数据断言、元数据与独立事务锁。最终 Mapper 扫描为 **119个 Mapper、694个不同 MappedStatement、996次真实 JDBC 执行全部成功，0次拒绝**。嵌入授权、启动、会话、回执和租约的12条 INSERT 均构造了完整合法依赖，检查写入、读回和新连接中的回滚结果；没有约束失败豁免。逐项口径见 [Mapper执行覆盖清单](obmysql-mapper-coverage-2026-10-13.md)。

修复后重跑6个结构发布队列场景及2个动态结构场景，8项全部通过，包含并发生产/消费、失效租约、不重复执行DDL、重放校验、全部存储类型与增改删列。大文本合法配置不设置非空数据库默认值；拒绝路径由专用测试验证。探索阶段205个旧实库方法的红色结果已区分过期fixture、RR条件与真实兼容问题，没有将其直接当成205个最终通过用例。

## 可重复执行

预先创建本机 `flow_ob_mapper_*` 独立库，按脚本说明设置随机 `@INIT_SIGNING_KEY` 后导入 `docs/sql/obmysql.sql`。凭据通过环境传入，不写入代码：

```bash
export FLOW_OBMYSQL_TEST_URL='jdbc:mysql://127.0.0.1:2881/flow_ob_mapper_check?serverTimezone=UTC&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true'
export FLOW_OBMYSQL_TEST_USER='<test-user>'
export FLOW_OBMYSQL_TEST_PASSWORD='<test-password>'
cd workflow-server
mvn -pl workflow-app -am test \
  -Dtest=OceanBaseMapperDatabaseTest,OceanBaseSchemaMetadataDatabaseTest,OceanBaseDatabaseLockDatabaseTest,EntityExcelJobCleanupTimezoneDatabaseTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Mapper测试严格限制本机 `flow_ob_mapper_*` 库，显式使用 JDBC 事务并强制回滚；生成的动态探测表会清理。需要完整授权对象的 INSERT 使用专用合法依赖样本，不关闭外键/CHECK，也不豁免数据库异常。默认不设置上述环境时，这些实库测试跳过。

本机原始证据位于 `/tmp/flow-ob-validation/`，包括初始化导入、HTTP、命名锁最小复现、修复回归及生产MappedStatement执行记录。本报告及仓库证据不保存测试凭据；临时目录中的本机凭据文件不得提交。

## 迁移清单

- 新增：`workflow-server/workflow-db-migrator/src/main/resources/db/migration/V116__oceanbase_transaction_mutex.sql`，只增加运行时事务锁表，便于已有数据库部署新代码。
- 修改或删除历史版本迁移：无。
- 同步独立空库初始化脚本：`docs/sql/obmysql.sql`；未建立Flyway B基线、未执行repair。
