# 实体新增与普通编辑加载优化

日期：2026-11-13。

## 范围及调用约定

本次优化 PC 原生实体新增、普通编辑面板及复用它们的列表、工作区和 Embed 入口。查看、审批的首屏拆分见 `entity-view-loading-optimization-2026-11-13.md`。

原实现先读取发布表单，编辑时再读取详情、执行打开事件并等待按钮，最后显示弹窗；字段组件挂载后还会执行数据源初始化。多页签会创建多个字段组件，共享的表单初始化容易被重复触发，或者在用户开始输入后继续回填。

本次没有新增外部接口，没有修改接口请求/响应结构或数据库结构。保存、保存并发起、配置摘要、列表来源、关联签名及服务端最终授权仍使用原有调用。界面增加加载骨架、初始化错误提示、按钮加载状态和独立重试入口，字段及业务操作沿用原有表单配置。

## 前端实现与约束

1. 列表点击新增/编辑后先显示原生弹窗骨架，将发布表单解析移入弹窗生命周期。普通页面每次打开重新解析最新发布表单；指定目标表单仍重新校验按钮和发布配置；固定 Embed 表单仍使用会话固定的表单。
2. 必要初始化在允许输入前完成：编辑详情及其服务端 `DETAIL_LOAD` → 页面参数初始化 → `FORM_OPEN` → 新增专用 `FORM_INIT` → 表单及字段 `AFTER_LOAD` → 字段默认值与计算 → 控件同步补值/联动。新增入口 `initialData` 继续保留最高优先级；数据源映射的 `context.*` 路径保留原有入口信息。
3. 根面板统一执行一次表单初始化，页签字段组件不再各自执行。初始化完成且控件挂载后记录重置及未保存修改基线，随后开放输入。
4. 按钮在字段可填写后独立加载，不阻塞输入。服务端解析失败显示重试入口；重试仅解析按钮，不重新读取详情、不重放打开事件，也不回退为可保存的本地默认按钮。
5. 字典和普通选项并行读取。同次打开内合并相同字典及完整参数相同的选项请求；选项参数变化仍重新请求，失败清除对应缓存。生命周期、计算、保存等调用不进行请求合并，也不跨会话缓存权限或记录。
6. 配置、详情、打开事件、初始化、选项和按钮都受打开会话约束。确认关闭时即使 Element Plus 关闭动画尚未结束，也立即释放列表入口并使旧结果失效。切换记录/卸载同样使旧会话失效；工作区仅暂时隐藏页面时保留会话和输入。
7. 初始化失败保留错误提示，不自动重放可能有业务效果的事件；用户关闭后可重新打开。事件主动返回 `CLOSE_FORM` 时，后续 `REFRESH_PARENT` 仍按服务端给定顺序执行。

浏览器 Performance 面板提供 `entity-form:{create|edit}:{visible|ready|actions-ready}`，分别记录本次开始打开至显示加载面板、允许填写、按钮就绪的耗时；每种模式/阶段只保留最近一次指标，不含记录 ID。`visible` 是面板可见状态设置时刻，实际绘制还受浏览器调度影响。

## 后端实现与权限

- 普通编辑的按钮解析先检查服务端发布配置中当前模式启用按钮的可见/可用规则。明确仅依赖系统字段、用户、状态及系统身份关系的规则使用主记录摘要，不展开多值字段和关系子树。
- 任意业务字段条件或不能确认依赖的扩展条件保守读取完整实体聚合。递归检查分组、显示条件和启用条件；停用按钮或其他模式的规则沿用原有跳过逻辑，避免性能分析改变规则校验行为。
- 摘要仍通过详情相同的 DataScope SQL 读取当前服务端记录；越权立即拒绝，不回退为其他读取或客户端数据。规则、对象权限和业务操作资格继续由原能力服务判断。
- 审批、流程查看及系统实体沿用各自原有访问链。本次没有跨请求权限缓存；最终提交及自定义动作仍实时校验身份、发布表单、可访问记录、字段和业务约束。
- 同次按钮解析复用已读取的发布配置，避免再次解析配置；debug 日志输出按钮解析总耗时。

## 回归结果

浏览器测试使用真实生产组件和模拟 API，显式挂起响应来检查调用先后及迟到响应，不写入业务环境。后端测试包含真实 H2/JDBC 的保存、事务及 Embed 数据面集成场景。

| 验证范围 | 结果 |
| --- | --- |
| 后端：按钮依赖、DataScope、权限、发布提交、字段合并、保存事务、Embed | 157 项通过，0 失败、0 错误、0 跳过 |
| 共享前端包 | 79 项通过 |
| PC 单元测试及前置检查 | `test:unit` 通过 |
| Embed SDK/请求/桥接/会话及原生表单刷新测试 | `test:embed` 通过，包含打开返回失败的处理 |
| 新增/编辑真实组件 Playwright | 18 项通过 |
| 既有编辑/审批/进度/已办列表 Playwright | 4 项通过 |
| 实体查看和历史档案浏览器脚本 | 通过 |
| 列表按钮加载、防重及失败恢复浏览器脚本 | 通过 |
| 自定义按钮打开新增/编辑/查看、关联子表单和列表 | 浏览器脚本通过 |
| 编辑/查看页签中即时加载关联子表单 | 浏览器脚本通过 |
| 关联页面配置检查 | `related-content-page-config.spec.js` 单独运行通过 |
| 共享包、PC、移动端生产构建 | `npm run build` 通过 |
| 差异检查 | `git diff --check` 通过；迁移和 `docs/sql` 无变化 |

新增/编辑浏览器用例覆盖：配置返回前显示骨架及关闭释放入口；详情/事件/初始化/按钮迟到；按钮失败独立重试；必要初始化顺序及初值映射；编辑不执行 `FORM_INIT`；重置基线；工作区隐藏恢复与未保存修改确认；新增/编辑的保存及保存并发起；真实列表连续打开重新解析发布表单；多页签初始化、字典、选项去重；关闭动画中的迟到效果；事件主动关闭后的刷新。

后端新用例检查：简单系统规则只读取摘要；业务字段的显示/启用条件均保留聚合；DataScope 拒绝后不继续解析；畸形有效规则不能绕过校验；停用/其他模式规则不因依赖分析额外校验。真实动态数据读取测试确认摘要使用原过滤参数且不展开关系/多值。

### 复验命令

在仓库根目录执行后端专项：

```sh
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true mvn -o -f workflow-server/pom.xml -pl workflow-app -am \
  -Dtest=EntityFormActionServiceTest,EntityFormActionConfigPolicyTest,PublishedFormSubmissionOutputMergeTest,EntityActionCapabilityServiceTaskBindingTest,EntityActionRuleEvaluatorTest,EntityDataActionServiceTest,EntityDataControllerTest,PublishedFormSubmissionServiceTest,EntityMutationReceiptServiceTest,EntityMutationTransactionExecutorTest,EntityMutationPipelineTest,EmbedDelegatedRuntimePolicyTest,EmbedDelegatedRuntimeAuthorizationInterceptorTest,EmbedDelegatedDataPlaneIntegrationTest,EntityDataDynamicServiceSubFormTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

在 `workflow-frontend` 执行前端检查：

```sh
npm run build
npm run test:packages
npm run test:unit --workspace workflow-web
npm run test:embed --workspace workflow-web
npm run test:e2e:refactor --workspace workflow-web -- tests/e2e/form-opening.spec.mjs tests/e2e/runtime-refactor.spec.mjs
```

在 `workflow-frontend/workflow-web` 执行补充浏览器和配置检查：

```sh
node scripts/entity-archive-view-browser-test.mjs
node scripts/list-action-loading-browser-test.mjs
node scripts/entity-relation-browser-test.mjs --form-buttons
node scripts/entity-relation-browser-test.mjs --immediate-tabs
node src/__tests__/related-content-page-config.spec.js
npm run test:page-config
```

### 未通过检查及未覆盖范围

`npm run test:page-config` 在 `page-config-audit.spec.js:1744` 失败：本次未修改的 `FlowActionConfigPanel.vue` 缺少检查要求的 `title="说明与参数"`。本次更新的新增/编辑入口检查位于此前，已通过；没有为消除该失败而修改流程动作配置界面或放宽该断言。关联页面配置检查已单独运行通过。

尚无实际慢请求的 URL、业务数据规模和环境耗时，没有进行生产环境耗时压测，也没有量化提速百分比。必要的详情事件、生命周期接口及字段回填仍须完成后才能填写；这些业务步骤自身很慢时，仍需要结合耗时指标和服务端日志进一步定位。

## 数据库迁移

本次新增、修改、删除的 SQL / Java Flyway 迁移文件：均无。未改变数据库结构、索引、约束或系统种子数据，`docs/sql` 无需同步修改。
