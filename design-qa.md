# Entity Validation Rule Help Design QA

- source visual truth path: `/Users/dawei/.codex/attachments/ec70ad75-76c1-4afb-9ca3-887048c5bdca/image-1.png`
- implementation screenshot path: `/private/tmp/entity-validation-tooltip-2026-07-29.png`
- viewport: source screenshot represents approximately `1578 x 825` CSS px at 2x density; implementation browser viewport is `1728 x 878` CSS px at 1x density
- pixel dimensions: source `3156 x 1650`; implementation `1728 x 878`
- density normalization: source dimensions were divided by 2 for CSS-size comparison; no raster resampling was needed for the visual review
- state: decimal field selected, "数据与约束" expanded; implementation additionally shows the validation-rule help tooltip

## Full-view comparison evidence

The three-column entity designer, header actions, selected field, property-panel hierarchy, spacing, typography, colors, controls, and validation textarea remain consistent with the source. The only intended visible addition is the small question-mark icon next to "验证规则" and its transient tooltip. The wider implementation viewport reveals slightly more list content but does not change the layout structure or density.

## Focused region comparison evidence

A separate crop was not required because the source validation row and the implementation tooltip are both readable in the full-resolution captures. The question-mark icon is aligned with the existing label, and the tooltip uses the existing Element Plus typography, border, tag, and semantic blue tokens.

## Required fidelity surfaces

- Fonts and typography: existing font family, weights, sizes, line height, and zero letter spacing are preserved; code keys use a readable monospace treatment.
- Spacing and layout rhythm: the property-panel form alignment remains unchanged; the tooltip is transient and does not resize the panel or field list.
- Colors and visual tokens: the icon, current-type marker, borders, text, and background reuse existing Element Plus colors.
- Image quality and asset fidelity: no image assets were introduced or replaced; the question mark uses the existing Element Plus icon component.
- Copy and content: all supported rule groups, keys, accepted values, and examples are present; the selected field type is clearly marked.

## Findings

No actionable P0, P1, or P2 visual differences were found. The viewport width differs from the source capture, but the responsive layout remains consistent and the difference does not hide or reflow the relevant controls.

## Interaction evidence

- Decimal field: tooltip marks "整数、小数" as the current type and shows `min` and `max`.
- Text field: tooltip marks "文本、长文本" as the current type and shows `minLength`, `maxLength`, and `format`.
- Date field: tooltip marks "其他字段类型" as the current type and states that no additional JSON rules apply.
- Invalid rule: `{"min":1}` on a reference field is blocked before persistence with a type-incompatibility warning.
- Test input was discarded by reloading the page; the entity returned to the saved state.

## Comparison history

Initial comparison found no actionable P0/P1/P2 mismatch, so no visual repair iteration was required.

final result: passed

---

# 实体配置版本历史触底分页验收

- source history screenshot: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-954eb40f-68b3-4135-8f10-dffa1b940d99.png`
- position reference screenshot: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-cd68ab2f-b40a-4f78-887c-aed0e583e375.png`
- implementation screenshot: `/private/tmp/entity-history-pagination-implemented.png`
- viewport: implementation and live geometry comparison used `1708 x 861` CSS px
- pixel dimensions: history source `3040 x 1764`; create-dialog source `3326 x 1806`; implementation `1708 x 861`
- state: “ZDW 需求条目”版本历史首屏，V18 至 V14，共 5 条记录
- comparison input: the two source screenshots and the implementation screenshot were inspected together at original detail.

## Geometry evidence

The live create-data dialog and history dialog were measured in the same authenticated browser viewport. Both resolve to `top=25.828px`, `left=213.5px`, and `width=1281px`, so the history dialog matches the create-data dialog's `top=3vh`, centered `left`, and `width=75%`. The history dialog height is fixed at `809.336px` (`94vh`). Its dedicated scroll container has `clientHeight=615px`, so the header and footer remain fixed while only the timeline scrolls.

## Visual comparison evidence

The timeline, cards, version labels, status tags, metadata, diff link, field-detail row, spacing, borders, typography, and colors remain consistent with the source. The only intended visible changes are the wider/create-dialog-aligned modal geometry and fixed-height content viewport. No image assets were added or replaced.

## Findings

No actionable P0, P1, or P2 visual differences were found. The blue V18 border in the source is a transient hover state; the non-hover implementation uses the existing neutral card border.

## Interaction evidence

- Opening history waits for the first page before showing the dialog, matching the create-data dialog's prepared-then-open behavior.
- First page loads 5 records (`V18` through `V14`). Successive bottom scrolls load 10, 15, and finally all 18 records through `V1`.
- Scrolling again at the end performs no additional append and produces no duplicate versions.
- The last item of the first page (`V14`) still opens the correct `V13 → V14` comparison; only `V1` has no previous-version action.
- Closing and reopening restores the first 5 records and resets `scrollTop` to `0`.
- Request-generation and entity-id checks prevent a late response from a previously opened entity from contaminating the current history list.

## Console and verification evidence

- Browser console errors: `0`; only the existing Element Plus `el-link underline` deprecation warning remains.
- Frontend functional tests: passed.
- Frontend production build: passed; only existing ineffective-dynamic-import warnings remain.
- Backend pagination/diff tests: 4/4 passed.
- Backend package build: passed.
- `git diff --check`: passed.

final result: passed
