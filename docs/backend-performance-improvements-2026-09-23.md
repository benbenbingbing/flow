# 后端性能改进跟踪

范围：按评审第 2～10 项实施；第 1 项任务列表保持现有行为。工作区有其他任务的修改，本任务在现有实现上增量调整。

| 项目 | 验收要求 | 状态 |
| --- | --- | --- |
| 2 导出 | 选中 ID、条件和权限共同下推；全部导出有界分批与流式输出、稳定顺序 | 已实现并通过回归 |
| 3 虚拟列 | 仅展示；保存、发布和请求拒绝虚拟筛选/排序；删除内存分页；模板与设计器一致 | 已实现并通过回归 |
| 4 扩展缓存 | 容量/TTL 上限、隔离键摘要、关闭时跳过构键、同键加载合并 | 已实现并通过回归 |
| 5 同步 REST | 总执行预算、重试共享剩余时间、按目标限制并发/故障隔离，保留同步与幂等语义 | 已实现并通过专项与相关回归 |
| 6 扩展超时 | 专用有界执行器、过期队列拒绝执行、底层超时与协作取消、容量可恢复 | 已实现并通过专项与相关回归；自定义阻塞代码须遵守协作取消契约 |
| 7 循环查库 | 历史详情和当前页权限批量加载，保留身份/变量作用域及提交时鉴权 | 已实现并通过完整后端验证 |
| 8 任务统计 | 数据库聚合，不读取已办全集，保留空时长计入分母及空集为零的口径 | 已实现并通过回归 |
| 9 HTTP 连接 | 有界复用和连接清理，保留逐次地址审查、固定 DNS、重定向禁用及策略隔离 | 已实现并通过专项与相关回归 |
| 10 保留期清理 | 稳定有界批次、独立事务、每轮限额、多实例协调 | 已实现并通过回归；V106 补充 Outbox 清理索引，MySQL 执行计划已验证 |

## 已实施行为

- 导出每批最多 200 行，不重复 COUNT；使用排序值与主键组成的游标，避免深 OFFSET。选中导出仍最多 5000 行，ID 参数按最多 500 项的 IN 组进入同一 SQL，与已有条件及权限求交。选中结果全部通过行权限校验后才写响应。
- 全部导出逐批写 CSV，保留列表配置排序，支持相同排序值及数据库默认 NULL 顺序。此接口是实时分批读取，不承诺导出期间发生修改时的数据库一致性快照；中途失败可能中止下载。没有新增后台导出任务系统。
- ENTITY_FIELD 之外的扩展、虚拟身份及接口覆盖列只能展示。旧发布版含虚拟查询标记时明确报错；管理员在设计器取消查询，修正固定条件、页面参数和排序后重新发布。使用固定旧发布版本的入口需切换到修正后的版本，不能直接改写历史快照。
- 缓存最多 512 条，按序列化长度加对象膨胀余量估重，权重上限 16 MiB；最多合并 128 个不同在途键。每 30 秒回收过期项，过大或无法估重的返回值不缓存。缓存键保留全部原有隔离维度后取 SHA-256。
- REST 节点的 timeout 作为全部尝试的总预算，退避和重试共享剩余时间；同步执行、幂等键和 throw/continue/ignore 语义保留。设计器已说明总超时含义，实际预算仍受系统上限限制。
- 扩展默认使用独立 8 线程、32 排队容量，队列饱和快速失败，等待超时取消并移除排队任务。预算传到 Provider SPI、平台 HTTP 和 MyBatis 语句，工作线程退出后清除用户/截止时间上下文；同键缓存等待也受调用方父预算限制，但等待方超时不取消共享加载。
- 平台 HTTP 默认最多 64 个在途请求、每目标 8 个，最多缓存 32 个按地址集合/白名单/私网策略隔离的连接池；空闲 60 秒回收、连接 TTL 300 秒。每次调用重新审批 DNS，DNS 使用 4 线程/32 队列隔离。截止取消覆盖慢速分块响应，超大响应中止读取，不为复用而排空内容。
- 按目标连续 5 次网络或服务端失败后熔断 30 秒，冷却后只允许一个探测；本地容量拒绝不再次重试。资源由 Spring 关闭生命周期释放，手工构造的传输或 REST 委托需要 close。
- 当前页审批 ID/名称及办理人身份按最多 100 组记录批量查询，复用 `TODO_USER_SCOPE`。同一记录的任务 ID/名称取同一条最新可办理任务，实际办理人身份取匹配任务的并集；实体和流程坐标同时存在时联合约束。列表展示使用惰性作用域复用，未用到任务规则时不查库；作用域结束即释放，精确审批上下文及提交仍实时鉴权。
- 流程历史详情按实例一次读取历史任务、活动任务和候选关系、变量、评论、本地任务动作；任务局部、执行局部、流程根变量分别索引，保留会签与重复节点隔离、评论类型和最新记录语义。用户名按 100 个键分批，候选组按 200 个键分批；原始查询键随结果返回，保留数据库排序规则及用户名优先于同名 ID 的兼容行为。
- 首页已办数量和总时长使用同一次聚合；NULL 时长计入数量，保留平均毫秒取整后换算小时的原有行为。
- 审计和 Outbox 每批默认 1000 行、每轮最多 10000 行、10 秒预算；按时间/主键选择 ID，每批 REQUIRES_NEW 事务，复用 DatabaseLockPort 协调不同实例。Outbox 删除时再次校验 PROCESSED 和保留期。

清理参数分别使用 `workflow.audit.retention-batch-size / retention-max-rows / retention-max-seconds` 和 `workflow.outbox` 下同名参数。单批硬上限 1000，单轮时间硬上限 60 秒；在批次边界停止，单批事务同时设置剩余超时。

扩展线程池参数为 `workflow.ui-extension.execution-threads / execution-queue-capacity`（硬上限 64/256），见 `config/runtime.yml`。HTTP 并发、连接池、TTL 和熔断参数见 `config/integration.yml`，均有硬上限。平台预算约束不能强行终止任意第三方 Java 代码；自建 HTTP/JDBC 客户端和长循环应实现 `UiDataSourceProvider` 的五参数入口，使用 `ExecutionControl` 传递剩余时间并注册取消。旧四参数实现仍兼容，但必须响应线程中断；不响应取消的自定义代码仍会占用其专用池，满载后快速拒绝。系统 DNS/驱动取消不可中断时也只能隔离容量并依赖底层超时，不能宣称 JVM 可以安全杀死线程。

## 验证与迁移

2026-09-23：相关后端 19 个测试类、214 个测试通过，包含真实 H2 聚合查询、MyBatis 导出与分页、独立清理事务、缓存并发/淘汰、发布和事件链路回归。导出 SQL 分别以 H2 MySQL/PostgreSQL 兼容模式验证升降序、重复排序值、NULL、ID/条件/权限交集；不将兼容模式测试当作生产数据库压测。

共享工作区曾出现不一致的编译产物，因此最终在 `/tmp/flow-performance-verification/workflow-server` 从当前源码重新构建。验证完成后对 2391 个后端文件比较 SHA-256，与当前工作区无差异。日志：`/tmp/flow-performance-isolated-tests.log`。

前端列表查询策略、列模板复制和开发指南测试通过，`npm run build:admin` 通过；构建保留已有动态/静态导入混用提示。`git diff --check` 通过。

2026-09-24：第 5、6、9 项完成本机 HTTP 集成、线程池/队列恢复、JDBC 取消回调、缓存等待预算和架构边界验证。真实 HTTP 测试覆盖同一连接连续复用、逐次 DNS 审批、严格策略拒绝已暖池的私网目标、单连接慢响应取消后恢复、DNS 超时恢复、REST 重试的总时限与幂等键。JDBC 取消使用阻塞 Statement 替身验证，未将它当作所有生产驱动的性能证明。

第 5、6、9 项合并回归为 31 个测试类、318 个测试全部通过，日志为 `/tmp/flow-performance-expanded-tests.log`，源码隔离目录仍为 `/tmp/flow-performance-verification/workflow-server`；当次运行后核对 2398 个后端文件摘要与工作区一致。前端 `npm run build:admin` 再次通过，保留上述既有导入提示。

列表能力批量查询的专项回归日志为 `/tmp/flow-performance-batch-capability-tests.log`，覆盖真实 H2/MyBatis 身份范围、记录/实例联合约束、实时认领/用户停用、候选人与办理人区分、展示作用域退出后重新查询、批次划分与列表集成；8 个测试类、65 个测试全部通过，运行后核对 2401 个后端源码文件摘要一致。

## 最终功能核对（2026-09-24）

| 项目 | 当前实现与验收证据 |
| --- | --- |
| 2 导出 | `EntityDataExportService` / `EntityDataSqlProvider` 的选中 ID 与权限/条件求交、200 行游标分批及流式输出；`EntityExportBatchDatabaseTest`、`EntityDataExportServiceTest` 验证顺序、NULL、权限交集与批次。 |
| 3 虚拟列 | `EntityListQueryPolicy` 在保存、发布和运行时拒绝虚拟筛选/排序；列表 SQL 分页后补展示值。`EntityListQueryPolicyTest`、列表配置/发布测试，以及前端 `list-query-policy`、`list-column-template`、`list-field-extension-guide` 测试通过。 |
| 4 缓存 | `UiExtensionResultCache` 有界容量/权重/TTL、合并同键加载、隔离摘要键；缓存并发、失效、淘汰及关闭分支测试通过。 |
| 5 REST | `RestServiceTaskDelegate` 使用共享总预算，重试保留幂等键与同步失败策略；HTTP 重试、退避和总时限测试通过。 |
| 6 扩展超时 | `UiExtensionExecutionConfiguration`、`ExecutionDeadline`、`ExecutionDeadlineInterceptor` 和 Provider 新入口覆盖专用池、队列取消、HTTP/JDBC 预算、线程上下文清理；旧四参数 Provider 由真实默认入口调用的兼容回归通过。 |
| 7 批量读取 | `CurrentProcessTaskAssigneeLookup` 请求内批量能力作用域及 `ProcessProgressReadBatch`；列表权限真实 SQL/新鲜鉴权、40 节点回归和真实 Flowable 会签变量/评论/候选组测试通过。 |
| 8 聚合 | `ProcessTaskMapper.aggregateDoneByUser`；H2 实库测试验证空值、数量和平均耗时口径。 |
| 9 连接复用 | `PinnedHttpResources` / `PinnedHttpTransport`；真实本机 HTTP 测试验证连接复用、逐次地址审查、策略隔离、慢响应取消、限流与熔断恢复。 |
| 10 清理 | `BoundedRetentionRunner` 独立批次事务、跨实例锁和预算；实库测试验证保护条件、回滚与迁移。V106 在独立 MySQL 8.0.27 执行，10 万行合成样本中清理查询改为覆盖索引范围扫描；审计已有索引足够。详见 [执行计划](testing/backend-retention-index-2026-09-24.md)。 |

`mvn verify` 在隔离目录完整通过，包括打包架构集成测试：651 个测试类，3974 项测试，0 失败/错误，273 项因未配置显式数据库测试环境或 Docker 不可用而跳过（其中 208 项要求 FLOW_MYSQL_TEST_URL，47 项要求 Docker）。运行后比对 2406 个后端源文件的 SHA-256，与共享工作区一致。日志 `/tmp/flow-performance-full-verify.log`。共享目录的编译产物未清理或覆盖。

前端 Node 22.22.1：隔离目录 `npm ci --offline --no-audit --no-fund` 和 workspace 包构建通过；共享源码的 `npm run build`（管理端及嵌入端）通过。`npm test` 被既有用户组列宽断言中断，继续逐项执行全部 114 个测试命令，108 个通过、6 个失败。使用同一审计工具在性能修改前的源文件快照重现以下 6 类失败，本次未降低审计标准或改动这些无关功能：

- 用户组操作列测试固定要求宽度 240，既有页面为 180。
- `CustomFormGuide.vue` 缺少审计要求的 `registerFormNodeComponent` 说明。
- 关联内容测试在 API 转导出文件中寻找 URL 常量而失败。
- 配置文档中“组织锚点”说明过于泛化。
- UI 审计解析 `EmbedBindingPanel.vue` 报缺少结束标签；对应源文件与修改前一致。
- 可维护性行数预算仍有 99 个超限文件；修改前后超限文件集合一致，没有新增超限文件。

前端逐项结果 `/tmp/flow-performance-frontend-all-results.json`，基线对照 `/tmp/flow-performance-baseline-results.json`。不能把此次结果描述为前端全量测试通过。

额外执行的部署清单检查：Helm lint 与渲染通过。Docker daemon 未启动后，改用官方发布、SHA-256 已核验的本机 kubeconform v0.7.0；官方 schema 域名解析为 0.0.0.0，下载失败，因此终止该轮无效网络重试，完整清单校验未完成。日志 `/tmp/flow-performance-manifests.log`。本任务未改变部署清单或审计脚本，也未部署应用。

本任务新增 Flyway 迁移：`V106__outbox_retention_index.sql`。没有修改、删除或重命名主分支已有迁移；工作区其他任务的 V103、V104、V105 不属于本次性能改进。

