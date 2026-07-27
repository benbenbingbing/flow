# 全流程验收记录

日期：2026-07-27

## 最终结论

本轮全流程验收已完成，最终统一入口 `npm run test:acceptance:real` 在修复后的真实后端上完整退出 `0`。

已验证：

- 动态新建并发布真实实体、列表、表单和流程。
- 列表全部主要配置能力、运行时查询和按钮能力。
- 表单字段、布局、联动、节点表单、读写策略和文件/图片上传。
- 全部 16 类可配置 BPMN 元素、60 个属性标签页和 77 个浏览器面板检查。
- 表单和列表标准发布、热发布、草稿隔离、历史回滚和并发版本冲突。
- 流程发布、跨用户待办、审批、状态映射、撤回、终止和动作时机。
- 20 个真实页面视觉检查，以及列表设计页横向空间占满。
- 修复后端实际启动、Flyway、MySQL、Flowable、文件静态访问和运行日志。

测试凭据仅通过环境变量传入，没有写入源码或验收证据。

## 覆盖范围

### 实体与字段

真实实体覆盖系统字段和以下业务类型：

- `STRING`、`TEXT`、`INTEGER`、`DECIMAL`
- `DATE`、`DATETIME`、`BOOLEAN`
- `SELECT`、`MULTI_SELECT`
- `FILE`、`IMAGE`
- `USER`、`DEPT`、`REFERENCE`

验证了必填、唯一、默认值、可编辑、发布后物理表字段、运行时保存、更新、详情和列表查询。

### 列表

| 能力 | 结果 |
| --- | --- |
| 基础属性、默认列表、修订号 | 通过 |
| 字段显示、查询、排序、宽度、对齐 | 通过 |
| 查询类型与字段类型约束 | 通过 |
| 单字段保存和过期 revision 409 | 通过 |
| 工具栏按钮、行操作按钮、可用性规则 | 通过 |
| 场景、选择模式、固定过滤、上下文绑定 | 通过 |
| 数据范围、权限旁路和审计记录 | 通过 |
| 查询数据源、自定义单元格和自定义列表 | 通过 |
| 草稿不影响运行时 | 通过 |
| 标准发布、热发布和历史版本激活 | 通过 |
| 发布快照包含字段、按钮和场景变更 | 通过 |
| 设计页横向空间占满 | 通过 |

热发布专项检查：

- 字段、按钮、场景分别修改时保持单项隔离。
- 草稿阶段运行态不变化。
- 发布后运行态立即读取新快照。
- 激活历史版本后运行态回滚。
- 回滚后原草稿仍可恢复。

### 表单

| 能力 | 结果 |
| --- | --- |
| 表单基础属性、默认表单、修订号 | 通过 |
| 字段节点、布局节点、Tab 和递归层级 | 通过 |
| 单节点保存和过期 revision 409 | 通过 |
| 必填、只读、隐藏、默认值和栅格宽度 | 通过 |
| 字段组件与实体类型匹配策略 | 通过 |
| 初始化器、联动、数据源和自定义表单 | 通过 |
| 节点专属表单、默认回退和全局只读 | 通过 |
| 新建数据和活动任务读取发布快照 | 通过 |
| 标准发布、热发布和历史版本激活 | 通过 |
| 损坏或目标不匹配的热修复 fail-closed | 通过 |
| `FILE`、`IMAGE` 浏览器上传、保存、编辑回显 | 通过 |
| `/uploads` 静态访问和文件内容校验 | 通过 |

文件/图片专项真实验收：

- 浏览器新增弹窗实际选择一个文件和一张图片。
- 两次 `/api/file/upload` 均返回成功。
- 实体数据保存两个 `/uploads/...` 地址。
- 重新打开编辑弹窗可回显文件项和图片项。
- 两个静态 URL 均返回 HTTP 200，文本附件内容与原文件一致。

### 流程与节点

配置、发布清洗和引擎运行时覆盖：

| 类型 | 结果 |
| --- | --- |
| StartEvent、EndEvent | 通过 |
| UserTask、ManualTask | 通过 |
| ServiceTask：类、表达式、委托、REST | 通过 |
| ScriptTask：Groovy | 通过 |
| BusinessRuleTask：DMN | 通过 |
| SendTask、ReceiveTask 和超时 | 通过 |
| Exclusive、Parallel、Inclusive Gateway | 通过 |
| EventBasedGateway | 通过 |
| CallActivity、SubProcess | 通过 |
| SequenceFlow：条件、默认流、状态映射 | 通过 |

补充验证：

- 执行人、候选人、角色、用户组、人员解析器和多实例集合。
- 表单绑定、审批意见、审批选项、抄送、加签和流程动作。
- 调用活动输入输出映射、业务键和被调用流程选择。
- 状态字典和节点状态映射真实进入实体运行态。
- 两名动态测试审批人的强制改密、独立登录、待办和两级审批。
- 流程完成、撤回、终止、动作重试和死信路径。
- 10 种动作时机：节点进入/完成、任务创建/分配/完成前、连线经过、流程开始/完成/撤回/终止。

## 热发布证据

表单检查：

- `initial-diff-clean`
- `single-node-isolation`
- `stale-revision-409`
- `draft-does-not-affect-runtime`
- `publish-affects-runtime`
- `historical-activate-rolls-back-runtime`
- `draft-restored`

列表检查：

- `initial-diff-clean`
- `single-field-isolation`
- `single-action-isolation`
- `single-scene-isolation`
- `field-action-scene-stale-revision-409`
- `draft-does-not-affect-runtime`
- `publish-affects-runtime`
- `published-snapshot-includes-scene-change`
- `historical-activate-rolls-back-runtime`
- `draft-restored`

## 最新证据

- 真实流程闭环：`workflow-web/docs/real-workflow-e2e/workflow-e2e-20260727022345.json`
- 跨用户配置闭环：`workflow-web/docs/workflow-closure/config-closure-26072702235jm.json`
- 节点表单矩阵：`workflow-web/docs/node-form-matrix/node-form-matrix-26072702233yv.json`
- 实体流程闭环：`workflow-web/docs/workflow-closure/closure-2607270223giw.json`
- 动作时机：`workflow-web/docs/flow-action-timing-e2e/flow-action-timing-2607270223m5p.json`
- 动态扩展：`workflow-web/docs/dynamic-extension-demo/dynamic-extension-demo-2607270224s2q.json`
- 文件/图片上传：`workflow-web/docs/file-upload-e2e/file-upload-2607270223otl.json`
- 上传回显截图：`workflow-web/docs/file-upload-e2e/real-file-image-upload.png`
- 视觉结果：`workflow-web/docs/visual-acceptance/visual-acceptance-results.json`
- 列表运行态截图：`workflow-web/docs/visual-acceptance/07-real-entity-list.png`
- 列表设计页截图：`workflow-web/docs/visual-acceptance/08-real-list-design.png`
- 表单设计页截图：`workflow-web/docs/visual-acceptance/10-real-form-design.png`
- 流程设计页截图：`workflow-web/docs/visual-acceptance/11-real-process-design.png`

视觉验收覆盖登录、首页、实体、流程、真实实体设计、列表目录、列表运行态、列表设计、表单目录、表单设计、流程设计、用户、角色、用户组、组织、菜单、字典和开发指南，共 20 页；所有页面均无缺失文案和页面错误。

## 已修复问题

1. 列表字段表格固定宽度导致“当前配置”和操作列之间出现大块空白。
2. 表单热修复快照未完整进入新实例和活动任务运行时。
3. 热修复目标不匹配或快照损坏时静默回退旧配置。
4. 流程详情加载失败后仍可能打开带旧数据的审批弹窗。
5. 用户任务执行人、表单、多实例和审批配置保存失败被吞掉。
6. ReceiveTask 超时配置未形成可运行的边界定时器。
7. ScriptTask 配置与 Flowable 实际可执行脚本类型不一致。
8. SendTask 暴露未实现渠道，保存后无法执行。
9. REST 节点 URL、JSON、结果映射和错误处理缺少闭环验证。
10. 流程发布把设计 XML 与运行时清洗 XML 混为同一份内容。
11. 文件存储目录和 `/uploads` 静态映射不一致。
12. 相对目录 `./uploads` 被 Servlet 容器按临时目录再次解析，真实上传失败。
13. V041 系统日志菜单迁移失败历史导致后续启动被 Flyway 阻断。
14. 系统日志只有权限节点，没有可显示的 C 类型侧栏菜单。
15. 真实验收脚本写死旧凭据和办理人，无法适配真实登录策略。
16. `NodeConfigPanel` 立即监听早于子流程加载函数初始化，生产包出现 TDZ `ReferenceError`。
17. 动态扩展仍跳转到废弃的 `/entity/list/{code}` 路由。
18. 多实例监听器处理普通活动事件，并用字符串截取解析不透明流程定义 ID。
19. 数据范围审计在只读查询事务内写入，导致审计数据丢失。
20. 正常的无表单节点被记录为 WARN，干扰生产日志判断。

## Flyway 与菜单

- 当前数据库版本为 `042`，启动迁移校验通过。
- 失败的旧 V041 记录按精确条件清理，不删除成功迁移记录。
- 系统日志菜单 `system_audit_menu_001` 为可显示的 `C` 类型菜单，路径 `/system/audit-logs`，角色权限已配置。
- 修复后的后端真实启动没有 Flyway 迁移失败。

## 自动化结果

- `npm run test:acceptance:real`：通过，包含热发布、文件上传、真实流程、配置闭环、节点表单、动作时机、动态扩展和 20 页视觉验收。
- `mvn test`：576 个测试，0 failures，0 errors，1 skipped。
- `npm test`：通过。
- 前端 UI 配置审计：95 个文件、1962 个控件，通过。
- 维护性门禁：171 个前端文件、601 个后端文件，通过。
- `npm run test:e2e:mock`：30 个路由、10 个交互、2 个布局检查、77 个流程节点面板检查，通过。
- `npm run build`：2195 个模块，构建通过。

## 运行日志审计

最终有效后端进程：

- 启动时间：2026-07-27 10:10:13
- PID：`59283`
- 日志边界：`workflow-server/server.log` 第 169847 行

从该边界到最终验收结束：

- 非预期 `ERROR`：0
- 多实例集合监听错误：0
- `Connection is read-only`：0
- 数据范围审计写入失败：0
- 文件上传 `FileNotFoundException`：0
- 无表单节点 WARN：0

保留的 WARN 均已核对：

- 热发布脚本主动制造的过期 revision 负向用例。
- 动作时机脚本主动制造的失败处理器重试。
- 一条历史实体范围策略启动提示，不影响本轮新建实体和数据范围审计。

## 非阻塞技术债

- Dart Sass legacy JS API 有弃用警告，不影响当前构建和运行。
- Node 测试使用 experimental loader，有未来迁移提示。
- 仓库仍有已冻结的大文件债务，当前维护性门禁通过，并有拆分计划。

## 当前运行状态

- 后端：`http://localhost:8080`
- 前端：`http://localhost:3000`
