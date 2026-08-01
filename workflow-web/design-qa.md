# 列表顶部预览入口恢复验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-e199ad84-4ce5-4bef-ab43-d0e4d9546937.png`
- Source pixels: `3170 x 1500`
- Implementation screenshot: unavailable because browser access to the local page remains blocked
- Intended state: list configuration designer header with the action buttons visible

## Implemented Interaction

- Restored the `预览` button between `版本` and `发布生效`.
- The preview opens in a wide dialog instead of restoring the removed right-side panel.
- The dialog uses current field, query, table, and pagination configuration.
- Query, reset, page change, page-size change, retry, desktop/tablet width, and close actions are available.
- Preview data continues to load through the controlled list runtime API.

## Findings

- Fonts and typography: the restored button follows existing Element Plus header-action typography.
- Spacing and layout rhythm: the button fills the intended header gap without changing the configuration-panel width.
- Colors and visual tokens: the button uses the existing default action style.
- Image quality and assets: no image assets are involved.
- Copy and content: `预览` and `列表预览` clearly distinguish the entry and dialog.

## Verification

- `npm run test:page-config`: passed
- `npm run test:functional`: passed
- `npm run build`: passed
- `git diff --check`: passed
- Browser rendering, preview interaction, console inspection, and screenshot comparison: blocked by the existing browser access restriction

final result: blocked

---

# 任务操作弹窗只读展示与宽度调整验收

**Evidence**

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-cc08c8c5-245a-49d9-818a-5695d81cfd62.png`
- Implementation screenshot: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-transfer-dialog-wide.jpg`
- Full-view comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-dialog-before-after.jpg`
- Focused comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-dialog-focused-comparison.jpg`
- Viewport: `1280 x 720` CSS pixels, device scale factor `2`
- Source pixels: `1236 x 1112`; implementation pixels: `1280 x 720`
- Density normalization: the source was resized proportionally to `720px` height for the full comparison. The source and implementation dialog crops were both normalized to `500px` height for the focused comparison.
- State: 首页 > 待办任务 > 更多任务操作 > 任务转办。任务加签、人工知会弹窗使用相同只读区域和宽度规则，并通过浏览器 DOM 检查。

**Findings**

- No actionable P0/P1/P2 differences remain.
- Fonts and typography: existing Element Plus/system typography is retained. The process code uses a monospaced fallback intentionally, and both read-only values remain legible without input chrome.
- Spacing and layout rhythm: all three dialogs use `min(680px, 92vw)` and a `96px` label width. The wider content area gives the selector and textarea adequate room while preserving the existing vertical rhythm.
- Colors and visual tokens: existing neutral text, border, overlay, and primary action colors are unchanged.
- Image quality and asset fidelity: the target dialog contains no application image assets; no placeholders or replacement graphics were introduced.
- Copy and content: process name, process code, field labels, placeholders, and action text are unchanged.
- The source and implementation screenshots contain different surrounding page crops, so the fidelity decision is based on the normalized full comparison and the focused dialog comparison.

**Open Questions**

- None.

**Implementation Checklist**

- [x] Remove the read-only input borders around process name and process code.
- [x] Widen transfer, add-sign, and manual-CC dialogs responsively.
- [x] Preserve editable controls and submission payloads.
- [x] Verify all three dialogs in the authenticated browser session.
- [x] Check browser logs after login and dialog interaction.

**Primary Interactions Tested**

- Opened the task transfer dialog from the todo row's more-actions menu.
- Opened the add-sign dialog and confirmed the same plain-text read-only fields.
- Opened the manual-CC dialog and confirmed the same plain-text read-only fields.
- Confirmed each dialog remains closable and its editable fields remain interactive.

**Console Check**

- No new application console errors appeared after login or while opening the three dialogs.
- Three `401` records are retained from the initial unauthenticated page load before login; they did not recur after authentication.
- An external Statsig telemetry timeout came from the browser tooling environment and is unrelated to the application.

**Comparison History**

- Pass 1: the normalized full-view and focused comparisons found no actionable P0/P1/P2 issue, so no visual correction loop was required.

**Follow-up Polish**

- None required for this scoped change.

final result: passed

---

# 开放集成能力状态验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-a373f8d9-6db5-4ec3-8003-11570d78958f.png`
- Source state: 接入应用列表与详情正常，但 Webhook、Secret、Connector 在能力关闭时请求未注册接口，连续显示“资源不存在”。
- Implementation screenshot: unavailable because the authenticated local browser session had expired before capture.

## Implemented

- 增加始终可用的开放集成管理能力接口，统一返回开放 API、Webhook 和 HTTP Connector 的启用状态。
- 页面加载应用列表时同步读取能力状态。
- Webhook、Secret、Connector 仅在对应能力启用时装载并请求数据。
- 能力关闭时展示明确的“能力未启用”状态，不再误报“资源不存在”。
- `.env.example` 补充可选能力开关和密钥配置说明，默认保持关闭。

## Verification

- 后端能力查询与默认关闭测试：passed。
- Webhook 条件装配测试：passed。
- `npm run test:functional`: passed。
- `npm run build`: passed。
- 后端 `workflow-app` package：passed。
- `git diff --check`: passed。
- 修复后的后端已于 2026-08-01 启动，Flyway 与自动建表均保持关闭。

## Blocking Reason

当前 Chrome 和应用内浏览器均停留在登录页，无法在不修改用户或认证数据的情况下复现已登录页面。因此本次没有生成实现截图；视觉状态仍需登录后刷新页面确认。

final result: code passed, authenticated visual verification blocked

---

# 表单区块与节标题语义调整验收

## Evidence

- Source visual truth:
  - `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-fb54d3c6-3a0d-45ed-8b94-965110f3a3a7.png`
  - `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-e4b75717-5131-4a56-a11c-a6a2ded4d317.png`
- Intended state: `SECTION` remains the recursive content container and is created from `添加容器节点 > 区块`; the top-level `添加节` command creates a non-container `TEXT` node with `textStyle=SECTION_TITLE`.
- Browser verification target: `http://localhost:3001/entity-form/design-by-entity/...`

## Implementation Verification

- The container menu exposes `区块` and no longer presents the container itself as a section title.
- `添加节` creates a text node rendered as a blue vertical rule followed by its name.
- Designer and published runtime both use the shared `SectionField` renderer.
- The property drawer identifies the node as `节` and edits its single-line `节名称`.
- Backend property policy accepts only `PLAIN` and `SECTION_TITLE`, preventing arbitrary style values.
- Legacy plain text and existing `SECTION` containers remain compatible.

## Automated Verification

- `npm run test:form-node-schema`: passed.
- `npm run test:page-config`: passed.
- `npm run build`: passed.
- `npm run test:integration`: passed.
- `npm run test:functional`: passed.
- `npm run test:runtime-form-tabs`: passed.
- Targeted backend property-policy test passed before unrelated concurrent backend changes introduced a compile error in `UiConfigReleaseService`.

## Blocker

- The available browser session redirected the designer route to `/login`, so an authenticated same-viewport screenshot comparison could not be completed without user credentials.
- No credentials were entered or changed.
- The repository-wide maintainability audit still reports 20 unrelated files above their existing line budgets; the changed property-policy file is no longer among them.

final result: blocked

---

# 流程节点与表单编辑权限主从规则验收

## Evidence

- Source visual truth:
  - `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-3ae7370e-5414-4d93-9ad1-8e413b86f1a6.png`
  - `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-51546724-c509-4fec-9244-1830fe72d9c4.png`
- Implementation screenshots:
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/process-force-readonly.png`
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/form-mode-permissions.png`
- Focused comparison evidence:
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/source-process-panel.png`
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/implementation-process-panel.png`
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/source-form-panel.png`
  - `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/qa-readonly-permissions/implementation-form-panel.png`
- Viewport: implementation captured at `1280 x 720` CSS pixels in the in-app browser.
- Source pixels: process `2854 x 1648`; form `3066 x 1602`.
- Implementation pixels: both screenshots `1280 x 720`.
- Normalization: full views were compared by corresponding product regions. Focused source and implementation crops isolate the right-side configuration panels and were reviewed together; no density-dependent measurements were used.
- State:
  - Authenticated requirement approval process, architecture review user task selected. The form source was changed locally to entity form only to expose the setting and was not applied or saved.
  - Authenticated requirement-project-link form designer, `承接角色` selected and `模式与权限` expanded.

## Findings

- No actionable P0/P1/P2 differences remain.
- Fonts and typography: existing Element Plus/system typography, weights, line heights, and letter spacing remain unchanged. The longer `强制整表只读` label stays on one line.
- Spacing and layout rhythm: the node description wraps below the switch without overlapping the following section. The form permission grid preserves its existing row height and alignment after removing the view-mode edit checkbox.
- Colors and visual tokens: switches, checkboxes, section borders, tags, and explanatory text continue using the existing product tokens.
- Image quality and asset fidelity: the affected regions contain no application image assets and introduce no replacement graphics.
- Copy and content: the node-level copy explicitly states that it overrides field approval editability. The field-level copy identifies approval editability as the default and states that view mode is always read-only.
- The red rectangles in the source screenshots are request annotations and are intentionally absent from the implementation.

## Interaction And Runtime Verification

- The process node displays `强制整表只读` and the complete override explanation when entity forms are selected.
- Approval mode still exposes both `显示` and `可编辑`.
- View mode exposes only `显示`; no editable checkbox is rendered.
- Runtime mode resolution forces `view` to `editable: false`, including legacy configuration that explicitly stores `editable: true`.
- The temporary process form-source change was not applied to the canvas and did not modify process data.
- No application error state appeared during authenticated navigation and interaction. Browser-tool Statsig telemetry timeouts are external to the application.

## Comparison History

- Pass 1 found no actionable P0/P1/P2 issue. The new copy is readable, the process label does not wrap, and the view row remains visually aligned without an edit control.

## Verification

- `npm run test:integration`: passed.
- `npm run test:page-config`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Authenticated browser interaction and focused screenshot comparison: passed.

final result: passed

---

# 首页待办任务操作列精简验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-a0f75582-6e0b-451e-aea4-5c18ea133cff.png`
- Source pixels: `3456 x 1310`
- Implementation screenshot: `/Users/dawei/.codex/visualizations/2026/07/26/019f9bd4-6d79-7383-857b-5bd7b7c262cc/home-todo-actions-after.png`
- Implementation pixels: `1728 x 935`
- Side-by-side comparison: `/Users/dawei/.codex/visualizations/2026/07/26/019f9bd4-6d79-7383-857b-5bd7b7c262cc/home-todo-actions-comparison.png`
- State: 首页 > 待办任务，第一行“更多任务操作”菜单展开

## Implemented Layout

- `审批`保留为操作列的直接主按钮。
- `转办`、`加签`、`知会`收纳到点击触发的更多菜单。
- 现有 SLA 操作在数据存在时继续由更多菜单承载。
- 操作列固定宽度调整为 `126px`，仅容纳主按钮和图标按钮。
- 认领任务、加签节点和撤销加签的原有可用条件保持不变。

## Findings

- No P0, P1, or P2 visual or interaction issues.
- Fonts and typography: 延续现有 Element Plus 表格按钮和菜单样式。
- Spacing and layout rhythm: 两个按钮以 `6px` 间距居中排列，操作列宽度实测为 `126px`。
- Interaction: 菜单展开后正确显示`转办`、`加签`、`知会`，审批按钮始终可见。
- Overflow: 右侧固定操作列完整显示，菜单使用右对齐弹层且未被表格裁切。
- Browser console: no warnings or errors.

## Verification

- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Authenticated browser interaction and screenshot comparison: passed.

final result: passed

---

# 列表与字段配置页签换位验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-bdfd6df5-acec-4c37-a976-480158eff7f5.png`
- Source pixels: `3426 x 2004`
- Implementation screenshot: unavailable because browser access to the local page remains blocked
- Intended state: list configuration designer with the primary configuration tabs visible

## Implemented Layout

- Moved `字段配置` to the first tab position.
- Moved `列表设置` to the second tab position.
- Changed the initial active tab from `列表设置` to `字段配置`.
- Preserved each tab's original content, actions, and model name.

## Findings

- Fonts and typography: unchanged.
- Spacing and layout rhythm: only the first two tab positions changed.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image assets are involved.
- Copy and content: unchanged.

## Verification

- `npm run test:page-config`: passed
- `npm run test:functional`: passed
- `npm run build`: passed
- `git diff --check`: passed
- Browser rendering, tab interaction, and source-to-implementation screenshot comparison: blocked by the existing browser access restriction

final result: blocked

---

# 列表设计器冗余标题移除验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-bcb59084-e94d-4d97-b802-bf76c5894cd2.png`
- Source pixels: `3352 x 1666`
- Implementation screenshot: unavailable because browser access to the local page remains blocked
- Intended state: list configuration designer with `字段配置` selected

## Implemented Layout

- Removed the outer card header containing `字段配置`.
- Removed the accompanying `拖拽排序，勾选控制显示和查询` description.
- Preserved the four functional tabs and all configuration content.
- The reclaimed vertical space now moves the active tab and field table closer to the page header.

## Findings

- Fonts and typography: no remaining duplicate heading is introduced by this change.
- Spacing and layout rhythm: the redundant full-width header row is removed.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image assets are involved.
- Copy and content: only the duplicate heading and explanatory copy were removed.

## Verification

- `npm run test:page-config`: passed
- `npm run build`: passed
- `git diff --check`: passed
- Browser rendering and source-to-implementation screenshot comparison: blocked by the existing browser access restriction

final result: blocked

---

# 字段用途列单行显示验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-662a0bb3-b624-429e-82f7-471a56d3f4f1.png`
- Source pixels: `2854 x 1308`
- Implementation screenshot: unavailable because browser access to the local page remains blocked
- Intended state: list configuration designer with `字段配置` selected

## Implemented Layout

- Increased the `用途` column from `104px` to `144px`.
- Changed the `列表` and `查询` controls from vertical stacking to a horizontal row.
- Added a no-wrap constraint so the two purpose options remain on one line.
- Kept `当前配置` as the flexible column that fills the remaining table width.

## Findings

- Fonts and typography: unchanged; purpose labels retain the existing Element Plus checkbox typography.
- Spacing and layout rhythm: source showed unwanted vertical stacking; source code and compiled output now provide sufficient fixed width and a horizontal `12px` gap.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image assets are involved.
- Copy and content: unchanged.

## Verification

- `npm run test:page-config`: passed
- `npm run build`: passed
- `git diff --check`: passed
- Browser rendering and source-to-implementation screenshot comparison: blocked by the existing browser access restriction

final result: blocked

---

# 列表设置宽屏平铺验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-3b21d625-f4b6-48e0-a0a2-1093d22379fd.png`
- Source pixels: `3016 x 1740`
- Implementation screenshot: unavailable because browser access to `localhost:3000` was explicitly blocked
- Intended state: list configuration designer with `列表设置` selected

## Implemented Layout

- Removed the `760px` maximum width from the list settings form.
- The form and every settings section now use the complete configuration-panel width.
- At viewports of `1440px` and wider, section bodies use two equal columns.
- Scene selection, return-mapping JSON, and component parameters span both columns.
- Below `1440px`, settings keep the existing single-column layout.

## Findings

- Fonts and typography: unchanged.
- Spacing and layout rhythm: code and build output establish a full-width form with a `32px` desktop column gap, but browser-rendered spacing could not be inspected.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image assets are involved.
- Copy and content: unchanged.

## Verification

- `npm run test:functional`: passed
- `npm run test:page-config`: passed
- `npm run build`: passed
- `git diff --check`: passed
- Browser rendering, interaction, console, and source-to-implementation comparison: blocked by the browser access restriction

final result: blocked

---

# 列表设计器字段配置扩宽验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-e350d104-fb8a-4492-b120-660000c4c4a8.png`
- Final list settings screenshot: `/private/tmp/entity-list-design-final-list-settings-normalized.png`
- Final field configuration screenshot: `/private/tmp/entity-list-design-final-field-config.png`
- Side-by-side comparison: `/private/tmp/entity-list-design-qa-comparison.png`
- Source pixels: `3302 x 1716`; cropped browser chrome to `3302 x 1655`, then normalized to `1651 x 828`
- Implementation capture: `1800 x 903`, normalized to `1651 x 828` for comparison
- Browser CSS viewport during final inspection: `1800 x 1000`
- Responsive inspection viewport: `900 x 800`
- State: list settings selected for full-view comparison; field configuration selected for focused verification

## Full-View Comparison

- The right-side draft preview and its header action are absent.
- The configuration panel expands across the complete available content width.
- Existing header, tabs, cards, spacing, typography, colors, borders, and controls remain consistent with the source page.
- No page-level horizontal overflow was present at `900 x 800`; the field table keeps its own horizontal scrolling behavior.

## Focused Region Comparison

- Field configuration table width measured `1482px` inside a `1556px` configuration panel at the final desktop viewport.
- No `.preview-panel` node or `草稿预览` copy remains.
- Switching between `列表设置` and `字段配置` works.
- Browser console errors: none.

## Findings

- No remaining P0, P1, or P2 visual or interaction issues.
- Fonts and typography: unchanged from the existing Element Plus design system.
- Spacing and layout rhythm: configuration width is reclaimed without introducing nested cards or new spacing patterns.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image assets are involved in this interface change.
- Copy and content: the stale “在预览中确认” description was replaced with “在实际列表页面确认”.

## Comparison History

1. Initial comparison found a P2 copy issue: the preview panel was removed, but the list settings description still referred to preview confirmation.
2. Updated the description to point users to the actual list page.
3. Rebuilt, reran audits, refreshed the authenticated page, and confirmed the updated copy with no console errors.

## Verification

- `npm run test:functional`
- `npm run test:page-config`
- `npm run build`
- `git diff --check`

final result: passed

---

# Entity List Field Table Fill Design QA

final result: passed

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-695fb5ee-b582-4a61-b0da-0febc14b0799.png`
- Implementation screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/entity-list-config-fields-full.png`
- Focused implementation screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/entity-list-config-fields-table.png`
- Full-view comparison: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/entity-list-config-fields-comparison.png`
- Focused comparison: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/.artifacts/entity-list-config-fields-focused-comparison.png`
- Route: `http://localhost:3000/entity-list-config/design/85af37def242563d414bbd97d57e6acb`
- Viewport: `2660 x 1804` CSS px, capture density 1x.
- State: basic mode, `字段配置` tab, unchanged draft preview data.

## Findings

The source showed a P1 layout issue: every table column had a fixed width, so the table body ended after the operation column and left a large unused white area inside the field-configuration panel.

The current implementation has no remaining actionable P0, P1, or P2 findings.

- Layout: `当前配置` is now the flexible column with `min-width="320"` and expands through the available panel width. At the verified viewport it measured `1051px`.
- Boundaries: the table measured `1489px` wide from `x=259` to `x=1748`; the fixed operation column ended at the same `x=1748` boundary, leaving no blank region after it.
- Advanced mode: the field-code column remains visible at `168px`; `当前配置` still expands to `883px`, and the operation column remains aligned to the right edge.
- Narrow mode: at `1440 x 900`, the document width stayed at `1440px`. Minimum column widths are preserved through the table's internal horizontal scrolling without creating page-level overflow.
- Typography: existing Element Plus fonts, sizes, line heights, and zero letter spacing are unchanged.
- Spacing and controls: field inputs, purpose checkboxes, summary text, and row actions remain aligned and do not overlap.
- Colors and tokens: existing primary, success, neutral, border, and background tokens are unchanged.
- Images and assets: this surface contains no product imagery; existing icons render unchanged.
- Copy and content: all field labels, summaries, button labels, and preview data are unchanged.

## Interaction Verification

- Switched from basic mode to advanced mode and back with the table remaining full width.
- Verified the wide viewport and a `1440 x 900` viewport.
- Browser console error count: `0`.
- `npm run build`: passed.
- `git diff --check`: passed.
- `npm run test:page-config`: the new field-table assertions passed before the suite reached an unrelated existing hotfix-copy assertion failure near line 1084.

## Comparison History

### Before

- Fixed-width columns totaled less than the available panel width.
- A large blank area appeared to the right of the operation column.

### After

- Stable controls keep explicit widths.
- The summary column absorbs remaining width.
- The fixed operation column sits on the panel's right edge in both basic and advanced modes.

---

# Role Permission Tree Transfer Design QA

final result: passed

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-5a18837d-a0da-466a-9f06-000d629e299c.png`
- Normalized source: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-source-normalized.png`
- Implementation screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-tree-transfer.png`
- Scrolled-state screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-tree-transfer-scrolled.png`
- Side-by-side comparison: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-tree-comparison.png`
- Viewport: `1562 x 690` CSS px.
- Source pixels: `3126 x 1380`, normalized to `1562 x 690`.
- Implementation pixels: `1562 x 690`, capture density 1x.
- State: role management, the first role's permission dialog open, `474 / 475` permissions assigned, both searches blank, both trees at the top.
- Current sizing contract: dialog width `66.6667vw`, height `90vh`, top offset `5vh`, and maximum width `calc(100vw - 32px)`.
- The screenshots document the verified dual-tree behavior before the final dialog sizing adjustment. The current sizing delta is verified from the deterministic viewport CSS, functional tests, and production build because the in-app preview browser was blocked on its network error page by browser security policy.

## Full-View Comparison

The source establishes the existing Element Plus visual language and exposes the original problem: a narrow tree dialog extends below the viewport and hides its footer. The implementation changes the composition to a large centered permission dialog with two equal tree panels, centered transfer controls, fixed headers and footer, and independently scrollable tree bodies.

At the verified `1562 x 690` viewport, the sizing contract resolves to approximately `1041.3px` wide and `621px` high, positioned `34.5px` from the top. The matching `5vh` top offset and `90vh` height leave `5vh` below the dialog, while the flex body gives remaining height to the two internally scrollable trees and keeps the action footer visible.

## Focused Comparison

The full-view comparison is also sufficient as the focused review because the panel titles, search inputs, hierarchy indentation, checkboxes, menu-type tags, transfer buttons, counts, and footer controls remain readable at the matched viewport. The scrolled-state capture verifies the long-tree behavior that cannot be judged from a static top position.

## Findings

No actionable P0, P1, or P2 findings remain.

- Fonts and typography: the application's existing Element Plus Chinese font stack, weights, sizes, line heights, and zero letter spacing are preserved. Labels and tags remain legible without overlap.
- Spacing and layout rhythm: the dialog uses two thirds of the viewport width and 90% of its height, both panels share equal width, the `80px` transfer-control column stays stable, and headers, search fields, tree bodies, panel footers, and dialog actions align consistently.
- Colors and visual tokens: existing primary, neutral, warning, success, and info tokens are reused. The panel framing and disabled context nodes remain clear without introducing a competing palette.
- Image quality and asset fidelity: the screen contains no product imagery. Existing framework icons and controls render crisply, with no substitute assets.
- Copy and content: `未分配`, `已分配`, side-specific search placeholders, menu-type labels, assignment count, `取消`, and `确定` are accurate and concise.

## Interaction Verification

- The assigned tree body measured `362px` high with a `17084px` scroll height at the `1562 x 690` viewport.
- Scrolling the assigned tree to `1100px` left page scroll at `0`; the dialog footer stayed at `611px` and the confirmation button stayed inside the viewport at `658px`.
- Searching the available tree for `角色管理` displayed the ancestor `系统管理` and the matching child `角色管理`.
- Moving `角色管理` from left to right changed the assigned count from `1` to `3` and retained the `系统管理 > 角色管理` hierarchy.
- Closing and reopening the dialog reset both search inputs to empty.
- No save request was submitted during QA.
- The final sizing change introduces no new interaction logic; `npm run test:functional` and `npm run build` both pass.
- Current automated browser re-capture: unavailable because the in-app browser security policy blocked access after its local connection error page. No workaround or alternate browser surface was used.

## Comparison History

### Current Iteration

- No P0, P1, or P2 issue was found in the centered `66.6667vw × 90vh` dual-tree implementation.
- The earlier fixed-height flat transfer layout was replaced as requested; its previous footer and search-state findings are no longer applicable to the current component structure.

## Implementation Checklist

- [x] Size the permission dialog to two thirds of the viewport width.
- [x] Fix the dialog height at 90% of the viewport with 5% space above and below.
- [x] Render both unassigned and assigned permissions as menu trees.
- [x] Preserve ancestor context on both sides.
- [x] Keep each long tree independently scrollable.
- [x] Keep the bottom action bar visible at a `690px` viewport height.
- [x] Verify tree search, selection, transfer, hierarchy preservation, cancel, and reopen behavior.

---

# List Button Drag Sorting Design QA

final result: blocked

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-96d949a1-8637-436a-a5cc-b88524873cee.png`
- Target state: both `工具栏按钮` and `操作列按钮` use the same drag-handle sorting interaction as `字段配置`; numeric sort inputs are removed.
- Static implementation review: passed.
- Production build: passed with `npm run build`.
- Page configuration audit: passed with `npm run test:page-config`.
- Functional test suite: passed with `npm run test:functional`.

## Blocking Reason

The current task inherits the existing restriction against opening or controlling the browser. A same-viewport implementation screenshot and side-by-side visual comparison could not be captured, so rendered interaction and pixel-level alignment remain unverified.

## Static Findings

- The numeric sort control was replaced by the existing Element Plus `Rank` icon and a stable `48px` sort column.
- Both button tabs share the same `Sortable` implementation and emit the same reorder event contract.
- Dragging a persisted button calls the existing POST order endpoint with optimistic revision and neighboring button IDs.
- Runtime rendering now prefers `orderKey`, while legacy configurations without it continue to fall back to `sort`.
- No P0, P1, or P2 issue was found in code, audit, functional, or build verification.

---

# 列表查询折叠预览一致性验收

final result: blocked

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-55af6eef-7ef4-470e-8eaa-b8e16baf3702.png`
- Target state: `收起时显示条件数`、`启用查询区折叠`在设计预览和实际列表中使用相同逻辑。
- Implementation approach: preview and runtime both render `EntityDataSearchForm`.

## Verification

- `npm run test:page-config`: passed.
- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Regression audit checks configured visible count, disabled folding, field slicing, and expanded-state branches.

## Blocking Reason

The existing restriction against opening or controlling the browser remains active. A rendered preview/runtime comparison and direct expand/collapse interaction test could not be captured, so visual design QA remains blocked.

---

# 列表配置页面宽度约束验收

final result: blocked

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-a732b831-e637-4764-8a2b-19ebd27e2ab8.png`
- Reported state: the list configuration page exceeds the browser's available width and clips the header actions and configuration panel on the right.

## Implemented Layout

- Added `min-width: 0` to the layout main content flex item.
- Constrained the list designer root, header, content area, card, tabs, tab content, and tab panes to the available `100%` width.
- Kept wide tab navigation horizontally scrollable.
- Moved header and field-toolbar wrapping to the `1280px` desktop breakpoint.
- Kept page-level overflow inside the configuration content area instead of expanding the browser viewport.

## Verification

- `npm run test:page-config`: passed.
- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.

## Blocking Reason

The existing restriction against opening or controlling the browser remains active. The corrected width could not be captured at the reported viewport, so rendered visual comparison remains blocked.
# 列表配置首次刷新宽度重算

## Design intent
- 列表配置页首次刷新时，应在侧栏和主内容区域完成布局后按真实可用宽度绘制字段表格。
- 浏览器宽度变化、配置容器宽度变化以及重新进入字段配置页签时，应使用同一套表格重算逻辑。

## Implemented
- 配置面板增加 `ResizeObserver`，容器宽度变化后调用 Element Plus 表格 `doLayout()`。
- 首次数据加载完成和字段配置页签重新显示后，主动重新计算固定列与主体列宽。
- 字段拖拽初始化改为从当前表格实例查找表体，避免页面中其他表格干扰。
- 页面销毁时清理尺寸监听、动画帧和拖拽实例。

## QA status
- Browser verification: `blocked`。当前任务约束不允许打开或控制浏览器。
- `npm run test:page-config`: passed.
- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.

final result: blocked
# 列表设置文案与未保存明细验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-8f87bd12-4f46-47b5-a50d-0d88dde5fcb9.png`
- Intended state: 查询区宽度配置含义明确，顶部未保存数量可悬停查看具体项目。

## Implemented

- `标签宽度` 调整为 `查询区标签宽度`。
- 顶部未保存数量增加悬停明细，并支持键盘聚焦查看。
- 列表设置按具体配置项展示，字段和按钮按中文名称展示。
- 未保存数量直接由明细列表计算，保证数量与提示内容一致。
- 提示内容较多时在固定高度内滚动。

## Verification

- `npm run test:page-config`: passed.
- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Browser rendering and screenshot comparison: blocked by the existing browser access restriction.

final result: blocked

---

# 首页待办任务辅助操作弹窗验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-ead16ba6-08f3-4708-838d-8329cb94b48a.png`
- Source pixels: `444 x 512`
- Target state: 首页待办任务的更多按钮无边框；转办、加签、知会弹窗展示只读流程名称和流程编码。
- Implementation screenshot: unavailable because the authenticated local session expired before capture.

## Implemented

- 更多入口改为 Element Plus `link` 图标按钮，保留可访问名称和点击下拉行为。
- 转办、加签和知会弹窗均增加只读的`流程名称`和`流程编码`。
- 三个弹窗打开时从当前待办行冻结展示值，不增加接口请求。
- 原有提交载荷保持不变，展示字段不会发送到任务操作接口。

## Verification

- `npm run test:functional`: passed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Browser route opened successfully, but the local authentication session had expired and redirected to the login page.

## Blocking Reason

The authenticated task list and its dialogs could not be rendered without submitting login credentials. Browser safety requires explicit authorization before transmitting credentials, so screenshot comparison and direct dialog interaction remain blocked.

final result: blocked

---

# 任务操作弹窗验收最终结论

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-cc08c8c5-245a-49d9-818a-5695d81cfd62.png`
- Implementation screenshot: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-transfer-dialog-wide.jpg`
- Full-view comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-dialog-before-after.jpg`
- Focused comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/task-dialog-focused-comparison.jpg`
- Viewport: `1280 x 720` CSS pixels at device scale factor `2`
- Source pixels: `1236 x 1112`; implementation pixels: `1280 x 720`
- Normalization: full comparison normalized to `720px` height; focused dialog crops normalized to `500px` height.
- State: authenticated homepage todo task, with transfer, add-sign, and manual-CC dialogs opened from the more-actions menu.

## Findings

- No actionable P0/P1/P2 differences remain.
- Typography preserves the existing system font stack, with an intentional monospaced process code.
- Spacing and layout use a responsive `min(680px, 92vw)` dialog width and aligned `96px` labels.
- Existing color tokens and semantic button colors are unchanged.
- No application image assets are present in the target region.
- Labels, values, placeholders, and action copy match the existing product content.

## Interaction And Console Verification

- All three dialogs opened successfully; read-only process values render as plain text and editable controls remain interactive.
- No new application console errors appeared after authentication or during dialog interaction.
- Initial unauthenticated `401` entries and an external browser-tool Statsig timeout are unrelated to the verified state.

## Comparison History

- Pass 1 found no actionable P0/P1/P2 issue, so no visual correction iteration was required.

final result: passed

---

# SLA 策略配置说明问号验收

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-fec62bd5-1411-4577-98f4-eb14924af2b5.png`
- Implementation screenshot: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/sla-policy-help-icons-final-v3.jpg`
- Full-view comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/sla-policy-help-full-comparison-v3.jpg`
- Focused comparison: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/sla-policy-help-focused-comparison-v3.jpg`
- First-pass implementation screenshot: `/Users/dawei/.codex/visualizations/2026/07/27/019fa384-9fc7-75e3-8ba0-530588987224/sla-policy-help-tooltip.jpg`
- Viewport: `1280 x 720` CSS pixels at device scale factor `2`
- Source pixels: `2434 x 1200`; implementation pixels: `1280 x 720`
- Normalization: the full source was resized proportionally to `720px` height. Dialog crops were normalized to `620px` height for the focused comparison.
- State: authenticated SLA strategy list, existing `PROJECT_APPROVAL_STANDARD` strategy opened in the configuration dialog.

## Findings

- No actionable P0/P1/P2 differences remain.
- Fonts and typography: the existing Element Plus/system font stack, weights, sizes, and line heights are unchanged. All eight labels and their help icons remain on one line.
- Spacing and layout rhythm: the form label width was adjusted from `110px` to `132px`, preserving the two-column grid and preventing long labels from wrapping without compressing the controls.
- Colors and visual tokens: the question icons use the existing secondary text token and the primary token for hover/focus feedback.
- Image quality and asset fidelity: the target region contains no application image assets. The standard Element Plus `QuestionFilled` icon is used instead of a custom drawing.
- Copy and content: explanations cover response and completion targets, work/natural time, manual pause, process suspension, pause limits, and the description field. The wording was checked against the runtime and work-calendar implementation.
- The red rectangles in the source are annotations identifying the requested fields, not product UI; they are intentionally absent from the implementation.

## Interaction And Accessibility Verification

- The rendered dialog contains eight unique help buttons with accessible names in the form `查看{字段}配置说明`.
- Each button is a native `type="button"` control inside an Element Plus tooltip, so mouse hover does not submit or modify the form.
- Browser measurements confirmed every help label has equal rendered and scroll height and no horizontal overflow.
- The existing numeric inputs, segmented controls, switches, text input, escalation table, cancel action, and save action remain present.
- No new application errors appeared after successful authentication and dialog interaction. Earlier logs came from the intentionally abandoned port `3001` login attempt and the unauthenticated first load on port `3000`.

## Comparison History

- Pass 1: `[P2]` the original `110px` label column caused `允许人工暂停`、`流程挂起暂停`、`最长暂停分钟` and `说明` to wrap after adding the icons.
- Fix: increased the dialog label width to `132px` and changed the tooltip trigger to a native borderless icon button.
- Pass 2: the final full-view and focused comparisons show all labels on one line, unchanged control alignment, and no remaining P0/P1/P2 differences.

## Implementation Checklist

- [x] Add help icons to all eight highlighted SLA properties.
- [x] Explain actual runtime behavior rather than generic configuration text.
- [x] Preserve required markers, form metadata, and existing control behavior.
- [x] Prevent long-label wrapping at the verified desktop viewport.
- [x] Add functional and UI configuration regression coverage.

final result: passed
