# Role Permission Transfer Design QA

final result: passed

## Evidence

- Source visual truth: `/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-5a18837d-a0da-466a-9f06-000d629e299c.png`
- Implementation screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-transfer.png`
- Scrolled-state screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-transfer-scrolled.png`
- Focused dialog screenshot: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-transfer-dialog.png`
- Side-by-side comparison: `/Users/dawei/Documents/ddup/ai/flow/workflow-web/docs/visual-acceptance/role-permission-transfer-comparison.png`
- Viewport: `1562 x 690` CSS px.
- Source pixels: `3126 x 1380`; treated as an approximately 2x capture and normalized to `1562 x 690`.
- Implementation pixels: `1562 x 690`; effective capture density 1x.
- State: role management, first role's permission dialog open, `474 / 475` permissions assigned, both searches blank, lists at the top.

## Full-View Comparison

The source shows the original narrow tree dialog extending below the viewport. The implementation intentionally replaces that structure with a centered `960 x 620` transfer dialog. The two panels, transfer controls, internal scroll regions, assignment count, and footer actions are all visible in the same `1562 x 690` viewport.

## Focused Comparison

The focused dialog capture is sufficient to read the panel headers, search fields, permission paths, type tags, transfer buttons, assignment count, and footer actions. No additional crop was needed.

## Findings

No actionable P0, P1, or P2 findings remain.

- Fonts and typography: Element Plus and the application's existing Chinese font stack are preserved. Header, panel title, row label, type tag, and footer hierarchy are legible with no wrapping or overlap.
- Spacing and layout rhythm: The dialog has a fixed total height, balanced two-column tracks, consistent 24px outer padding, and a footer contained within the dialog frame.
- Colors and visual tokens: Existing Element Plus primary, neutral, warning, success, and info tokens are reused. Contrast and semantic type tags are consistent with the product.
- Image quality and asset fidelity: This screen contains no product imagery or custom raster assets. Existing icon and control rendering remains crisp.
- Copy and content: `未分配`, `已分配`, `搜索权限`, permission paths, type labels, assignment count, `取消`, and `确定` are accurate and concise.

## Interaction Verification

- Scrolled the assigned list by `1200px`: list scroll changed while page scroll remained `0`; the footer and confirmation button stayed fixed.
- Moved the only unassigned directory to the assigned panel: count changed from `474 / 475` to `475 / 475`, including hierarchy completion.
- Filtered assigned permissions by `角色管理`: only `系统管理 / 角色管理` remained visible.
- Closed and reopened after filtering: both search inputs reset to empty and the original `474 / 475` assignment state returned.
- No save request was submitted during QA.
- Browser console warnings/errors: none.

## Comparison History

### Iteration 1

- Earlier P2: At a 690px viewport height, the footer extended about 19px beyond the dialog frame.
- Fix: Set a fixed total dialog height with a flexible, overflow-contained body.
- Post-fix evidence: dialog bottom `654.5px`; footer bottom `638.5px`; confirm button bottom `622.5px`.

### Iteration 2

- Earlier P2: Closing and reopening retained the previous transfer search query.
- Fix: Enabled `destroy-on-close` for the permission dialog.
- Post-fix evidence: reopened search values were `["", ""]`, with `474` assigned items visible.

## Implementation Checklist

- [x] Replace the permission tree with an unassigned/assigned transfer control.
- [x] Preserve hierarchy context through full permission paths and type labels.
- [x] Keep the dialog height fixed and lists independently scrollable.
- [x] Keep footer actions visible at the tested desktop viewport.
- [x] Verify transfer, search, cancel, reopen, and hierarchy behavior.
