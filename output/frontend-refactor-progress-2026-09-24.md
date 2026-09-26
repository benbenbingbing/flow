# 前端整改与验收说明（2026-09-24）

基线：`c7318b5f`。本轮完成九类问题的一轮整改及职责拆分。主要页面仍有继续拆分空间，本报告不把“抽出模块”解释为所有历史大文件已治理完毕。

## 大文件的实际拆分

行数包括模板、脚本、样式与注释；模块总行数不等于删除量。新增模块均低于 900 行。

| 原入口 | 前 → 后 | 抽出位置 | 本轮边界 | 后续拆分空间 |
|---|---:|---|---|---|
| [EntityFormDesignByEntity.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityFormDesignByEntity.vue) | 5349 → 4876 | [formNodeEditorModel.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/form-designer/formNodeEditorModel.js) | 节点读写协议、组件配置恢复、附件与实体约束；父页面保留草稿和保存协调 | 目录加载、发布绑定、字段属性面板 |
| [NodeConfigPanel.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/NodeConfigPanel.vue) | 5293 → 5131 | [NodeSlaEditor.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/node-config/NodeSlaEditor.vue) | 受控 SLA 编辑；另抽 BPMN 扩展属性读写，修复直接修改旧对象导致撤销失效的问题 | 办理人、会签、知会与表单绑定仍可分步拆分 |
| [EntityListConfigDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityListConfigDesign.vue) | 2863 → 2787 | [listColumnModel.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/list-designer/listColumnModel.js) | 列合并与持久化模型；修正元数据与规则部分保存的反馈及重试 revision | 规则绑定、预览与保存会话 |
| [EntityDesign.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityDesign.vue) | 2400 → 1979 | [useEntityPermissions.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity-design/useEntityPermissions.js) | 权限草稿、候选目录、规则保存与 SQL 预览；字段保存仍由原页面负责 | 字段属性、附件规则 |
| [RelatedContentConfigDialog.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentConfigDialog.vue) | 2244 → 1996 | [RelatedContentTargetStep.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/related-content/RelatedContentTargetStep.vue) | 目标选择步骤、步骤标题、候选字段模型；向导只持有一份草稿 | 其余向导步骤、目录加载与试运行 |
| [Home.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/Home.vue) | 1713 → 1516 | [useWorkInbox.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/home/useWorkInbox.js) | 四类列表独立状态及请求代次；另抽统计卡片 | 列表展示、批量动作 |
| [EntityList.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/EntityList.vue) | 1607 → 1524 | [useEntityPublication.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity-list/useEntityPublication.js) | 发布预览、风险确认、结构重试；首次发布与重新发布复用同一入口 | 历史、流程绑定与状态工作区 |
| [EventBindingEditor.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/ui-config/EventBindingEditor.vue) | 1605 → 1493 | [eventStepModel.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/ui-config/eventStepModel.js) | 编辑模型、行标识、序列化和事件链校验；父级负责会话与请求 | 映射 UI、绑定会话 |
| [EntityDataList.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/EntityDataList.vue) | 1499 → 1459 | [useEntityVersionCapabilities.js](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/entity/list/useEntityVersionCapabilities.js) | 版本能力状态、重置和迟到响应隔离；保留 Embed reload 完成契约 | 查询、元数据与按钮启动协调 |
| [ProcessProgress.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/views/ProcessProgress.vue) | 996 → 512 | [VueBpmnViewer.vue](/Users/dawei/Documents/ddup/ai/flow/workflow-web/src/components/VueBpmnViewer.vue) | 删除重复流程图高亮、连线和提示逻辑，复用公共查看器 | 本轮已收敛到进度数据和业务明细协调 |

## 正确性与共享边界

- `workflow-core/form-runtime/eventEffects` 统一按顺序消费事件；字段映射保留源路径、覆盖确认、空值策略与过期会话检查。PC 两类弹窗、移动详情使用同一协议。
- `runFormAction` 统一先占锁，再确认、校验、执行和消费效果；保留原 requestId、发布坐标与服务端权限协议。
- 流程状态统一为活动优先，再终止、已完成和未开始；回退重审节点不会因为存在历史记录被显示为完成。
- 首页分列表 loading 和请求代次隔离，旧查询迟到不能覆盖新查询；审批后刷新仍可等待完成。
- 列表元数据成功后立即保留新 revision，规则保存失败返回部分失败并停止后续保存，重试不使用过期 revision。
- 本次涉及页面的请求错误使用现有通知去重入口；本地校验继续使用原提示。
- 54 份扩展 JSON/Schema 文件原样迁到根目录 `extensions/`，逐文件内容比对一致；另两份 README 随迁并更新路径说明。生成/校验工具迁到根目录 `scripts/extensions/`；PC 旧工具入口保留薄适配层。移动端构建无需再导入 PC 目录工具。
- `dev:web` / `dev:mobile` 先构建公共包，再同时监听三个包和应用；已验证公共包文件新增、修改、删除同步到 dist，退出后测试端口释放。
- CI 恢复桌面单元、功能、配置、Embed 契约及 PC/移动浏览器回归；新增前端增量预算，覆盖 PC、移动和三个公共包。配置审计跟随新模块及清单位置，并修正旧文档中的失效路径和枚举。

## 验证结果

| 验证 | 结果及范围 |
|---|---|
| `npm run build` | 通过：公共包、PC 管理端、Embed、移动端生产构建 |
| `npm run test:regression --workspace workflow-web` | 通过：桌面单元、扩展契约、Embed、表单节点、功能/集成、页面配置、配置文档、UI 与术语审计 |
| `npm run test:packages` | 34 项通过；包含动作锁、取消/过期请求、效果顺序、字段映射及模块边界 |
| `npm run test:mobile` | 13 项通过 |
| `npm run test:e2e:refactor --workspace workflow-web` | 12 项通过：真实生产组件和页面；含部分保存重试、编辑/审批确认回填、已办查询竞态、进度状态、BPMN 撤销重做；补验 SLA 开关及跨页签草稿保留通过 |
| `npm run test:e2e --workspace workflow-mobile` | 33 项通过：审批、驳回、转办、撤回、确认、回填、关闭刷新、发布坐标和流程图手势等 |
| 既有字段事件/列表按钮事件浏览器脚本 | 通过：新增、编辑、删除、排序、去重、字段切换、旧响应与未保存保护 |
| 扩展注册浏览器脚本 | 通过：十类注册、Demo 开关、重复初始化及清单新增/修改/删除刷新 |
| `npm run check:frontend-budget` | 通过：13 个历史大文件缩减，仍有 27 个源码文件超过 900 行 |
| `git diff --check` | 通过 |

浏览器使用生产 Vue 组件，业务 API 由测试夹具拦截；未知接口会使测试失败。未向真实业务库写入测试数据，未执行真实后端全链路集成。浏览器自动化通过不等于穷尽所有业务配置组合。

页面截图已检查：PC 表单设计、编辑弹窗、关联内容向导、流程进度；移动端 360/390/430 宽度与流程图横竖屏。截图位于 `.codex-artifacts/frontend-refactor/`（测试产物不入 Git）。

## 保留的维护债务

- 原严格命令 `npm run test:maintainability --workspace workflow-web` 仍未通过，原因是仓库历史文件已超过其旧基线。本轮未提高旧阈值；`npm test --workspace workflow-web` 因包含该严格检查仍会失败。
- 新增增量预算只保证新文件不超过 900 行、历史超限文件不继续增长，并不宣称历史体积问题已清零。
- 生产构建仍有既有动态/静态混合导入提示；浏览器开发模式有部分 Vue reactive 绑定提示；本轮构建及页面回归通过。
- 上表“后续拆分空间”是后续结构治理方向，需继续保留保存基线、发布快照、权限和异步请求的既有契约。

## 数据库与交付状态

Flyway 迁移文件：新增无、修改无、删除无、重命名无。未改后端业务代码。改动留在本地工作区，尚未提交或推送。
