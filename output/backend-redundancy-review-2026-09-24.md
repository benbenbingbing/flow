# 后端冗余与重复实现分析

审查日期：2026-09-24。依据当前工作区，包含尚未提交的改动。

对 `workflow-server` 的 1,546 个生产 Java 文件、约 28.9 万行源码（含注释）做了结构检索、重复片段筛选和调用关系检索，再人工复核下面的热点。不是对全部源码的逐行审查。本次仅新增这份报告，没有修改业务代码，没有运行编译、应用测试或数据库验证；下文的行为差异来自源码分析，“验证重点”是后续修改时应完成的工作。

最应优先处理的是权限规则求值和任务查询：已经存在同类输入得到不同处理结果的情况。其他重复主要集中在流程元数据读取、表单发布约束、版本指纹、DTO 组装和重构后留下的私有方法。

| 编号 | 问题 | 优先级 | 判断 |
| --- | --- | --- | --- |
| 1 | 权限条件求值维护两份 | 高 | 空值、集合和有序比较已有差异 |
| 2 | 待办、已办查询存在多条实现路径 | 高 | 旧已办查询先分页再筛选 |
| 3 | BPMN 读取维护三份 | 中 | 发布快照回退没有统一 |
| 4 | 创建任务镜像的两个重载大段重复 | 中 | 初始化和身份展示需要多处维护 |
| 5 | 版本冻结与采集分别维护相同指纹算法 | 中 | 两端必须长期保持一致 |
| 6 | 表单结构规则及子表单引用解析重复 | 中 | 编辑与发布可能发生规则漂移 |
| 7 | UI 事件适用范围维护多份 | 中 | 事件可配置与可继承规则分散 |
| 8 | 实体字段 DTO 组装维护两份 | 中 | 选项回退策略不同，调用场景也不同 |
| 9 | 字段版本比较为不同输入类型复制算法 | 中 | 新增比较属性需修改多处 |
| 10 | 配置迁移的摘要组装、SLA 引用遍历重复 | 低至中 | 可在模块内提取纯逻辑 |
| 11 | 菜单树构建维护两份 | 低 | 重复 ID 处理已有差异 |
| 12 | 18 个未发现生产调用的私有方法候选 | 低 | 其中一项仍有反射测试 |

## 1. 权限条件求值维护两份，而且语义已经不同

位置：

- [EntityActionRuleEvaluator.compare](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/EntityActionRuleEvaluator.java:386)
- [PermissionSqlBuilder.evaluateUserField / compare](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/PermissionSqlBuilder.java:739)

两者都处理 `EntityActionRuleDTO.RuleNode`，都实现了 EQ、NE、IN、NOT_IN、CONTAINS、GT 等比较，并复制了 `equalsValue`、`contains`、`isEmpty` 等方法。SQL 构建器处理 `USER_FIELD` 时，也是在 Java 中求值，再生成 `1=1` 或 `1=0`。

可以从源码直接推导以下差异：

| 同类条件 | 按钮规则求值器 | SQL 构建器的 USER_FIELD 求值 |
| --- | --- | --- |
| 当前用户 deptId 为 null，条件为 NE "d1" | false，非判空操作先拒绝空值 | true，取反 equalsValue(null, "d1") |
| roleIds 为 ["r1", "r2"]，条件为 IN ["r1"] | true，逐项求交集 | false，把整个集合与单个值比较 |
| 当前用户某字符串属性为 "10"，条件为 LT "2" | 按字符串比较，为 true | 尝试数值比较，为 false |

这说明复用同样的规则结构，并没有保证相同的比较语义。并不据此断言已经发生线上越权，但这是比单纯代码重复更应优先解决的问题。

**修改建议：** 在 permission 领域内提取一个无数据库依赖的条件比较器，统一空值、标量/集合、数值/字符串的规则。`USER_FIELD` 的两条路径先复用该比较器；保留 SQL 编译与内存求值各自的执行方式。数据库字段比较还需结合字段类型和数据库方言，不能简单把全部 SQL 判断替换成 Java 判断。

**验证重点：** 用同一组规则输入同时验证按钮结果与 USER_FIELD 生成结果，覆盖空值、NE/NOT_IN、角色集合、数字字符串及 EMPTY/NOT_EMPTY；明确哪些历史配置语义需要兼容。

## 2. 待办、已办查询有多条实现路径

位置：

- [TaskController：/api/task](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/api/web/TaskController.java:28)
- [TaskServiceImpl.getTodoList / getDoneList](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskServiceImpl.java:120)
- [ProcessTaskController：读模型及回退分支](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/api/web/ProcessTaskController.java:88)
- [TaskInboxQueryService.findPage / toVO](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskInboxQueryService.java:35)

当前主要有三条路径：旧 `/api/task` 服务自行组合 Flowable 查询与内存筛选；`/api/process-task` 优先使用 SQL 读模型；读模型关闭或尚未就绪时，又在 Controller 内执行列表获取、VO 转换、过滤和分页。`TaskVO` 的业务数据、身份和 SLA 字段也在多处组装。

**已有具体问题：** `TaskServiceImpl.getDoneList` 在第 182 行统计总数、第 183 行分页，之后才按流程名称、任务名称过滤。假设前 10 条不匹配而第 11 条匹配，第一页可能为空，但 total 仍然是未按名称筛选的总数。旧待办路径则先获取全部可见任务、转换后再做内存分页，数据量大时成本高。

仓库内共享前端 API 当前使用 `/process-task/todo` 和 `/process-task/done`。这有利于收敛，但不足以证明旧 HTTP 接口没有外部调用。

**修改建议：** 让两个 Controller 都只做参数适配，调用统一的任务查询应用服务。旧参数 `processName/taskName/timeRange` 与新参数保留明确映射，不把两个不同名称条件粗暴合成一个 keyword。统一查询服务内保留读模型策略与回退策略，保证二者的筛选、计数、分页、身份范围和排序契约一致；统一结果组装。读模型回填验证完成、外部调用确认迁移后，再考虑退役旧路由和回退实现。

**验证重点：** 名称筛选跨页、total 一致性、候选人/候选组、加签本地任务、历史任务、SLA 时区，以及读模型开启/关闭/未就绪时的结果对照。

## 3. BPMN 原文读取维护三份，回退行为不同

位置：

- [ProcessInstanceService.getBpmnXmlByProcessDefinitionId](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/instance/application/ProcessInstanceService.java:304)
- [ProcessDetailRuntimeService.getBpmnXmlByProcessDefinitionId](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/instance/application/ProcessDetailRuntimeService.java:565)
- [ProcessProgressRuntimeService：读取 BPMN](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/instance/application/ProcessProgressRuntimeService.java:163)

三处都有查询定义、读取 Model、回退部署资源、UTF-8 转换的逻辑。进度服务额外在第 197 行后回退到该部署对应的发布历史快照，其余两份没有这一回退。如果部署资源不可读但发布快照存在，不同入口可能一个有流程图，一个没有。

**修改建议：** 在 process 模块中提取以 `processDefinitionId` 为输入的发布 BPMN 读取组件，统一资源优先级、缺失处理和日志，并集中用 try-with-resources 关闭资源流。严格使用实例所绑定的部署版本；设计器按 processKey 读取草稿属于另一种语义，应保留独立入口。

**验证重点：** Model 缺失、部署资源缺失、历史发布快照存在、同一个 processKey 已发布新版本时，三个运行态入口仍返回同一历史版本的 XML。

## 4. 创建任务镜像的两个重载大段重复

位置：

- [ProcessTaskService.createTask(DelegateTask, ...)](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/ProcessTaskService.java:138)
- [ProcessTaskService.createTask(Task, ...)](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/ProcessTaskService.java:278)
- [TaskInboxProjectionService.synchronizeTask](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/TaskInboxProjectionService.java:41)

两个重载分别面向事件监听与普通查询结果，但流程元数据、候选人/候选组遍历、名称拼接、节点表单查找、时间初始化、insert 和 SLA 初始化基本相同。投影服务又维护了一份候选身份及名称处理逻辑。

**修改建议：** 两个重载先适配为模块内部的任务初始化输入，再共用构造与初始化逻辑；候选身份解析和展示名组装独立复用。普通 Task 路径对转办后旧镜像的恢复、监听路径的重复事件处理必须保留。投影服务仍负责加锁和提交阶段的最终身份同步，不能因去重把这些时序职责删除，也不应在构造镜像时反向触发投影造成循环调用。

**验证重点：** 同一任务重复事件只生成一个镜像；转办清除旧意见/结束时间；候选人和候选组混合；首次创建只初始化一次 SLA；提交前投影与回填结果一致。

## 5. 版本冻结与快照采集各自维护同一指纹算法

位置：

- [EntityVersionScopeFreezer.entitySchemaHash / relationDefinitionHash](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/application/EntityVersionScopeFreezer.java:532)
- [EntityRecordSnapshotService.entitySchemaHash / relationDefinitionHash](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/application/EntityRecordSnapshotService.java:1688)

两份都维护相同的实体身份、字段、关系、关系归属和级联属性清单，并通过按 Map 键排序的 JSON 做 SHA-256。一端生成冻结指纹，另一端比较当前指纹，因此这两份代码承担的是同一份持久化契约。

**修改建议：** 提取 version 领域内的指纹组件，集中定义摘要材料与序列化方式。首次提取只保持原算法等价，不增删字段、不调整集合排序或空值表示；需要改变算法时，显式版本化并设计历史兼容。

**验证重点：** 用已有冻结配置和发布快照作固定样例，确认提取前后摘要完全一致；关系变化仍能被检出，旧版本不会无故被判定为范围失效。

## 6. 表单结构约束与子表单引用解析重复

位置：

- [EntityFormNodeService：节点类型、深度和父子关系规则](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:65)
- [UiConfigReleaseService：重复的类型和关系规则](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java:125)
- [编辑期父子关系校验](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:1932)
- [发布期父子关系校验](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java:6622)
- [readFormReleaseReference](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:1293) 与 [requiresLegacyReleasePin](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java:2696)

编辑服务和发布服务各自维护节点类型、最大深度 8、`TAB_SET -> TAB` 等规则。同一编辑服务内，又两次展开 `props/subFormConfig/componentProps.subFormConfig` 中的各种表单 ID、发布 ID、版本别名。

**修改建议：** 提取表单结构策略，统一节点种类、父子限制、深度规则；保留编辑和发布两处校验入口及各自的数据读取。子表单引用提取为纯解析器，先返回完整或不完整的引用结果，再由调用方分别执行“必须固定版本”或“是否需要补固定版本”的判断，不能直接用会抛异常的严格校验替代旧数据探测。

**验证重点：** 新增节点类型只需修改一份策略；根 TAB、非法父子关系、第 8/9 层、循环引用、各别名优先级、不完整旧引用的探测行为一致。

## 7. UI 事件适用范围维护多份

位置：

- [UiEventBindingService：FORM_EVENTS / LIST_EVENTS / EVENT_SCOPES](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventBindingService.java:95)
- [UiEventBindingApplicability：重复的 FORM_EVENTS / LIST_EVENTS](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventBindingApplicability.java:16)

前者决定配置时允许哪些 owner/target/event 组合；后者决定实体默认事件可以应用到哪些 FORM/LIST 上下文。两者复制同一组事件清单。增加事件时，漏改其中一份就可能出现“允许配置，但继承或发布时不识别”。

**修改建议：** 建立模块内的事件定义目录，描述事件适用页面、目标类型和必要的能力限制，从目录派生校验集合及继承上下文。先集中这两份已经相同的列表；不要直接把 FIELD/BUTTON 专属事件全部提升为 OWNER 通用事件。

**验证重点：** 每个事件在配置校验、ENTITY 默认继承和发布引用处理中的适用页面一致，FIELD/BUTTON 的目标限制不变。

## 8. 实体字段 DTO 组装维护两份

位置：

- [EntityDefinitionService.convertToDTO](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityDefinitionService.java:1399)
- [EntityFieldDefinitionService.convertToDTOWithRelation](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityFieldDefinitionService.java:378)

字段属性、系统字段能力、文件限制、引用类型等映射重复。实体详情在结构化选项为空时回退解析 `optionsJson`，单字段保存返回的映射只读结构化选项。

这里应区分“映射不同”与“已确认用户可见错误”：单字段写入此前会执行 `synchronizeFieldOptions`，会把请求中的旧 JSON 同步成结构化选项，因此不能仅凭映射差异就断言正常保存一定丢选项。但两套映射会使以后新增字段属性和调整兼容逻辑容易漏改。

**修改建议：** 提取基础字段映射和选项解析策略；关系元数据、文件配置通过显式上下文补充。实体详情的批量字段读取应允许预加载选项/关系，避免共享 assembler 后仍每个字段单独查询。保存操作保留结构化选项同步职责，不让 mapper 隐式写库。

**验证重点：** 实体详情与单字段保存返回的基础属性一致；结构化选项、仅旧 JSON、空选项、引用字段、文件字段均有明确行为；批量详情查询数量不增加。

## 9. 字段版本差异算法为不同输入类型复制了两份

位置：

- [EntityVersionDiffService.compareField(EntityFieldDTO, EntityField)](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityVersionDiffService.java:222)
- [compareField(EntityFieldDTO, EntityFieldDTO)](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityVersionDiffService.java:283)
- [convertToFieldDiff 的两个重载](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityVersionDiffService.java:344)

字段名称、必填、唯一、默认值、长度、精度、列名的比较和描述构建基本逐项重复；差异主要是输入来自当前实体字段还是历史 DTO。

**修改建议：** 把两种输入适配为只含比较所需属性的内部快照，再调用唯一的比较函数和 FieldDiff 组装函数。无需为去重让持久化对象继承 API DTO，也不必引入全项目通用反射比较器。

**验证重点：** 相同内容通过“草稿与发布版比较”和“两个历史版比较”得到一致差异；保留历史快照缺少长度/精度/列名时的兼容规则和系统字段删除过滤。

## 10. 配置迁移中的纯转换逻辑重复

位置：

- [ConfigMigrationReadService：导入/导出摘要](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationReadService.java:249)
- [ConfigMigrationPackageService：相同摘要](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationPackageService.java:1534)
- [ConfigMigrationAssetService.rewriteSlaUserReferences](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:769)
- [ConfigMigrationImportApplyService.rewriteSlaUserReferences](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java:645)

摘要的字段清单相同；SLA JSON 的对象/数组遍历和 `userId/userIds` 识别也相同，方向差异已通过 converter 参数表达。

**修改建议：** 摘要提取为模块内 mapper 或有明确字段的响应 record；SLA 引用遍历提取为单一 JSON 重写器，继续由导出、导入分别提供身份转换策略。保持导出本地 ID 转可移植标识、导入目标环境校验的不同职责。摘要查询仍只选择轻量列，不能因复用而加载整个大快照。

**验证重点：** 列表与创建/导入返回的摘要字段一致；SLA 嵌套对象、数组、单用户和多用户引用可往返转换；目标用户缺失仍按原规则阻止导入。

## 11. 菜单树构建维护两份

位置：

- [SysRoleService.buildMenuTree](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/role/application/SysRoleService.java:286)
- [SysMenuService.buildTree](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/menu/application/SysMenuService.java:368)

两处都建立 ID 索引、查找父菜单、追加 children 并排序根节点。前者遇重复 ID 会由 `toMap` 抛异常，后者保留第一条。是否应容忍重复需要统一约定，而不是取决于调用哪个服务。

**修改建议：** 提取 admin 模块内的菜单树构建器；明确根节点、孤儿节点、重复 ID、排序规则和是否修改输入对象。角色范围过滤、失效实体菜单过滤仍由各自服务完成，不能把不同权限的数据选择一并合并。

**验证重点：** 相同菜单输入得到相同树；重复构建不会重复追加 children；覆盖重复 ID、孤儿节点和排序空值。

## 12. 重构后留下的私有方法候选

词法检索发现以下 18 个 private 方法，在所属类中没有发现调用或方法引用。它们不是 Controller 端点或接口实现。对这些方法的删除判断仍应结合完整编译、测试及动态调用约定；不能用“没有 Java 直接调用”判断整个 Spring Bean、公开 Port 或 Mapper 方法无用。

| 类 / 位置 | 方法 |
| --- | --- |
| [MultiInstanceCollectionListener](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/assignment/infrastructure/flowable/MultiInstanceCollectionListener.java:706) | normalizeAssignmentType (706)、mapValue (772)、firstText (784)、nullSafe (810) |
| [RelativeOrgPositionCollectionHandler](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/assignment/infrastructure/flowable/RelativeOrgPositionCollectionHandler.java:668) | firstText |
| [NextApproverSelectionPolicyReader](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/task/application/nextapproval/NextApproverSelectionPolicyReader.java:483) | firstText |
| [ProcessProgressRuntimeService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-process/src/main/java/com/workflow/process/instance/application/ProcessProgressRuntimeService.java:1062) | resolveFormKeyFromBpmn；仍有反射测试 |
| [ConfigMigrationAssetService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java:2367) | exists |
| [UiConfigReleaseService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java:7123) | integer (7123)、booleanFlag (7140) |
| [UiEventBindingService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventBindingService.java:563) | emptyChain (563)、blankToNull (1269) |
| [UiInterfaceExtensionService](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiInterfaceExtensionService.java:2264) | copyDefinition (2264)、writeList (2638)、readList (2666) |
| [EntityVersionConfigurationValidator](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/version/application/EntityVersionConfigurationValidator.java:523) | requireDefinition |
| [DataPermissionEngine](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/DataPermissionEngine.java:588) | and |
| [PermissionSqlBuilder](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/permission/application/PermissionSqlBuilder.java:1264) | orSql |

`resolveFormKeyFromBpmn` 被 [ProcessProgressRuntimeServiceFormTest](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-app/src/test/java/com/workflow/process/runtime/ProcessProgressRuntimeServiceFormTest.java:46) 通过反射调用，但没有生产调用。这类测试即使通过，也不能证明实际运行态走了这段解析逻辑。

**修改建议：** 分模块删除确认退役的方法及其专用 import/依赖；把上述表单解析测试迁到实际使用的解析组件或公开运行态入口，并保留同一测试类中仍有效的其他测试。不要为了维持旧私有方法测试而继续保留生产不可达代码。

**验证重点：** clean 编译与所属模块测试，特别检查反射测试、BPMN 表达式和框架回调。可将 IDE/静态检查发现的未使用私有成员纳入后续代码检查。

## 建议实施顺序与边界

1. 先处理第 1、2 项的行为不一致，增加能证明统一契约的针对性回归测试。第 3 项同时统一运行态 BPMN 的历史版本读取。
2. 再抽取第 4～10 项中的纯算法、映射和规则清单；每次只移动一个职责，保持事务、鉴权、发布版本选择和外部接口不变。
3. 第 11、12 项可作为独立小改动完成，便于评审与回退。

`UiConfigReleaseService` 当前 7,188 行，`UiViewCompositionService` 4,113 行，`ConfigMigrationImportApplyService` 3,862 行。长度本身不等于冗余，但这些类同时承担编排、验证、引用解析和转换，使规则容易被复制。建议先按上述证据提取职责，再考虑进一步拆分；不建议先创建一个大而泛的 CommonService/Utils。

应保留的边界包括：跨模块 Port/Adapter 与内部 Service；请求 DTO 与持久化模型；编辑、发布、运行时的校验入口；SQL 方言适配；尚未完成读模型回填时的回退策略。可以共享其纯规则，不能仅因名字相似或字段相同就合并。

本次迁移文件变更：新增 0，修改 0，删除 0。本报告不涉及改写历史 Flyway 迁移。
