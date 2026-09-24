# 后端冗余清理交付记录

日期：2026-09-24。对应前一份审查报告的 12 项问题。代码已修改，未提交 Git。

## 实际改动与位置

| 编号 | 范围 | 当前代码位置 | 处理结果与兼容约束 |
| --- | --- | --- | --- |
| 1 | 权限条件比较 | [PermissionConditionComparison](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/PermissionConditionComparison.java:19) | 按钮与 USER_FIELD 列表权限共用比较器。空值取反失败关闭、角色 IN 按交集、纯字符串保持字典序；SQL 数据字段编译保持原规则，旧 SQL 的逗号分隔集合输入仍可用。 |
| 2 | 任务列表入口 | [TaskListQueryService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskListQueryService.java:38)；[TaskListViewMapper](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskListViewMapper.java:12) | 删除旧 /api/task/done 及 TaskService/TaskServiceImpl 的已办方法。新列表统一在应用服务内选择读模型或实时回退，共用基础字段映射；旧待办保留自身名称、时间、授权和加签范围。回退分页使用 long 偏移，避免极大页码溢出。 |
| 3 | 发布 BPMN 读取 | [PublishedBpmnReader](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/instance/application/PublishedBpmnReader.java:18) | 实例、详情、进度统一 Model → 部署原文 → 对应发布快照的读取；流自动关闭，按实例定义 ID 定位历史版本。设计器草稿查询仍独立。 |
| 4 | 任务镜像初始化 | [ProcessTaskService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/ProcessTaskService.java:183)；[TaskCandidateNames](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskCandidateNames.java:10) | 两个 createTask 重载共用初始化；重复事件判断、转办恢复、事务和最终投影时序保留。候选组名称共用解析，空组名称回退保持兼容。 |
| 5 | 版本指纹 | [EntityVersionFingerprint](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/application/EntityVersionFingerprint.java:19) | 冻结与采集共用摘要材料及 SHA-256。字段清单、null 处理、键排序、集合顺序、字符编码和关系 dataKey 回退不变，固定摘要测试校验兼容。 |
| 6 | 表单结构与引用 | [FormNodeStructurePolicy](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/FormNodeStructurePolicy.java:8)；[子表单引用解析](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:1276) | 编辑与发布共用节点种类、8 层上限和父子约束；子表单别名仅解析一处，严格发布校验与旧引用缺版本探测分别保留。 |
| 7 | UI 事件清单 | [UiEventBindingApplicability](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventBindingApplicability.java:16) | FORM_EVENTS、LIST_EVENTS 只维护一份不可变集合，配置服务复用；FIELD/BUTTON 等目标限制保持原状。 |
| 8 | 实体字段映射 | [EntityFieldViewMapper](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityFieldViewMapper.java:9) | 共用无 IO 的基础 DTO 映射及系统字段能力。选项、文件、关系仍由原入口补充，实体详情旧 JSON 回退及保存结构化选项同步保持原状。 |
| 9 | 字段版本差异 | [EntityVersionDiffService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityVersionDiffService.java:220) | EntityField 适配为 DTO 后进入唯一比较算法；历史缺失长度/精度/列名的兼容、系统字段过滤和完整字段 DDL 预览保留。 |
| 10 | 配置迁移转换 | [ConfigMigrationPackageViews](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationPackageViews.java:9)；[SlaUserReferenceRewriter](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/SlaUserReferenceRewriter.java:14) | 导入导出摘要各维护一份，SLA 嵌套 userId/userIds 共用遍历。身份转换方向及校验异常仍由各入口负责，摘要查询仍只读取轻量字段。 |
| 11 | 菜单树 | [MenuTreeAssembler](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/application/MenuTreeAssembler.java:9) | 角色与菜单服务共用树组装；权限过滤、孤儿节点处理和根排序保持原状。重复 ID 的原入口策略暂保留：角色抛错、菜单取首条；输入仍为每次查询的新对象。 |
| 12 | 无调用私有方法 | 见原审查报告第 12 项 | 删除已核对的 18 个无生产调用私有方法及专用导入。删除只验证退役解析器的 3 项反射测试；保留完成态节点测试，并通过实际运行态发布版本测试验证表单行为。 |

## 旧已办接口退役

已删除 `/api/task/done` 列表端点及专用查询、历史任务 DTO 转换方法。仓库检索确认桌面端、移动端、共享 API 和压测均使用 `/api/process-task/done`；仓库外调用无法由本地代码验证。新已办列表的读模型关闭/未就绪回退仍在，旧待办及其他任务操作入口继续按原协议运行。

这次不再修补已删除旧已办的分页缺陷，也没有保留一份不可达的旧查询实现。

## 需要明确的行为调整

- USER_FIELD 条件与按钮规则统一：缺失字段不再命中 NE/NOT_IN 等非判空条件；角色集合按交集判断 IN；`"10" < "2"` 采用字典序，显式 Number 输入仍按数值比较。
- 三个运行态 BPMN 入口都能在部署资源缺失时读取对应历史发布快照；不会选择新版本或当前草稿。
- 列表回退的极大页码返回空记录和正确 total，不再发生 int 偏移溢出。
- 其余抽取保留既有事务、身份授权、选项兼容、哈希协议和发布版本约束。

## 测试结果

修改前选取相关模块测试建立基线；新增权限一致性测试先在旧实现上复现了三类失败，再修改实现。全量测试使用当前工作区的独立源码副本，避免工作区其他构建刷新 target 目录；本次相关源码与该副本逐文件核对一致。

全量命令：在 `workflow-server` 下执行 `mvn -o test`。

- **BUILD SUCCESS**，总计 4,014 项：**3,741 通过，0 失败，0 错误，273 跳过**。
- 跳过项来自 Docker 不可用、专用 MySQL 环境变量/系统属性未配置以及手工集成测试；不能据此宣称已完成真实 MySQL/PostgreSQL 环境的全部验证。
- 初次全量执行的 HTTP 测试被沙箱禁止绑定本机端口。获得沙箱外执行权限后，8 项本机 HTTP 测试及其余模块全部通过。
- 全量通过后，新增转办恢复补充测试，再执行 `mvn -o -pl workflow-app -am -Dtest=ProcessTaskCreationTest,TaskInboxDatabaseTest -Dsurefire.failIfNoSpecifiedTests=false test`，**18 项通过，0 失败**（6 项任务创建测试、12 项 H2/Flowable/MyBatis 真实事务测试）。补充测试验证旧结果字段显式清空，且不重新插入镜像或初始化 SLA。

| 模块 | 全量测试项数 | 跳过 | 失败/错误 |
| --- | ---: | ---: | ---: |
| workflow-entity | 905 | 0 | 0 |
| workflow-process | 499 | 0 | 0 |
| workflow-app | 1761 | 209 | 0 |
| workflow-admin | 189 | 0 | 0 |
| workflow-migration | 132 | 0 | 0 |
| workflow-embed | 314 | 0 | 0 |
| workflow-open-api | 63 | 0 | 0 |
| workflow-db-migrator | 71 | 64 | 0 |
| biz-project、database、http、outbox | 80 | 0 | 0 |

关键回归包括：

- [权限空值、角色集合和数值/文本比较](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/test/java/com/workflow/entity/permission/application/PermissionConditionParityTest.java:21)
- [持久化指纹固定摘要兼容](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/test/java/com/workflow/entity/version/application/EntityVersionFingerprintTest.java:11)
- [BPMN 优先级、历史版本、UTF-8、异常关流](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/test/java/com/workflow/process/instance/application/PublishedBpmnReaderTest.java:16)
- [读模型与回退、筛选分页、身份范围](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/test/java/com/workflow/process/task/application/TaskListQueryServiceTest.java:20)
- [双入口初始化、重复事件、空组回退、转办恢复](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/test/java/com/workflow/process/task/application/ProcessTaskCreationTest.java:28)
- [SLA 引用往返与异常传播](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/test/java/com/workflow/migration/application/SlaUserReferenceRewriterTest.java:7)
- [H2/Flowable/MyBatis 的授权分页、转办、投影及事务回滚](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-app/src/test/java/com/workflow/process/task/TaskInboxDatabaseTest.java:49)
- [旧实例绑定历史发布版本](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-app/src/test/java/com/workflow/process/runtime/ProcessProgressRuntimeServiceTest.java:380)

已有表单节点、UI 发布、事件范围、字段保存、版本差异、菜单权限、流程自动跳过、迁移导入导出及架构测试也包含在全量回归中。

## 数据库迁移

新增迁移文件：无。修改迁移文件：无。删除或重命名迁移文件：无。没有执行 `flyway repair`，未改写历史迁移。`git diff --check` 通过。
