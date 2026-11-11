# 新建流程与 wfpack 跨环境迁移测试记录

测试日期：2026-11-11（Asia/Shanghai）。用户所称 wpack 在项目中的实际扩展名为 `.wfpack`。

本次先按要求测试、记录问题，随后用户授权“如果存在阻塞性问题，直接修改”，已修复下面 3 个阻断场景。真实启动两个应用和独立数据库，执行导出、上传、分析、发布及审批运行；同时补充真实浏览器新建流程验证和现有自动化测试。下文保留修复前的失败现象和证据。

结论：**确认并修复 3 个问题，修复后的真实跨环境复测通过。** 全新数据库可完整执行 V001–V129；父子流程同包发布改为按调用依赖排序；缺省字段类型及旧投影的 FIELD 占位值按实体元数据恢复。基础迁移、重复/增量更新和实际审批链路通过。

## 修复及复测结果

| 问题 | 修复方式 | 实际复测 |
| --- | --- | --- |
| P1 空库启动失败 | 应用启动和独立迁移工具共用 `MySqlV062CompatibilityCallback`。仅为 V062 的两条已知 SQL 提供等价执行：临时表沿用 `sys_menu.id` 的字符集/排序规则；授权清理只 JOIN 一次临时表。保留历史迁移文件及校验和，任何兼容 SQL 执行失败仍使迁移失败 | 两个全新应用库均执行 129 个迁移并启动；另一个真实 MySQL 集成测试验证复制的盘点根/子菜单及授权清理、通用权限保留、完整迁移校验和与重复执行 |
| P1 同包父子流程发布失败 | 发布与回滚均按包内 PROCESS 调用依赖排序，子流程先于父流程；批次外依赖保留正常校验，调用环明确拒绝 | 原配置重新导出的完整包在新目标一次发布成功，实际父子实例和业务记录均最终完成；多层/共享子流程、自引用与环有回归测试 |
| P2 缺省字段类型导入失败 | 表单 materialize 前，从目标实体字段恢复缺省类型及旧 FIELD 占位值；不覆盖显式有效数据类型，不猜测缺失实体字段，不改结构节点 | 原始失败包逐字节复用后发布成功，节点类型恢复为 STRING，实际创建业务记录成功；新建同类配置后导出导入也成功 |

修复后真实 API 验收 **25 项检查通过**：包括新源环境创建流程、表单、实体并发布，导出完整包到新目标，目标 ID/表单引用重绑，重复上传与发布不增版，流程增量升为 v2，以及父、子、基础、新建四类业务记录最终均为 `COMPLETED`。

工作区存在同时进行的引用协议升级：格式从 3 升到 4，旧父流程包因 `callConfig.entityRecordId` 未带新协议标记而被明确阻断。最终验证使用同一源配置回源重新导出的 [格式 4 父子流程包](evidence/wfpack-2026-11-11/fixed-parent-child-format4.wfpack)，没有手工修改包来绕过检查。缺省字段类型的格式 3 复现包仍逐字节直接复用。新增流程也由当前源应用实际导出格式 4 后导入目标。

复测证据：[API 响应和最终数据库状态](evidence/wfpack-2026-11-11/fix-observations.json)、[25 项结果](evidence/wfpack-2026-11-11/fix-e2e-results.txt)、[两个全新应用启动日志](evidence/wfpack-2026-11-11/fixed-fresh-startup.txt)、[修复后新建流程包](evidence/wfpack-2026-11-11/fixed-new-roundtrip.wfpack)。

## 测试环境与边界

- 代码：报告生成时 HEAD 为 `3a2bdc4a4589d4eebd1135d3b78560e7aa9b6e60`。构建开始时的工作区后来被其他操作提交；已核对其间流程、迁移、实体、数据库迁移模块及流程列表/设计页面无差异。
- 后端：当前源码构建成功；Temurin Java 21.0.11。最终自动化使用独立源码副本，避免共享 target 被并行构建改写；生产代码改动保留在原工作区。
- 数据库：临时独立 MySQL 8.0.27，端口 13311；源/目标应用端口 18081/18082，数据库、文件目录、签名密钥分别独立。没有使用原开发环境的数据库和应用接口。
- 第一轮在两个空库 `wfpack_source`、`wfpack_target` 按版本顺序执行 Flyway，均失败于 V062，见问题 1。
- 修复前为继续检查迁移业务，另建两个空库 `wfpack_source_current`、`wfpack_target_current`，使用仓库 `docs/sql/mysql.sql`（V129）初始化。仅这条独立初始化路径关闭 Flyway；没有在失败库上 repair、baseline、跳过失败版本或改写迁移文件。初轮通过项基于该替代路径；修复后的 25 项验收改用完整 Flyway 初始化的新库。
- 浏览器：临时 Chrome 配置目录；Vite 端口 13000，连接源测试应用。页面验证覆盖登录、流程列表新建、进入设计器、保存初始草稿。完整包导入、发布及审批通过真实 HTTP API 执行，未全部逐步点击页面。
- 测试数据均为 `qa_` 前缀的合成实体、流程和人员。本次未覆盖真实用户发布包、其他六种数据库、外部接口调用、附件存储迁移、所有子表单/模板/自定义扩展组合。

## 问题 1：P1，全新 MySQL 环境在 V062 初始化失败（已修复）

**影响：** 新环境无法启动，尚未进入 wfpack 导入就被阻断。两个独立空库均复现；已初始化且越过该版本的环境不由本测试推断。

复现步骤：

1. 在 MySQL 8.0.27 创建空数据库，将当前完整应用的数据源和 schema 数据源都指向该库。
2. 保持 Flyway 开启，不预导入全量初始化脚本、不设置跳过迁移。
3. 启动应用。日志依次执行到 V061，执行 V062 第 29 行时失败，应用启动终止。

实际错误：

```text
Failed to execute script V062__remove_entity_list_scope_inventory.sql
SQL State  : HY000
Error Code : 1137
Message    : Can't reopen table: 'own_root'
Line       : 29
```

定位：`workflow-server/workflow-db-migrator/src/main/resources/db/migration/V062__remove_entity_list_scope_inventory.sql:29`。同一条 DELETE 在第 32、33 行以 `own_root`、`parent_root` 两个别名重复 JOIN 临时表 `tmp_scope_inventory_menu_root`。这是本次实际失败 SQL 的直接定位，没有修改历史迁移。

预期：空库可以顺序执行全部迁移并启动应用。

证据：[两个空库的失败日志摘录](evidence/wfpack-2026-11-11/flyway-failure.txt)。

## 问题 2：P1，父子流程同包导入分析通过，发布却按编码顺序失败（已修复）

**影响：** 源端已经正常发布的父子流程及完整实体同包迁入空目标时，是否成功受到 processKey 字典序影响；分析阶段未提示阻断。

复现配置：

- 子流程 `qa_z_child`，唯一绑定实体 `qa_child_entity`，源端已发布。
- 父流程 `qa_a_parent`，唯一绑定实体 `qa_parent_entity`，源端已发布。
- 父流程 callActivity 调用 `qa_z_child`，`callActivityType=bpmn`、`entityBinding=existing`、`entityRecordId=${relatedRecordId}`。
- 两个实体、表单完整导出，表单字段显式包含 `fieldType=STRING`，排除问题 3 的干扰。

复现步骤：

1. 使用目标端没有上述配置的环境，上传 [child-parent-order.wfpack](evidence/wfpack-2026-11-11/child-parent-order.wfpack)。
2. 若目标签名密钥不同，按页面/API 要求核对文件 SHA-256 后明确确认信任。
3. 调用 `POST /api/config-migration/imports/{id}/analyze`：四个条目均为 `NEW / RESOLVED`，`blocked=false`，没有缺失依赖或风险。
4. 调用 `POST /api/config-migration/imports/{id}/publish`：HTTP 400，返回下述错误。

```text
所选子流程必须已发布且唯一绑定实体: qa_z_child
```

本次失败批次：`2120338598262755330`。包号：`WFP-WFPACK_QA_ADVANCED_RETRY-20261111101130-09E247`。

包 SHA-256：`381acb3074765e6db8c8a091dd8578cc11f1eaf7f605f7d7645241599fc7baf2`。

定位：`ConfigMigrationImportApplyService.java:3211` 的 `itemsOfType` 按 `businessKey` 排序；第 323–332 行先准备所有流程、绑定实体，再按该顺序逐个发布。父流程 `qa_a_parent` 先于子流程 `qa_z_child` 发布，此时子流程还是目标端草稿。`ProcessBpmnPublishSanitizer.java:2439` 的 `validateCalledEntity` 要求被调用流程已发布，因而拒绝父流程。这是实际响应与代码路径共同支持的定位。

对照验证：先单独导入并发布子流程及其完整实体，再导入父流程及其完整实体，两个批次均成功；父流程包中的子流程识别为 `CONSISTENT`。随后真实创建子实体记录、启动父流程，确认引擎产生父子实例，办理子流程任务后，父子业务记录最终均为 `COMPLETED`。

失败回滚：在拆包导入前查询，上述父子实体、流程、表单记录均为 0，未留下对应业务表。失败批次恢复到 `ANALYZED`，`error_message` 为 NULL；错误由本次发布响应体现。

预期：同包依赖能够先发布子流程再发布父流程；若当前不支持，应在分析阶段明确阻断并说明，不能显示全部可发布后因内部顺序失败。

## 问题 3：P2，源端接受的缺省字段类型表单，导入分析通过后发布失败（已修复）

**影响边界：** 这是通过公开表单 API 创建的缺省属性配置；未证明当前正常设计器一定会生成该缺省值。显式包含 `fieldType=STRING` 的正常测试表单已成功迁移。

复现步骤：

1. 源端新建实体 `qa_optional_type`，字段 `note` 类型 STRING、长度 100，创建默认表单。
2. 调用 `POST /api/entity-forms/{formId}/nodes`，提交以下节点，省略 `props.fieldType`。接口成功保存。

```json
{
  "nodeKey": "note",
  "nodeType": "FIELD",
  "bindingType": "ENTITY_FIELD",
  "bindingRef": "note",
  "props": {
    "fieldCode": "note",
    "fieldName": "备注",
    "componentType": "input",
    "label": "备注"
  }
}
```

3. 源端表单 `publish-preview` 返回 `canPublish=true`，实际发布也成功。
4. 导出完整实体，上传目标环境。可直接使用 [missing-field-type.wfpack](evidence/wfpack-2026-11-11/missing-field-type.wfpack) 复现。
5. 目标分析为 `NEW / RESOLVED`、`blocked=false`；目标发布 HTTP 400，错误如下。

```text
组件 input 不兼容字段类型 FIELD
```

本次失败批次：`2120339689067008002`。包号：`WFP-WFPACK_QA_OPTIONAL_TYPE-20261111101550-0E63A4`。

包 SHA-256：`730c5bf72a2e655bfe8011ac9c568434198534a3b8e55273aabb901e4c3db1d3`。

定位：`EntityFormFieldProjection.java:116–124` 在没有 `fieldType` 时回退到 `nodeType`，普通字段节点得到 `FIELD`；`mergeField` 第 259 行将投影结果合回节点。导入在 `ConfigMigrationImportApplyService.java:1333–1350` materialize 后执行 `replaceByDiff`，触发组件与字段类型校验。上述回退值和目标端兼容性校验构成不一致，实际错误与该路径吻合。

失败回滚：目标不存在 `qa_optional_type` 实体，未留下半成品。批次状态为 `ANALYZED`、`error_message` 为 NULL。

预期：源端能够发布的配置应在导出导入时保持有效类型；如果缺省字段类型不合法，应在源端保存/发布或目标分析阶段明确拦截。

## 初轮已通过的真实跨环境场景

| 场景 | 核对结果 |
| --- | --- |
| 新建流程、表单与实体 | 源端 API 创建 `qa_basic`、`qa_expense`；STRING/DECIMAL 字段、必填约束、起始和审批节点自定义表单绑定均能发布 |
| 完整包首次导入 | formatVersion=3，完整实体+流程导入、分析、发布成功；目标流程/实体/表单生成新 ID，BPMN 表单引用指向目标表单 |
| 仅勾选流程 | 自动带出表单所需的部分实体资产；空目标缺少所属实体时正确阻断。新环境需要同时选择完整实体，这项按当前导出范围处理，未单列为缺陷 |
| 独立签名密钥 | 首次上传要求显式确认 SHA-256，不直接建立正式导入批次；确认后记录 `MISMATCH_CONFIRMED`、确认人和时间 |
| 同文件重复上传/发布 | 复用原导入批次，重复发布未增加流程版本 |
| 内容相同、不同包号 | 分析识别 `CONSISTENT`，发布复用配置 |
| 流程增量更新 | 修改流程名称和审批节点名称后识别 `SOURCE_NEWER`，目标原流程 ID 保持，版本升为 2，未残留源表单 ID |
| 基础流程实际审批 | 目标创建业务数据、启动实例、读取任务表单、admin 办理，调度处理完成后业务流程状态为 `COMPLETED` |
| 人员跨环境 ID 变更及撞号 | 源 `qa_reviewer` 与目标同用户名不同 ID；目标另外放置占用源 ID 的错误人员。办理人仍正确命中 `qa_reviewer`，未误选撞号人员；实际审批成功 |
| 知会配置及实际知会 | 导出不残留源用户 ID，目标知会配置绑定目标 ID；实际知会接收人为 `qa_reviewer`（运行记录中的 cc_user_id 按当前实现存用户名） |
| 目标人员缺失 | 目标即使存在相同源 ID 的其他用户，仍按 `USER/qa_missing` 缺失阻断，没有回退误匹配 |
| 包损坏、旧格式 | 篡改资产但不更新校验和时拒绝；将格式设为 2 且同步正确校验和后，明确返回不支持格式版本 2 |
| 子流程运行 | 分别先发布子、后发布父后，实际产生父子实例，子任务办结后父子最终完成 |
| 条件网关、并行会签 | 金额 200 走会签分支，生成 admin 和 qa_reviewer 两个真实任务；第一人办理后保留另一任务；金额 50 走默认分支；全部办理后均完成 |
| 浏览器新建和保存草稿 | 真实页面创建 `qa_browser_created`，进入 BPMN 设计器保存成功；revision=2、DRAFT，包含 BPMNDiagram，浏览器运行时异常为 0 |

页面证据：[新建流程保存成功截图](evidence/wfpack-2026-11-11/browser-create.png)。

调度说明：业务测试应用已启用调度并等待 outbox 处理，最终状态查询完成后才判定通过；没有将审批后的短暂 RUNNING 中间态记录为缺陷。

## 自动化与证据

修复后的隔离构建最终结果：

| 范围 | 测试数 | 失败 / 错误 / 跳过 |
| --- | --- | --- |
| workflow-process | 574 | 0 / 0 / 0 |
| workflow-migration | 217 | 0 / 0 / 0 |
| MySQL 兼容回调及真实空库迁移 | 4 | 0 / 0 / 0 |
| 应用启动迁移配置 | 2 | 0 / 0 / 0 |
| 合计 | **797** | **0 / 0 / 0** |

最终从独立源码副本执行干净编译和流程/迁移/数据库测试，并执行应用配置测试、完整应用打包；后续并行更新涉及的迁移测试又单独复跑通过。见 [最终测试摘要](evidence/wfpack-2026-11-11/final-backend-tests-summary.txt) 和 [精确统计](evidence/wfpack-2026-11-11/final-test-counts.json)。前端配置迁移 3 组测试最后再次通过，见 [最终输出](evidence/wfpack-2026-11-11/final-frontend-config-migration.txt)。

最后重启验证中，原问题配置再次分析均为 `CONSISTENT`，重复发布成功，流程 v2 保持；重新发起并实际办结一笔审批。**9 项检查通过**，见 [结果](evidence/wfpack-2026-11-11/final-smoke-results.txt)、[状态](evidence/wfpack-2026-11-11/final-smoke.json) 和 [重启时 129 个历史迁移校验日志](evidence/wfpack-2026-11-11/final-restart-validation.txt)。

构建边界：本次修改的兼容回调、迁移入口、发布顺序和表单恢复均包含在最终构建。由于工作区同时进行引用协议开发，独立副本建立后的其他并行代码变更不能自动算作已验证；具体源码及 JAR 哈希、快照后继续变化的文件列表见 [tested-build.json](evidence/wfpack-2026-11-11/tested-build.json)。本报告的完整模块测试和运行结论对应这一明确的构建快照，后续并行版本需使用其最终构建再次验收。

初轮后端执行命令（在 `workflow-server`）：

```sh
mvn -o -pl workflow-process,workflow-migration -am test \
  -Dtest='com.workflow.process.**,com.workflow.migration.**' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- `workflow-process`：86 个测试类，574 项，失败/错误/跳过均为 0。
- `workflow-migration`：38 个测试类，195 项，失败/错误/跳过均为 0。
- 合计 **769 项后端测试通过**，见 [结果摘要](evidence/wfpack-2026-11-11/backend-tests-summary.txt)。
- `npm run test:config-migration`：分页、页面分页集成、签名确认 3 组通过，见 [输出](evidence/wfpack-2026-11-11/frontend-config-migration.txt)。
- `node --test workflow-frontend/packages/workflow-core/tests/process-lifecycle.test.mjs`：6 项通过，见 [输出](evidence/wfpack-2026-11-11/frontend-process-lifecycle.txt)。
- `node scripts/called-process-browser-test.mjs`：真实 Chrome 下 BPMN 子流程转换、外围连线、撤销/重做、旧配置和记录来源校验通过；该脚本的业务请求为 Mock，与上述真实双应用 API 验收区分，见 [输出](evidence/wfpack-2026-11-11/called-process-browser.txt)。
- 针对真实双环境响应和数据库状态的 **36 项语义断言通过**。这些断言包含确认缺陷确实复现，不能解读为 36 个业务场景全部无缺陷。
- 原始业务观察、失败响应、分析条目、运行状态、断言名称及附件哈希见 [observations.json](evidence/wfpack-2026-11-11/observations.json)。

测试执行限制：最初扩大到所有上游模块时，非本次范围的 `ExcelListenersTest` 出现 JVM/native fork exit 134，未将全仓测试记为通过。曾遇到旧 target class 损坏、本地测试 CORS 配置不匹配，以及并行构建导致缺失 class；最终改用独立源码副本干净编译。另为并行新增的 `ConfigMigrationValueReferencesTest` 移除一个多余右括号，使整套迁移测试可以编译；未改其断言意图。

## 本次变更与清理

本次修复集中在迁移发布顺序、导入表单类型恢复和数据库迁移入口的精确 MySQL 兼容回调，补充对应测试、记录及合成证据。工作区其他引用协议和迁移页面改动来自并行工作，已保留，未回退。没有提交 Git commit。测试服务使用独立端口和目录，验收结束后停止并清理临时凭据；原开发服务未重启。

Flyway SQL / Java 版本迁移文件：**新增无、修改无、删除无、重命名无**。新增兼容回调是执行入口代码，不是版本迁移；没有更改最终结构或种子集合。已复核七份初始化脚本表名/字段清单均为 V129、200 表、2,796 字段，并同步 `docs/sql/README.md` 与 `database-design.md` 的兼容执行说明；七份 SQL 未修改。未执行 `flyway repair`，未创建 baseline。
