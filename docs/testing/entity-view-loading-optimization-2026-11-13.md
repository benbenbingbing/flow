# 实体查看与流程首屏加载优化

日期：2026-11-13。

## 改动范围与接口约定

实体查看面板原来需要等候实体/表单、完整流程进度、档案摘要和按钮解析，再显示内容。完整进度又会读取实体聚合，指定表单的查看入口随后再次读取实体详情；档案摘要鉴权也会展开实体关系。当前缺少实际慢请求和运行环境，因此这些是已确认的代码开销，不能据此给出生产环境的耗时降幅。

本次调整前后端调用约定，不修改数据库结构，不保留旧进度接口的实体/表单聚合返回。前后端应一起发布。

| 接口 | 职责 | 调用时机 |
| --- | --- | --- |
| `GET /api/process-instance/{id}/form-runtime?taskId=...` | 实体数据、单个生效 `formConfig`、审批配置、当前任务及实例版本坐标 | 使用流程节点表单的查看/审批首屏；办理场景传真实任务 ID |
| `GET /api/process-instance/{id}/progress` | BPMN、节点状态、办理历史、处理人和可读轮次；无实体/表单负载 | 打开流程图/审批历史页签，或独立进度页面 |
| 原实体详情入口 | 按列表指定表单执行实体查看权限、数据范围和 `DETAIL_LOAD` | 指定实体表单的查看入口；不额外读取另一套流程表单 |

同步调整了 PC 弹窗、移动端、共享加载器及仓库内依赖旧进度表单返回的验收脚本。纯进度页面和历史轮次继续使用 `/progress`。

## 前端行为

- 点击查看/审批立即显示加载面板，列表按钮表单配置读取也在面板打开后执行。
- 表单和实体准备好后展示基本信息；按钮和迁入档案摘要不再阻塞 PC 首屏。按钮返回前不展示上一条记录的操作能力。
- 图与历史共用一次进度读取；失败可单独重试，不重载表单、不重放详情事件。
- 首屏与进度请求分别管理会话；切换记录、关闭弹窗或离开移动页面后，旧响应不能回填新记录。
- 历史响应不能替换表单引用或覆盖未提交输入；移动端独立的能力、字段元数据查询并行执行。

## 后端开销与权限

- 首屏不查询审批历史全集、评论、历史处理人或轮次。运行实例一次读取根变量坐标；结束实例只读取根作用域的 `entityCode`、`entityDataId` 和最后完成的用户活动。
- 结束时间相同的活动继续按开始时间和 Flowable 的事务顺序排序，避免自动跳过节点与前一审批任务同时结束时选错表单。
- 档案摘要沿用实体查看权限与原行级过滤条件，只校验记录可访问，不展开多值字段及关系子树。档案详情仍执行原字段过滤。
- 单次实体关系读取复用实体定义、字段、发布关系、表存在性与表名。缓存仅在本次聚合调用内有效；子记录数据仍按原关系与深度规则读取。
- 轮次实例事实以最多 200 个 ID 为一批读取，复用同一事实进行权限判断与 DTO 组装。每轮仍校验参与范围；不将知道某轮 ID 视为拥有其他轮次的权限。
- 指定任务必须属于该实例；加签任务仍验证真实来源任务与当前办理人；发布表单解析失败时失败关闭。

按钮条件仍由原服务端规则完整求值，本次没有删减权限条件或将客户端已有数据当作授权依据。

## 回归覆盖

后端专项覆盖：接口序列化与参数、实例鉴权先于加载、并行任务选择自身表单、外部/过期任务拒绝、加签权限、发布版本固定、默认表单回退、解析失败关闭、真实 Flowable 自动跳过与同名局部变量、最后用户活动、只鉴权读取不展开聚合、元数据请求内复用、轮次批量查询与权限过滤、迁入档案真实 JDBC 事务，以及 Embed 委托访问策略。

浏览器使用真实生产组件与模拟 API，不写入业务环境。额外挂起档案、按钮、进度、实体或目标表单配置响应，验证：

1. 档案及按钮未返回时，首屏表单已可见。
2. 首屏不发起 `/progress`，首次进入历史/图示后发起；两页签切换只读取一次。
3. 指定表单只调用一次实体详情事件，流程节点表单不重复调用普通实体详情。
4. 旧记录的实体、进度、档案及按钮响应不能覆盖新记录；等待表单配置期间关闭面板不会继续读取实体。
5. 进度失败可以重试，重试不再请求表单；审批页签切换后保留编辑和回填值。

### 执行结果

| 验证范围 | 实际结果 |
| --- | --- |
| 后端最终专项：流程首屏/进度、权限、实体关系、轮次、档案、历史迁入、Embed | 110 项通过，0 失败、0 错误、0 跳过；使用 `HEAD` 加本次 21 个后端文件的独立临时副本执行，原因见下节 |
| 共享前端包 | 78 项通过；最终额外复验共享流程加载器 9 项通过 |
| PC 单元与脚本测试 | `test:unit` 通过；完整回归中 Embed、表单模型与运行时集成阶段通过，后续阻断见下节 |
| 移动端单元测试 | 27 项通过 |
| PC 查看/审批/进度 Playwright | `runtime-refactor.spec.mjs` 4 项通过 |
| PC 实体查看生产组件浏览器脚本 | 通过，包含可选请求挂起、旧响应丢弃、详情事件次数和进度失败重试 |
| 移动端 Playwright | 37 项通过 |
| 生产构建 | 共享包、PC、Embed、移动端全部通过 |
| 差异检查 | `git diff --check` 通过；SQL / Java 迁移及 `docs/sql` 无变化 |

前端主要复验命令（在 `workflow-frontend` 执行）：

```sh
npm run test:packages
npm run test:unit --workspace workflow-web
npm run test --workspace workflow-mobile
npm run test:entity-archive-view:browser --workspace workflow-web
npm run test:e2e:refactor --workspace workflow-web -- tests/e2e/runtime-refactor.spec.mjs
npm run test:e2e --workspace workflow-mobile
npm run build
```

后端最终专项命令（在独立副本的 `workflow-server` 执行；副本只包含本次后端修改）：

```sh
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true mvn -o -pl workflow-app -am \
  -Dtest=ProcessProgressRuntimeServiceTest,ProcessProgressRuntimeServiceFormTest,ProcessProgressAutoSkipIntegrationTest,ProcessInstanceControllerTest,FormConfigsCompatibilityTest,ProcessRoundServiceTest,ProcessArchiveListTest,ProcessInstanceAccessServiceTest,EntityDataDynamicServiceSubFormTest,EntityRelationRuntimeDataKeyTest,ProcessInstanceServiceTest,HistoryImportTransactionTest,EmbedDelegatedRuntimePolicyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

浏览器验证使用模拟 API，后端自动跳过场景使用真实 Flowable/H2，历史迁入验证使用真实 JDBC 事务。未执行实际业务环境的端到端耗时压测，不能把上述结果解释为生产环境耗时指标。

### 全量测试中另外发现的问题

工作区全量后端测试报告共 5,398 项（其中 300 项跳过），3 项断言失败、1 项配置错误；实体模块 1,487 项、流程模块 609 项均通过。该轮之后补充了结束活动排序及相关专项测试，并通过上表的最终专项复验。全量失败项涉及本次未修改的路径：

- `DatabaseNamingIsolationTest`：Excel、schema operation、历史来源 Mapper 的命名检查。
- `UiInterfaceExtensionServiceWiringTest`：测试容器缺少 `CurrentFormRuntimeContext`。
- `CrossModulePersistenceBoundaryTest`：其他已有迁移/流程类的跨模块依赖清单。
- `ArchitectureBoundaryTest.HTTP_APIS_ONLY_USE_GET_OR_POST`：岗位分配控制器的 HTTP 方法检查。

PC 完整脚本回归中，`test:unit`、Embed 和运行时集成阶段通过，`test:functional` 在岗位 API 缺少 `precheckPositionAssignments` 的检查处停止。PC 全部 Playwright 场景 11 通过、1 失败，失败为关联内容设计器中找不到旧占位文案。本次查看、审批和进度浏览器场景另行执行验证。

验证期间工作区存在同时进行的其他模块修改，最终 Maven 再跑时还遇到新增组织分页测试的 `PaginationInnerInterceptor` 编译问题。本次保留这些修改，使用 `HEAD` 加本次后端修改的独立临时副本复验专项测试，避免改动其他任务的代码来绕过问题。

## 数据库迁移

本次新增、修改、删除的 SQL / Java Flyway 迁移文件：均无。未修改数据库结构、索引、约束或种子数据，`docs/sql` 无需变更。
