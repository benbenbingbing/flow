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
