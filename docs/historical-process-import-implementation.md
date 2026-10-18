# 历史流程及关联业务数据导入实施记录

日期：2026-10-18。功能已在独立 worktree 完成实现与验证，交付目标为本地 main；环境数据库迁移尚未执行。

- 基础：本地 main，提交 `4b3fcbe4bdc24d9dc60481f6a6c8aadc61f8419b`。
- 分支：`codex/historical-process-import`。
- worktree：`/Users/dawei/.codex/worktrees/historical-process-import/flow`。
- 完整范围、模板和操作规则：[实现方案](historical-process-import-implementation-plan.md)。

## 已实现

- 历史导入管理页：多实体配置、发布字段模板、两类 XLSX 上传、节点/操作映射、联合预检、依赖排序、逐组执行、继续/重试和 JSON 报告。
- 关联数据：共享实体独立组优先创建；组成父子同组提交；旧 ID 自动转换为新主键；支持复用以前批次的来源记录。
- 原子性与幂等：实体、来源关联、档案及组成功进度在同一事务；失败整组回滚；来源唯一键和内容指纹防止重复创建及静默覆盖。
- 历史档案：固定 BPMN、完整审批数组、主实体及关联业务快照；实体列表增加“迁入历史”；重复办理保留并可从节点查看。
- 权限与保护：本人批次、操作权限、实体权限及数据范围复查；历史只读记录写保护；历史导入跳过表单事件并禁止自定义取号及启动流程。
- 数据边界：拒绝公式、未知列和未支持的附件；长 ID 按文本处理；小数在持久化中保持精度，档案展示按十进制文本返回。
- 配置一致性：修改配置后必须重新预检；上传/下载模板前保存当前编辑内容，防止模板配置与执行配置不一致。

## 主要代码入口

| 范围 | 文件 |
| --- | --- |
| 批次接口 | workflow-server/workflow-migration/src/main/java/com/workflow/migration/history/HistoryImportController.java |
| 预检与依赖排序 | 同目录 HistoryImportPlanner.java |
| 文件编解码 | 同目录 HistoryImportWorkbook.java |
| 组事务协调 | 同目录 HistoryImportService.java |
| 实体写入与来源映射 | workflow-server/workflow-entity/src/main/java/com/workflow/entity/history/EntityHistoryImportService.java |
| 只读策略 | 同目录 EntityHistoryWriteGuard.java |
| 档案存取 | workflow-server/workflow-process/src/main/java/com/workflow/process/archive/ProcessArchiveService.java |
| 跨模块契约 | workflow-server/workflow-port/src/main/java/com/workflow/contracts/history/ |
| 管理页面 | workflow-frontend/workflow-web/src/views/system/HistoryImport.vue |
| 档案组件 | workflow-frontend/workflow-web/src/components/ProcessArchivePanel.vue |

## 数据库迁移清单

新增：

`workflow-server/workflow-db-migrator/src/main/resources/db/migration/V124__historical_entity_and_process_import.sql`

该文件创建 history_import_batch、history_import_group、entity_import_source_link、process_archive_instance，并添加管理菜单及权限。

修改、删除、重命名既有版本迁移：无。创建前本地 main 最大版本为 V123；未改 V001，未执行 flyway repair。

部署时先按项目原有 Flyway 流程执行新增迁移，再启动服务及配置角色权限。此 worktree 未连接实际业务数据库执行迁移。测试使用 H2 MySQL 模式读取真实 V124 SQL，仅移除 H2 不支持的 MySQL ENGINE/CHARSET 后缀；不能据此替代真实 MySQL 环境验收。当前 Docker daemon 不可用，未运行 MySQL 容器验收。

## 验证记录

后端针对性检查共 119 项，失败 0、错误 0、跳过 0，包含：

- 新增导入预检 9 项、文件解析 4 项、真实 Mapper/事务集成 9 项、写保护 1 项。
- 既有实体关系、编码生成、实体动作、流程启动回归。
- API 访问策略、数据库适配器、模块/契约边界等架构检查。

事务集成使用真实 MyBatis SpringManagedTransaction、分页拦截器和 V124 表结构。动态实体变更端口用隔离测试表模拟，实体元数据及关系服务使用测试替身；测试验证回滚、提交前关系检查失败、重试、同组并发、跨批次复用、权限拒绝、配置漂移、精确小数和 XML 外部实体拒绝。实际业务字段约束及物理表需在部署环境以目标实体再次验收。

执行命令（workflow-server 目录）：

```sh
mvn -pl workflow-app -am '-Dtest=HistoryImport*Test,HistoryWriteGuardTest,DatabaseAdapterBoundaryTest,ArchitectureBoundaryTest,ApiAccessPolicyCoverageTest,ModulePackageLayoutTest,ContractModuleBoundaryTest,ContractPackageArchitectureTest,EntityRelationRuntime*Test,EntityCodeGeneratorServiceTest,EntityDataActionServiceTest,ProcessRuntimeServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test -q
```

前端：

```sh
# workflow-frontend/workflow-web
npm run test:history-import
npm run test:history-import:browser

# workflow-frontend
npm run build:web
```

前端单元测试 3 项通过。浏览器脚本挂载真实页面与 BPMN Viewer、模拟 API，覆盖关联配置、未保存配置保护、依赖顺序、失败后重试、同节点多次办理、图点击、关联快照及配置复制。脚本默认使用本机 Chrome，也可通过 CHROME_PATH 指定。管理端及 embed 构建通过。

### 已知 main 基线问题

额外运行的 CrossModulePersistenceBoundaryTest 失败，报告以下 6 条跨模块持久化依赖未在既有基线登记：

- ConfigMigrationPackageService → EntityRelation
- ConfigMigrationPackageService → EntityRelation.RelationType
- ConfigMigrationPackageService → EntityField.RefEntityType
- UiRuntimeAccessAdapter → EntityDefinitionMapper
- UiRuntimeAccessAdapter → EntityDefinition
- UiRuntimeAccessAdapter → EntityForm

上述源文件与架构基线文件相对 main 无差异，依赖已存在于本地 main。本次未通过放宽基线消除失败；119 项通过数不包含这一失败项。因此不能声称全量后端测试全部通过。

## 当前边界

本期只迁入已完成流程。附件、任意旧列映射、后台 Worker、来源账号自动匹配、时区换算、逐节点表单快照、已提交组撤销及档案修订不在实现范围。仅保存解析后的输入 JSON，不保留原始 XLSX 二进制。

关闭页面不取消已发出的组请求，但不会继续发送下一组。出现部分成功后，已提交组保留；临时错误可重试，修正文件需复制配置新建批次。历史图表示节点映射记录，不代表旧系统真实执行路径。
