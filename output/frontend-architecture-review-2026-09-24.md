# 前端架构、重复实现与大文件拆分审查

审查日期：2026-09-24。基于提交 `c7318b5f` 的本地代码。范围包括 `workflow-web/src`、`workflow-mobile/src`、三个公共包及构建/CI 配置；源文件盘点为 544 个、131,817 行（含模板、样式、注释、生成常量，不含测试和构建产物）。采用目录依赖与重复片段检索，再追踪关键调用链；没有逐行证明所有前端功能无缺陷。

本报告保留整改前审查结论与当时行号；当前实施及验证见 [整改验收说明](./frontend-refactor-progress-2026-09-24.md)。原审查阶段新增本报告和[问题复现脚本](/Users/dawei/Documents/ddup/ai/flow/output/frontend-audit-reproductions-2026-09-24.mjs)，当时未修改业务源码；证据脚本仅适用上述基线。Flyway 迁移文件：新增、修改、删除均无。

## 结论与已有基础

已有共享核心 `workflow-core`、共享 API `workflow-api` 和移动 UI 包，方向合理。主要问题是共享能力接入不完整：同一事件协议和流程状态，在页面中仍各自解释；大型页面抽过一部分 UI，却继续承担大量状态、持久化和配置转换。

以下情况不计为冗余：
- 仅转发公共包的适配入口，仍承担稳定导入路径或宿主依赖注入。
- PC 与移动端不同的控件、布局和交互。
- 构建生成并同步的字段定义常量。
- 用户手册、操作说明等内容型大文件；行数大不直接等同于业务架构有问题。

确认 9 类值得处理的问题，其中前 5 类已用当前源码方法做确定性复现。P1 表示应先处理的数据正确性问题；P2 表示可复现功能缺陷或应安排的结构治理；P3 为开发体验和构建边界优化。

## 1. P1：字段回填存在三份解释逻辑，审批入口会读取错误路径

**位置**
- [EntityDataFormDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/components/EntityDataFormDialog.vue:527)：编辑弹窗效果处理；路径读取在第 656 行。
- [EntityApprovalDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/components/approval/EntityApprovalDialog.vue:903)：审批弹窗效果处理；路径读取在第 964 行。
- [fieldEvents.js](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-core/src/shared/form-runtime/fieldEvents.js:33)：已有共享 `applyRuntimeFieldEffects`。

**已复现的问题**

输入 `effect.data = { form: { amount: 100 } }`、`targetPath = "form.amount"`，当前记录 `amount = 7`：
- 编辑弹窗与共享实现回填 100。
- 审批弹窗提前剥离源数据路径中的 `form.`，读取到 `undefined`，随后覆盖原来的 7。

这不是虚构协议：[UiEventValueMapper.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventValueMapper.java:63)按完整目标路径构造结果，[UiEventRuntimeService.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/ui/application/UiEventRuntimeService.java:1164)将映射结果和 mappings 一并放入 FIELD_MAPPING 效果。

此外，共享实现已处理 `clearOnEmpty=false` 和异步确认后的请求有效性检查，两个弹窗未完整接入这些规则。

**建议**
- 先以回归测试固定正确路径，再让两个弹窗复用共享字段映射实现。
- 明确区分“源结果路径”和“记录目标路径”；只对目标记录路径剥离 `form./data.`。
- 共享纯协议和覆盖策略，宿主注入记录读写、确认弹窗、字段名称、路由等能力。
- 保留效果顺序、异步覆盖确认、旧格式 result.data 兼容及各弹窗自己的关闭行为。

**必要测试**：form/data/无前缀、嵌套字段、ALWAYS/IF_EMPTY/CONFIRM、取消覆盖、clearOnEmpty=false、记录切换后迟到事件不得回填。

## 2. P2：自定义按钮执行流程分散，移动端遗漏确认和非字段效果

**位置**
- [ProcessDetail.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-mobile/src/pages/ProcessDetail.vue:198)：移动端 handleAction，第 204 行读取 `item.confirmMessage`。
- [form-actions.js](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-core/src/shared/form-actions.js:150)：统一按钮协议为 `confirm.enabled / confirm.message`。
- [EntityApprovalDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/components/approval/EntityApprovalDialog.vue:836)：PC 按照统一协议确认。
- [MobileActionBar.vue](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-mobile-ui/src/workflow/MobileActionBar.vue:1)：动作对象原样传出，没有字段转换。

**已复现的问题**

用共享 normalizeCustomButton 生成 `confirm.enabled=true` 的按钮，执行移动端真实 handleAction：确认次数 0，业务执行次数 1。服务端 DTO 同样返回 confirm 对象，见 [FormActionRuntimeDTO.java](/Users/dawei/Documents/ddup/ai/flow/workflow-server/workflow-entity/src/main/java/com/workflow/entity/form/api/response/FormActionRuntimeDTO.java:28)。

移动端还只调用字段效果处理器；对 MESSAGE、OPEN_ROUTE、CLOSE_FORM、REFRESH_PARENT、DOWNLOAD_TASK 没有与 PC 对等的分发。这一项为静态调用链确认，尚未做移动浏览器端到端测试；应先明确各效果在移动端的支持范围。

**建议**
- 抽出统一的动作执行流程：占用执行锁 → 确认 → 校验 → 执行 → 按顺序消费效果 → 释放锁。
- PC/移动端分别提供 confirm、message、navigate、close、refresh 适配，不把 Element Plus 或 Vant 放入共享核心。
- 复用既有动作锁和 requestId 生成方式，避免重构后重复提交。
- 移动端不支持的效果应明确阻止配置或返回可解释的提示，不能静默遗漏。

**必要测试**：确认与取消、确认期间连点、页脚与插槽同时点击、校验失败不发请求、效果顺序、关闭后的刷新、动作解析失败的既有兜底。

## 3. P2：流程进度页重复实现 BPMN 高亮，状态优先级已不同

**位置**
- [ProcessProgress.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/ProcessProgress.vue:50)：使用 VueBpmnViewer，但不传 progressData，由页面自己操作 viewer。
- [ProcessProgress.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/ProcessProgress.vue:349)：页面再次实现节点、连线、徽标样式。
- [VueBpmnViewer.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/VueBpmnViewer.vue:304)：公共查看器已经实现这些功能。

**已复现的问题**

同一节点同时出现在 completedNodes 和 activeNodes 时：
- ProcessProgress 优先 completed，显示已完成。
- VueBpmnViewer 优先 active，正确支持回退后重新经过该节点；还支持 terminated。
因此回退重审的流程在不同入口会显示不同状态。

**建议**
页面只负责加载进度、业务明细和错误状态，将 progressData 传给公共查看器；删除页面中的重复高亮/徽标/连线实现。状态选择可以进一步抽为纯函数，供其他流程图入口使用。保留当前缩放、全屏、详情点击、自动布局与异步导入队列行为。

**必要测试**：活跃与历史节点重叠、终止节点、已执行连线、快速切换流程 XML、导入后只按最新进度渲染。

## 4. P2：首页四类列表各自加载，缺少统一的请求时序控制

**位置**
- [Home.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/Home.vue:802)：待办加载。
- [Home.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/Home.vue:884)：已办加载；我发起的在 901 行，知会在 919 行。
- [Home.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/Home.vue:766)：筛选可重复触发加载。
- [inbox.js](/Users/dawei/Documents/ddup/ai/flow/workflow-mobile/src/inbox.js:15)：移动端已有分列表状态和 generation 防过期响应实现。

**已复现的问题**

连续查询 A、B，B 先返回并展示新数据，A 后返回又将列表覆盖为旧数据。四个 PC 加载函数还共用一个 loading，跨页签并行刷新时，一次请求结束可能提前结束另一次请求的加载提示。

**建议**
- 抽取 `useWorkInbox`，各列表分别保存数据、错误、页码、loading 和请求代次。
- 可共享“最新请求才能更新状态”的规则及分页结果规范；PC 替换分页、移动端追加分页仍分别处理。
- 首页只协调页签、查询表单和动作后的失效刷新。
- 复用现有 API；这里不需要恢复已下线的旧已办接口。

**必要测试**：A/B 乱序、旧请求失败不覆盖新成功、切换页签、并发刷新、办理后列表和统计刷新；待办能力加载也必须绑定对应请求代次。

## 5. P2：列表设计器的多步骤保存忽略子步骤失败

**位置**
- [EntityListConfigDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:2112)：saveListMetadata。
- [EntityListConfigDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:2155)：saveScopeBindings 在取消确认或失败时返回 false。
- [EntityListConfigDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:2213)：saveAll 依赖上述返回值决定是否继续。

**已复现的问题**

saveScopeBindings 返回 false 后，saveListMetadata 仍更新元数据基线、返回 true，并显示“数据规则绑定已立即生效”。这会混淆“元数据保存成功”和“权限规则已生效”，批量保存也可能继续后续步骤。

**建议**
把保存结果明确为成功、取消、失败或部分成功，外层必须检查每个步骤；只有相应步骤成功才能更新对应基线和提示。已有元数据写入不等于整体成功，也不能在前端假装回滚。

特别注意：数据规则绑定保存后立即生效；列、按钮等草稿发布后生效。拆成模块时仍需明确保留这两种生效方式，不能为了统一接口而混在一起。

**必要测试**：规则确认取消、规则 API 失败、元数据成功但规则失败、部分字段已保存后下一项失败、重试时 revision 与脏状态一致。

## 6. P2：错误提示去重机制已存在，但部分入口绕过它

**位置**
- [request.js](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-api/src/request.js:17)：notifyRequestError 通过 notificationShown 去重；请求失败默认会提示。
- [index.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/shared/request/index.js:21)：PC 已提供 showRequestError。
- [EntityDataFormDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/components/EntityDataFormDialog.vue:649)：编辑弹窗使用去重入口。
- [EntityApprovalDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/components/approval/EntityApprovalDialog.vue:894)：审批按钮 catch 再次直接 ElMessage.error。
- [ProcessDetail.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-mobile/src/pages/ProcessDetail.vue:210)：移动按钮 catch 再次 showFailToast。
- [uiConfig.js](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-api/src/uiConfig.js:86)：按钮执行 API 未指定 silentError。

**影响**
同一个失败经过请求层与页面层，会重复发出提示；错误处理方式还容易随着复制代码继续分叉。不同 UI 库可能叠加或替换已有提示，但重复调用已可由调用链确认。

**建议**
页面统一通过去重接口兜底；需要完整接管错误的流程显式使用 silentError 并自行处理。保留本地校验错误的展示，不能简单删除所有 catch 提示或全局静默请求。

**必要测试**：一次网络失败只通知一次、本地校验仍有提示、取消确认不提示失败、服务器字段校验仍定位到正确字段。

## 7. P2：大文件有明确拆分空间，重点是拆职责和状态所有权

下表中的新模块名是建议名称，不代表仓库已有。行数包括模板、脚本和样式；拆出 CSS 只能作为整理，不算完成业务拆分。

| 文件与规模 | 建议拆分 | 父级应保留/不能打散的边界 |
| --- | --- | --- |
| [EntityFormDesignByEntity.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:1057)，5,349 行 | 节点转换 model、子表单/子列表发布绑定、节点数据源绑定、设计会话与保存发布；属性 UI 按区域拆组件 | 唯一表单草稿、选中节点、revision、基线和保存发布顺序 |
| [NodeConfigPanel.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:1929)，5,293 行 | 办理人/会签、知会、节点表单、SLA/跳过规则、服务任务/连线状态等编辑器；BPMN 属性读写适配 | 当前 BPMN element、modeler/commandStack 和节点切换会话 |
| [EntityListConfigDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:925)，2,863 行 | 列配置、按钮配置、数据规则绑定、预览、发布与草稿会话 | 各资源 revision、单项/批量保存顺序、立即生效与发布生效的区别 |
| [EntityDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityDesign.vue:938)，2,400 行 | 数据权限工作区及规则 model、字段属性 UI、附件规则编辑器 | 实体与字段总保存、已存在的字段草稿保存/校验 composable |
| [RelatedContentConfigDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentConfigDialog.vue:854)，2,244 行 | 向导各步、目标目录加载、接口动作配置、试运行结果 | 单一 editor 草稿、步骤校验、关系派生约束、validate → save 顺序 |
| [Home.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/Home.vue:588)，1,713 行 | 四类列表数据状态、列表呈现、任务与实例动作协调 | 当前页签、筛选入口、办理后的失效刷新；提交前实时能力检查 |
| [EntityList.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue:646)，1,607 行 | 实体发布与结构变更、发布历史、流程绑定、状态配置四个工作区 | 实体列表查询和各工作区完成后的刷新 |
| [EventBindingEditor.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/ui-config/EventBindingEditor.vue:549)，1,605 行 | 事件步骤转换 model、范围/目标选择、步骤与映射编辑器、绑定会话 | owner/target 身份、expectedRevision、事件链顺序与继承语义 |
| [EntityDataList.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:232)，1,499 行 | 数据查询、运行时元数据加载、列表按钮效果、表单/审批弹窗启动、版本能力加载 | 宿主页面上下文与对外 reload 契约、当前查询及选择状态 |

### 7.1 表单设计器：已有子组件，应进一步拆业务层

已有 FormDesignerSettingsDrawer、FormNodeDataSettings、FormNodeStateConditions 等，不建议再做一次同名功能模块。

优先边界：
1. [nodeToField](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:2921)、第 3051 行 fieldToNodePayload、第 3188 行 restoreFieldConfig、第 3277 行 buildSerializedFieldComponentProps：整理为编辑模型与持久化模型转换，尽量为纯函数。
2. [子表单/子列表目录与发布绑定](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:2162)：单独管理目录、发布快照、参数和加载状态。
3. [节点数据源绑定](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:2695)：集中校验和规范化，复用现有公共规则。
4. [单节点保存](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:4357)、[发布入口](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:4475)、[整体保存](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue:4613)：由一个设计会话负责协调。

保存链当前包含元数据创建/更新、子发布绑定检查、父节点先于子节点排序、replaceFormNodes、失败后刷新草稿状态。不要把这些步骤分别藏在几个会自动保存的子组件里。自定义渲染方式还会影响是否保存默认节点，必须保留。

测试重点：转换往返、节点顺序与深度、单节点保存、revision 冲突、元数据已成功但节点失败、脏状态与发布阻断、自定义渲染模式。

### 7.2 节点配置面板：先分领域配置，再集中 BPMN 写入

- [角色/组织/办理人目录加载](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:2542)属于可独立管理的目录服务。
- [会签更新](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:3519)、[办理人配置](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:4186)适合一个领域编辑器，与空办理人策略和既有 process-config 规则协作。
- [表单绑定](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:4021)、[自动跳过](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:3771)、[SLA](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:3834)可各自拆出。
- [扩展属性写入](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:4077)应有集中边界，保留 modeler 命令栈的撤销/重做语义。
- [连线状态保存](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue:4917)还会更新后端状态映射，不是普通的 UI 字段赋值，必须保留其他连线映射的合并行为。

不能把整份巨型 reactive 状态塞进一个同样巨大的 composable 再让所有子组件任意改。建议父级拥有节点会话，子编辑器只提交该领域的配置变化；节点切换后的旧目录响应需要失效。

测试重点：切换节点隔离、办理人模式互斥、会签变量命名、撤销重做、XML 导出与再次导入、状态映射不覆盖其他连线。

仓库已有[节点面板拆分计划](/Users/dawei/Documents/ddup/ai/flow/docs/maintainability-extraction-plan-2026-07-27.md:13)，应在该计划上细化落实，避免出现第二份互相冲突的拆分安排。

### 7.3 列表设计器：资源模块可以独立，保存会话仍要统一协调

- [列配置合并](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:1538)与第 1990 行 normalizeFieldForSave，可抽列配置模型。
- [按钮保存/排序/删除](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:1837)可抽动作编辑模块。
- [数据范围绑定](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:2155)适合独立模块，保留 EXPLICIT_ALL 的原因确认。
- [预览查询](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue:2338)、第 2390 行运行时代码预览可抽只读模块。
- saveAll、revision/dirty 基线与发布阻断由统一会话协调，并先修复第 5 项的返回结果问题。

测试重点：排序、单列保存与全量保存一致、规则取消不报整体成功、部分成功重试、丢弃草稿、发布前确认没有未保存内容。

### 7.4 实体设计器：权限工作区最适合先拆

[数据权限管理](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityDesign.vue:1423)一直延伸到规则保存，含列表绑定摘要、条件编辑、预览、旧配置兼容，与字段拖拽编辑可以分开。建议提取 EntityPermissionWorkspace + permissionEditorModel；[附件条目编辑](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityDesign.vue:1774)可再拆组件。

已有 useEntityFieldDraftSave、useEntityValidationRules，应继续复用。实体字段“全部保存”在第 1395 行，与单字段保存协调，不应因拆 UI 重新产生两套字段序列化。

测试重点：系统实体只读约束、整体/单字段保存、权限规则编辑往返、规则预览和绑定摘要、字段类型切换的校验清理。

### 7.5 关联内容配置：拆向导步骤，共享一个受控草稿

[目录加载](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentConfigDialog.vue:1285)、[特殊模式与接口动作](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentConfigDialog.vue:1483)、[试运行](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentConfigDialog.vue:1659)是三个独立职责；第 1713 行保存负责本地检查、服务端规范化校验、最终写入。

拆分时要保留：一对一/一对多决定目标内容类型；读取数据接口和执行动作接口的目录不能混用；映射依赖已发布参数声明；自定义组件版本与 artifact digest 联动。目标内容 schema 已有 sequence 检查，应保留并把相同保护覆盖到关联的目录请求。

测试重点：切换目标后旧选项失效、向导步骤保留编辑内容、试运行结果归属当前草稿、服务端 normalizedConfig 写入、取消不保存。

### 7.6 事件编辑器：转换与校验是最容易先拆的部分

[normalizeStep / mappingRows](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/ui-config/EventBindingEditor.vue:840)与[serializeCondition / cleanMappings / serializeStep](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/ui-config/EventBindingEditor.vue:1058)可以先整理为纯模型；需要 rowKey 时注入行 ID 生成器。第 1092 行 save 只负责协调已经校验过的 payload。

应保留 REPLACE/INHERIT/DISABLE 语义、主处理步骤数量和无条件限制、legacyListQuery 兼容、输出映射 clearOnEmpty/overwrite、owner/target 范围校验。

测试重点：旧格式到新格式的兼容往返、步骤排序与条件、继承方式、自定义按钮主步骤限制、expectedRevision 冲突。

### 7.7 首页、实体目录页与实体数据页

- 首页按第 4 项先收敛请求时序，再把表格展示和动作交互拆开；待办与已办的操作权限不同，不宜硬塞成满是模式分支的万能表格。
- [实体发布](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue:1019)到结构操作重试、[发布历史](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue:1118)、[流程绑定](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue:852)、[状态配置](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue:1211)可分工作区。结构变更结果与重试入口要保留；不能将“发布请求返回”和“数据库结构操作完成”合并为同一状态。
- [实体记录查询](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:1010)、[列表按钮事件](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:1152)、[表单弹窗启动](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:1237)、[版本能力查询](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:522)是拆分点。已有 useEntityDataSelectionState 继续保留。Embed 和关联内容会复用页面，不能改变 [reload](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue:1452) 的参数、完成时机和错误传播。

### 7.8 其他大文件的处理优先级

- 两个表单弹窗（1,116 / 1,220 行）：优先合并第 1、2、6 项的协议处理；编辑与审批的提交、下一审批人、历史只读语义保留各自协调。
- [embedRuntimeController.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/embed/runtime/embedRuntimeController.js:173)（1,202 行）：已有依赖注入和统一状态机。可以后续抽导航、提交与错误规范化；先保留单一会话状态、心跳、destroy/logout 确认，避免拆出并行状态机导致销毁时序失控。
- 用户手册等内容文件先按章节整理即可，不与保存发布链的大文件治理同等排序。

## 8. P2：桌面端自动回归与可维护性约束未进入常规 CI

**位置与证据**
- [ci.yml](/Users/dawei/Documents/ddup/ai/flow/.github/workflows/ci.yml:79)：桌面功能与配置测试已注释。
- [ci.yml](/Users/dawei/Documents/ddup/ai/flow/.github/workflows/ci.yml:92)：可维护性预算检查已注释。
- [ci.yml](/Users/dawei/Documents/ddup/ai/flow/.github/workflows/ci.yml:110)：仍有两端构建、公共包/移动测试及移动浏览器测试，不能笼统说“没有 CI 测试”。
- [security-audit.yml](/Users/dawei/Documents/ddup/ai/flow/.github/workflows/security-audit.yml:5)：包含 test:web 的工作流仅 workflow_dispatch 手动触发。
- [maintainability-budget.mjs](/Users/dawei/Documents/ddup/ai/flow/workflow-web/scripts/maintainability-budget.mjs:103)：前端预算只扫描 workflow-web/src，没有覆盖移动端和公共包。

本次实际运行预算审计失败，其中 **28 个桌面前端文件超过当前预算**；完整脚本还报告后端问题，不能把完整 97 项都记为前端问题。EntityDesign 的 2,400 行仍在旧预算内，但职责分析表明仍可拆，说明行数只能辅助判断。

部分现有测试用 readFileSync + assert.match 检查源码结构，例如 [bpmnViewerStability.spec.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/__tests__/bpmnViewerStability.spec.js:1)；这类检查能保留约束，但不能替代实际执行断言。本次该脚本通过，仍能复现第 3 项的状态差异。

**建议**
常规 PR 至少执行桌面关键行为测试和共享协议测试；将新增大文件、现有文件继续增长作为轻量治理检查，历史债务按拆分计划逐步缩减。扩大预算范围到移动端和公共包，区分生成文件、内容文件与业务模块。不要仅提高所有阈值来获得通过。

## 9. P3：共享包的构建仍依赖桌面目录，开发入口缺少统一监听

**位置**
- [build-module-package.mjs](/Users/dawei/Documents/ddup/ai/flow/scripts/build-module-package.mjs:11)：core 构建从 workflow-web 的 generated 目录复制字段定义。
- [mobile-extensions.mjs](/Users/dawei/Documents/ddup/ai/flow/scripts/mobile-extensions.mjs:3)：移动扩展构建复用桌面 build 工具及 manifest 目录。
- [package.json](/Users/dawei/Documents/ddup/ai/flow/package.json:16)：公共包构建先执行桌面 extensions:generate。
- [package.json](/Users/dawei/Documents/ddup/ai/flow/packages/workflow-core/package.json:7)：导出入口指向 dist。
- [package.json](/Users/dawei/Documents/ddup/ai/flow/package.json:23)：dev:web/dev:mobile 只启动应用，没有启动公共包 watch。

**影响**
运行时已做平台边界隔离，但构建层仍要求公共包和移动端了解桌面目录结构。只运行当前根目录 dev 命令时，修改公共包 src 不会自动更新其 dist；开发者需要另外启动公共包 dev，冷启动缺 dist 也依赖额外构建步骤。

**建议**
先提供统一开发编排，明确初始构建和各公共包 watch 的生命周期；再把扩展协议、manifest 与发现/校验工具逐步移到中立目录。继续通过公开包入口消费，保留现有平台边界检查，避免用跨包源码别名掩盖依赖问题。

这属于构建耦合和开发体验建议，不是说移动端运行时混入了 Element Plus。

## 建议实施顺序

1. 将五组复现场景转成“正确行为”的正式回归测试，先修复回填、确认、状态、查询时序和部分保存错误。
2. 合并共享事件效果与动作流程，统一错误通知；保留平台 UI 适配。
3. 先抽纯模型：事件步骤转换、表单节点转换、列表列配置规范化；用输入输出和往返测试锁定数据形状。
4. 再拆领域组件与目录加载，优先表单设计器、节点面板、列表设计器；保存发布会话保持单一所有者。
5. 恢复关键 CI 检查并补统一开发监听；后续再拆 Embed 会话相关模块。

重构与行为修复应有独立、可审查的小批次；每批检查网络 payload、revision、保存/发布顺序和既有错误兜底，不要把“减少一个文件的行数”作为完成标准。

## 本次验证

| 检查 | 结果 |
| --- | --- |
| `node --test packages/*/tests/*.test.mjs` | 22 项通过 |
| 在 workflow-mobile 执行 `node --test tests/*.test.mjs` | 13 项通过 |
| 在 workflow-web 执行 `node src/components/__tests__/bpmnViewerStability.spec.js` | 通过 |
| [源码复现脚本](/Users/dawei/Documents/ddup/ai/flow/output/frontend-audit-reproductions-2026-09-24.mjs) | 5 组问题均复现 |
| 在 workflow-web 执行 `node scripts/maintainability-budget.mjs` | 失败；前端超预算 28 个文件 |

复现脚本提取 Vue 组件的实际方法并注入内存记录、假接口和可控 Promise，没有复制被审查的业务算法，也没有请求真实业务服务。它的断言成功表示当前缺陷存在，不能写成“5 项功能测试通过”。

本次未执行全量桌面测试、生产构建或浏览器端到端验收；不据此宣称整个前端已验证正确。分析和复现使用 Node.js 22.22.1。

