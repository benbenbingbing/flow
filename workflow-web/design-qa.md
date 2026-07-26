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
