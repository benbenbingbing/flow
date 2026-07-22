# 表单与列表通用配置升级进度

## 暂停信息

- 暂停时间：2026-07-20 20:42（Asia/Shanghai）
- 恢复时间：2026-07-20 20:46（Asia/Shanghai）
- 再次暂停时间：2026-07-20 21:21（Asia/Shanghai）
- 再次恢复时间：2026-07-20 21:24（Asia/Shanghai）
- 本次暂停时间：2026-07-20 21:50（Asia/Shanghai）
- 当前状态：已按用户要求停止实现、测试、服务启动和浏览器验收；本轮并行 Agent 已全部收回并关闭。
- 当前目标：完成自定义表单、递归节点、列表单项配置、草稿发布、统一数据源、迁移兼容及真实流程验收。
- 数据库约束：只使用隔离克隆库 `workflow_ui_config_clone_20260718`；原数据库未修改。
- 运行状态：端口 `3000`、`8080` 未发现监听；隔离库后端和 Vite 前端保持停止。
- 工作区状态：保留全部未提交修改；没有回滚、提交或清理其他历史改动。
- 测试注意：本轮新增功能已通过定向后端 83 项测试、前端集成测试、页面配置审计和生产构建；尚未执行合并后全量 Maven/前端回归、数据库迁移演练和浏览器截图级验收，不得把定向结果当作最终交付结论。

## 已完成

### 后端

- 表单与列表草稿、差异、发布、历史版本激活和内容哈希校验。
- 发布/激活时校验递归节点父子类型、最大 8 层、跨表单循环引用、模板版本及扩展契约。
- 表单节点、列表字段、按钮和场景按单项增删改，使用 `expectedRevision` 返回 `409` 冲突。
- 统一数据源输入/输出 Schema 校验、映射、缓存隔离、数据权限计划及受控 Provider/Connector 执行。
- 数据源 `/preview` 与 `/execute` 已分离授权：草稿预览必须由配置管理员发起且数据源真实存在于对应 FORM/LIST 草稿绑定。
- 生产执行必须命中当前 ACTIVE FORM/LIST 发布快照中的相同 `sourceId + usage` 绑定；无来源、未绑定、版本漂移、作用域不符和列表访问权限不足分别返回结构化 `403/409`。
- 运行时实体、列表、用户、组织/租户及数据权限计划全部由服务端解析；请求中的 `entityCode`、`listKey`、发布版本等只作为一致性声明，不能扩大授权。
- Connector 已接收服务端可信运行上下文、完整 `DataScopePlan`、权限摘要和服务端生成的 SHA-256 幂等键；客户端伪造可信字段或幂等键会被拒绝。
- Provider SPI 的 `execute(context, dataScopePlan, configuration, input)` 签名保持不变，旧 `UiDataSourceContext` 五参数构造方式继续兼容。
- 普通 `BEFORE_SUBMIT` 已改为后端唯一权威执行；前端只允许显式标记 `clientPrevalidate=true` 且 `sideEffectFree=true` 的无副作用预校验，并且不合并其返回结果。
- 提交链路已增加业务追踪与幂等上下文，实体新增、修改和流程审批提交共用后端执行路径。
- 流程发布快照已固定节点表单的 `formReleaseId/formReleaseVersion`；任务详情、流程进度和实体表单解析已优先按具体流程定义读取对应表单发布快照。
- 表单发布运行时支持按 `formId + releaseId + releaseVersion` 精确读取并校验内容哈希。
- 审批提交已按任务的 `processDefinitionId` 读取流程发布快照，并把节点固定的 `formReleaseId/formReleaseVersion` 传入提交运行时。
- 历史非激活表单版本仅允许服务端内部固定发布上下文执行；客户端不能通过请求字段伪造固定版本访问。
- 审批提交幂等键已包含表单发布 ID，避免同一业务操作跨发布版本错误复用结果。
- 子表单节点已显式保存 `childFormId/releaseId/releaseVersion`，兼容旧 `refFormId/publishedFormId`，并按具体发布快照校验跨表单循环和最大 8 层。
- 子表单设计器可选择具体已发布版本；运行时通过最小化 `runtime-release` 接口读取指定快照，不使用草稿或管理端版本历史接口。
- 配置写权限已覆盖表单、列表、数据源、扩展、模板和发布接口。
- 表单、列表、模板、发布历史等设计读取接口已补充配置管理权限；正常实体运行时继续使用专用解析接口。
- 发布和历史激活已锁定配置主记录，同内容重复发布返回当前激活版本；新增迁移约束同一配置只能存在一个 ACTIVE 发布。
- 兼容整包表单和列表保存已增加父配置 `expectedRevision` CAS；列表整包同时校验子字段 revision，冲突整笔事务回滚并返回服务器当前配置。
- 普通 HTTP 整包保存不能绕过 CAS；系统迁移/导入保留独立入口。
- 表单差异删除已按节点深度降序执行，保证子节点先删、父节点后删。
- 活动表单节点 `form_id + node_key` 已增加数据库唯一约束；软删除历史允许重复，竞态冲突稳定映射为 `409`。
- 表单级数据源绑定已持久化到草稿元数据并进入发布快照，设计器支持配置 `FORM_INIT`、`AFTER_LOAD` 和 `BEFORE_SUBMIT`。
- 表单运行时已按顺序执行表单级 `FORM_INIT`、`AFTER_LOAD`；提交时先执行表单级 `BEFORE_SUBMIT`，再执行节点和字段绑定。
- 配置迁移已支持数据源引用便携转换、导入后生成初始发布版本、幂等导入及发布依赖重试。
- 启动迁移可增量补齐历史节点，不再因部分迁移而跳过整个表单。
- 历史孤立 `TAB` 会匹配同表单最近的 `TAB_SET`；修复与发布处于同一事务。
- 孤立 `TAB` 自动发布安全门使用规范化 JSON 语义比较，兼容 `Integer/Long` 等数值反序列化差异。
- 安全门拒绝时只记录差异章节、节点 ID 和字段名，不打印可能包含敏感信息的配置值。
- 新增候选任务正式认领接口 `POST /api/process-task/claim/{taskId}`。
- 候选任务完成前可原子认领；并发第二认领者返回 `409 TASK_ALREADY_CLAIMED`。
- 认领同步 Flowable、`process_task` 和动态实体当前办理人，并记录 `CLAIM` 操作及 `_team` 参与事件。
- 任务详情访问改为校验任务身份；审批提交仍执行实体操作能力校验。
- `TaskVO` 已增加 `assigneeType` 和 `claimRequired`。

### 前端

- 新增递归表单节点组件，按 `parentId` 展示多层容器和内容节点。
- 表单设计器按稳定节点 ID 管理选中项，属性面板只保存当前节点。
- 同级移动使用稀疏排序键，不再全量重建节点。
- 列表列、按钮、场景和顶层设置均为单项保存，并展示未发布状态及 `409` 冲突。
- 表单与列表已提供草稿、差异、发布、版本列表和历史激活入口。
- 表单设计器已增加表单级数据源配置弹窗，支持同一 usage 多条绑定、受控数据源选择、输入/输出映射和预校验安全标记。
- 表单级数据源保存使用元数据局部 PATCH 与 `expectedRevision`，不覆盖其他节点配置；未知绑定属性保留在 `extra`。
- 首页待办对候选任务先显示“认领”，认领后再显示审批、转办、加签和抄送操作。
- 用户手册已覆盖递归节点、单项保存、数据源、发布回滚、模板升级和迁移兼容。
- 流程用户手册已补充候选任务认领说明。
- 定制开发相关页面已覆盖节点组件、Provider/Connector、配置 Schema、运行上下文和版本兼容边界。

## 已验证

### 自动化

- 候选任务针对性回归：`TaskActionServiceTest`、`ProcessTaskServiceTest` 共 11 项通过。
- 数据源权限定向回归：39 项通过，覆盖任意启用数据源直调、未发布、未绑定、版本漂移、列表权限、Connector 伪造、草稿管理员预览和可信上下文。
- 后端完整 Maven 回归：430 项通过，1 项跳过。
- 流程发布快照、流程进度定向测试：12 项通过。
- 子表单发布版本、循环、深度和兼容测试：`EntityFormNodeServiceTest` 19 项通过。
- 子表单相关前端页面配置、运行时集成和生产构建通过。
- `BEFORE_SUBMIT` 后端相关测试 14 项通过；对应前端测试和构建通过。
- 后端干净打包：12 个 Maven 模块编译并打包成功。
- 前端 `npm test`：单元、集成、功能、页面配置和 UI 配置测试全部通过。
- UI 配置审计：87 个文件、1783 个控件通过。
- 前端生产构建：通过。
- 全工作区 `git diff --check`：通过。
- 本轮合并后后端定向回归：83 项通过，零失败，覆盖固定表单发布提交、历史发布授权、表单级数据源、整包 CAS、节点删除顺序和数据库唯一迁移。
- 整包 CAS 新增 11 项负向测试，Agent 独立定向回归 20 项通过。
- 节点删除与唯一约束 Agent 独立定向回归 27 项通过。
- 本轮前端 `npm run test:integration`、`npm run test:page-config` 和 `npm run build` 均通过。

### 真实配置闭环

- `test:real-ui-config` 通过。
- 表单检查通过：初始差异为空、单节点隔离、过期 revision 返回 `409`、草稿不影响运行时、发布生效、历史版本回滚、草稿恢复。
- 列表检查通过：单字段、单按钮、单场景隔离，三类 `409`，草稿隔离、发布生效、发布快照包含场景修改、历史回滚、草稿恢复。
- 隔离库中的历史孤立 `TAB` 已修复为 `node_tab_set_1784531494264` 的子节点。
- 表单 `2059999841773215746` 已生成迁移发布版本 7。

### 真实流程闭环

- `test:workflow:config-real` 已通过。
- 测试脚本已改用真实状态映射接口：
  - `PUT /process-entity-status-mappings/process/{processId}`
  - `GET /process-entity-status-mappings/process/{processId}`
- 流程数据经过两级审批后，一级状态为 `LEVEL1_APPROVED`，最终状态为 `FINAL_SPECIAL`。
- 通过证据：`workflow-web/docs/workflow-closure/config-closure-2607201036i4h.json`。

## 当前未完成

- 浏览器截图级真实手工验收尚未执行；恢复后需覆盖表单设计、列表设计、草稿预览、发布、回滚和真实流程。
- 模板升级仍只计算合并结果，尚未完整持久化升级后的模板版本与本地覆盖；列表按钮模板字段和发布校验也需补齐。
- `SUB_FORM/REPEATER` 内嵌子节点的运行时语义仍需统一；当前子表单主要把发布快照转换为兼容字段集合。
- 整包保存白名单需复核新增加的 `dataSourceBindingsDocument` 是否同时覆盖用户 CAS 保存和系统导入，避免表单级绑定在兼容整包导入时遗漏。
- 启动迁移失败隔离、开发文档 SPI 精确签名和节点级差异展示仍需完善。
- 列表发布/激活尚需校验列表字段与按钮绑定的模板类型、版本和内容哈希。
- 尚未进行最终变更文件总清单核对、数据迁移演练、真实截图证据归档和最终交付总结。

## 关键修改文件

- `workflow-server/workflow-entity/src/main/java/com/workflow/runner/UiConfigurationBootstrapRunner.java`
- `workflow-server/workflow-app/src/test/java/com/workflow/runner/UiConfigurationBootstrapRunnerTest.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/UiConfigReleaseService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/ResolvedEntityFormRelease.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/UiConfigDraftMetadataService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/EntityFormNodeService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/EntityFormService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/EntityListConfigService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/UiDataSourceService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/UiDataSourceExecutionAccessService.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/service/UiDataSourceExecutionAuthorization.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/dto/UiDataSourceExecuteRequest.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/dto/EntityFormMetadataPatchRequest.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/dto/EntityFormSaveRequest.java`
- `workflow-server/workflow-app/src/main/resources/db/migration/V033__enforce_active_form_node_key_uniqueness.sql`
- `workflow-server/workflow-app/src/main/resources/db/migration/V034__add_form_level_data_source_bindings.sql`
- `workflow-server/workflow-contracts/src/main/java/com/workflow/contracts/integration/IntegrationRequest.java`
- `workflow-server/workflow-contracts/src/main/java/com/workflow/contracts/integration/IntegrationRuntimeContext.java`
- `workflow-server/workflow-contracts/src/main/java/com/workflow/contracts/ui/UiDataSourceContext.java`
- `workflow-server/workflow-app/src/test/java/com/workflow/service/UiDataSourceExecutionAccessServiceTest.java`
- `workflow-server/workflow-migration/src/main/java/com/workflow/service/migration/ConfigMigrationAssetService.java`
- `workflow-server/workflow-migration/src/main/java/com/workflow/service/migration/ConfigMigrationImportApplyService.java`
- `workflow-server/workflow-process/src/main/java/com/workflow/service/TaskActionService.java`
- `workflow-server/workflow-process/src/main/java/com/workflow/service/ProcessTaskService.java`
- `workflow-server/workflow-process/src/main/java/com/workflow/controller/ProcessTaskController.java`
- `workflow-server/workflow-process/src/main/java/com/workflow/vo/TaskVO.java`
- `workflow-web/src/components/FormNodeDesignItem.vue`
- `workflow-web/src/components/form-fields/components/SubFormField.vue`
- `workflow-web/src/views/EntityFormDesignByEntity.vue`
- `workflow-web/src/views/EntityListConfigDesign.vue`
- `workflow-web/src/views/Home.vue`
- `workflow-web/src/api/processTask.js`
- `workflow-web/src/data/user-manual/entity.js`
- `workflow-web/src/data/user-manual/process.js`
- `workflow-web/scripts/real-ui-config-release.mjs`
- `workflow-web/scripts/real-workflow-config-closure.mjs`

## 恢复执行建议

1. 先确认不存在被中断的 Maven、Surefire、后端、Vite 或残留 Agent；不要直接启动服务。
2. 复核 `EntityFormService` 整包保存白名单和 `EntityFormSaveRequest`，确保表单级数据源绑定在用户 CAS 保存、导入及快照之间一致。
3. 完成模板升级闭环：
   - 表单节点和列表字段显式升级后立即单项持久化；
   - 列表按钮补齐 `templateId + templateVersion + localOverrides`；
   - 列表发布/激活校验模板类型、版本和内容哈希；
   - 增加升级冲突及本地覆盖负向测试。
4. 统一 `SUB_FORM/REPEATER` 嵌套运行时语义，补齐节点级差异、迁移失败隔离和开发文档 SPI 签名。
5. 执行最新后端全量 Maven、前端全量测试、生产构建和 `git diff --check`。
6. 在隔离克隆库执行 `V033`、`V034` 及全量迁移演练，核对幂等性和迁移报告。
7. 启动隔离库后端：

   ```bash
   cd /Users/dawei/Documents/ddup/ai/flow/workflow-server
   DB_NAME=workflow_ui_config_clone_20260718 java -jar workflow-app/target/workflow-server-1.0.0.jar
   ```

8. 启动前端：

   ```bash
   cd /Users/dawei/Documents/ddup/ai/flow/workflow-web
   npx vite --port 3000 --host
   ```

9. 重新执行真实闭环：

   ```bash
   cd /Users/dawei/Documents/ddup/ai/flow/workflow-web
   API_BASE=http://127.0.0.1:8080/api npm run test:real-ui-config
   API_BASE=http://127.0.0.1:8080/api npm run test:workflow:config-real
   ```

10. 恢复 in-app Browser，完成表单设计器、列表设计器、数据源、发布版本、回滚和真实流程的截图级验收。
11. 核对用户手册、定制开发指南、API 示例、迁移报告和最终文件清单。

## 2026-07-22 暂停快照

**记录时间：** 2026-07-22 09:43 CST  
**执行状态：** 已按用户要求暂停；未启动服务、未执行全量测试、未进行浏览器验收。

### 本轮新增完成

- 表单整包兼容保存已补齐 `dataSourceBindingsDocument`：
  - 新建或系统导入可写入表单级数据源绑定；
  - 旧版用户 CAS 整包保存未传该字段时保留原值，避免意外清空；
  - `EntityWholePackageCasServiceTest` 定向测试已通过（12 项）。
- 嵌套表单运行时已从“仅扁平字段兼容”推进为递归节点树渲染：
  - `SUB_FORM`、`REPEATER` 支持在子表单行内递归渲染子节点；
  - 子表单读取指定已发布版本快照，并保留节点树；
  - `SECTION` 不再被错误转译为运行时字段；
  - 前端生产构建已通过。
- 模板显式升级链路已推进：
  - 表单节点升级存在本地覆盖冲突时要求确认，并立即保存当前节点；
  - 列表列、工具栏按钮、行按钮均支持模板选择、版本锁定、本地覆盖与显式升级；
  - 列表按钮增加 `templateId`、`templateVersion`、`localOverridesDocument` 持久化字段；
  - 列表发布和激活校验列表列/按钮模板类型、版本与内容哈希；
  - 模板并发建版本时对唯一键冲突返回修订冲突；
  - 相关后端定向测试已通过（15 项）。

### 本轮已验证

- `mvn -pl workflow-app -am -Dtest=EntityWholePackageCasServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `mvn -pl workflow-app -am -Dtest=UiComponentTemplateServiceTest,UiConfigReleaseServiceTest,EntityListIncrementalConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `npm run build`：通过。
- `git diff --check`：通过。

### 暂停时剩余重点

1. 将发布差异从章节级 `changedSections` 扩展为基于稳定节点/字段/按钮 ID 的增删改移明细，并在发布确认界面展示。
2. 修复 `UiConfigurationBootstrapRunner` 的迁移失败隔离：当前类级事务会在任一配置失败后回滚此前成功项，需要拆分为独立 `REQUIRES_NEW` 事务并输出结构化迁移报告。
3. 补充嵌套子表单的表单级 `FORM_INIT`、`AFTER_LOAD` 数据源执行语义及对应负向测试。
4. 执行前后端全量测试、隔离库迁移演练和真实浏览器截图级验收。
5. 最终核对并补齐用户手册、定制开发指南、API 示例与迁移报告。

## 2026-07-22 完成记录

**记录时间：** 2026-07-22 10:10 CST  
**执行状态：** 自定义表单与列表配置升级已完成实现、迁移演练、真实闭环和浏览器验收。

### 完成项

- 发布差异已升级为稳定 ID 级明细：表单节点、列表字段、按钮和场景均返回 `ADDED`、`REMOVED`、`UPDATED`、`MOVED` 及变更字段；旧 `changedSections` 响应保持兼容。
- 表单与列表设计器发布确认界面已展示明细差异；草稿预览、历史发布版本和激活回滚均继续使用不可变发布快照。
- `UiConfigurationBootstrapRunner` 已改为逐项 `REQUIRES_NEW` 事务；单个历史配置迁移或初始发布失败会写入结构化报告，不再回滚其他成功项或阻断服务启动。
- `SUB_FORM`、`REPEATER` 子行已在独立记录上下文中执行 `FORM_INIT`、`AFTER_LOAD`、默认值和计算数据源；父记录数据不会串入子行。
- 表单整包兼容保存、列表按钮模板绑定、模板显式升级、数据源绑定、发布校验、手册与定制开发指南均已同步完成。
- 列表设计器补齐响应式行为：窄屏下转为单列预览，标签栏可横向访问，避免长标签被容器裁切。

### 隔离库迁移演练

- 使用数据库 `workflow_ui_config_clone_20260718` 启动服务；未连接或修改原数据库。
- Flyway 成功校验 36 个迁移，并将隔离库从 `V031` 原子升级至 `V035`。
- 历史 `V030` 校验和已按已部署版本恢复；未执行 Flyway repair 或修改迁移历史。

### 真实闭环验收

- `API_BASE=http://127.0.0.1:8080/api npm run test:real-ui-config`：通过。
  - 覆盖节点/字段/按钮/场景单项隔离保存、过期 `expectedRevision` 的 `409`、草稿不影响运行时、发布生效、历史激活回滚及草稿还原。
- `API_BASE=http://127.0.0.1:8080/api npm run test:workflow:config-real`：通过。
  - 覆盖流程配置发布、实体绑定、流程发起和任务闭环所需配置一致性。
- 应用内浏览器登录隔离环境后，已人工检查表单设计器、递归表单预览、列表设计器和列表单项配置区；未发现阻断性视觉或交互问题。

### 最终回归

- `npm run build`：通过。
- `npm test`：通过（单元、运行时集成、功能、页面配置审计、UI 配置审计；共 87 个文件、1803 个控件）。
- `mvn -pl workflow-app -am test`：通过（460 项通过，1 项跳过）。
- `git diff --check`：通过。
