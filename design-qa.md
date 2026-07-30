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
