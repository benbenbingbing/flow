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

---

# Flow Native Embed FORM — Design QA

Status: Chrome 原生 FORM 主路径与 revision 4 运行时契约通过；Edge、Firefox、Safari 和第二个权限用户仍待专项矩阵验收。

## 本次验收坐标

- 第三方宿主：`https://localhost:3443`
- Embed Origin：`https://localhost:8443`
- 稳定 View Key：`zdwreq-form-demo`
- Session 运行快照（内部证据）：`evr_3a33ffaf14bc4e669ef276b280f9b594`；不是管理端可选发布版本
- Form Release：`e4df6db5f763bfd6b1c6fc7f0eb8eb53` / V3
- 映射 Flow 用户：`lisi` / `2038628006255251457`，显示名“李四”
- 入口：`CREATE`
- Chrome 视口：`2310 x 1662` CSS px
- 实现截图：`/Users/dawei/Documents/ddup/ai/flow/.codex-artifacts/embed-demo/embed-native-chrome-2310x1662.png`
- 用户提供的管理端预览参考：`/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-36a56062-5020-4848-bf85-8c9842719dce.png`
- 用户提供的旧 Embed 结果：`/var/folders/vd/668ws5sn77l5xxnb85xd9mtc0000gn/T/codex-clipboard-cd3868db-156a-47ee-8716-7a0ff27efbf1.png`

参考图是管理端“表单预览”，不是映射用户的 Published Runtime，因此不能拿它作为权限按钮和运行状态的像素级真值。本次把参考图与同尺寸实现截图放入同一比较输入，核对字段顺序、控件种类、富文本工具栏和布局结构；权限按钮、标题和“草稿”状态以映射用户的 Flow 原生运行时为准。

## Chrome 实测结果

- iframe 中完整出现 8 个发布字段：名称、数据编码、状态、需求描述、流程实例 ID、流程结束时间、流程开始时间、提交人 ID；不再只有富文本。
- 富文本使用完整 Flow 工具栏；日期点击后出现原生日期/时间 Dialog，状态点击后出现包含草稿、处理中、已完成、已终止、已撤回的原生 Select Popper。两个弹层都位于 iframe 文档内。
- 操作栏由同一次 Flow 权限解析返回：取消、重置、保存、保存并发起流程；服务端记录 `buttonCount=4`、`visibleCount=4`、`enabledCount=4`。
- 网络只出现 Flow 原生运行时端点，包括 `/api/entity/code/ZDWREQ`、`/api/entity-forms/{formId}/runtime-release`、`/api/entity-status/list/ZDWREQ`、`/api/ui-runtime/events/FORM_OPEN/execute` 和 `/api/ui-runtime/form-actions/resolve`；没有 `/api/embed/v1/runtime/form*` 投影请求。
- Chrome 控制台日志为空；未发现 CSP、Vue 组件解析、Cookie、普通登录刷新或 `/login` 重定向错误。
- 14:56:08 创建 Session 后，根令牌及派生令牌均固定到 15:26:08 的 Session 绝对到期点。15:07:07（超过旧 5 分钟边界）点击“重置”，`FORM_RESET` 返回 200。
- 点击“关闭嵌入”后 iframe 被移除，`DELETE /api/embed/v1/session` 返回 204；随后创建新 Launch，再次完整显示 8 个字段与 4 个动作，没有活动 Session 上限错误。

## Revision 4 最终契约验收

- 全量重建后连续执行两轮 CREATE：Launch → exchange → bootstrap → native-form-target → Flow 原生 `form-actions/resolve` → session → DELETE。
- 两轮均为 Launch 201、exchange 200、Session ACTIVE、DELETE 204；第一轮关闭后第二轮重新打开成功，没有 `Active Embed session limit has been reached`。
- 两轮 bootstrap 均固定到 `zdwreq-form-demo` / FORM / revision 4，并透传 `RECORD_VIEW`、`RECORD_CREATE`、`ACTION_EXECUTE`。
- 映射身份均为 `lisi` / 李四 / `common_user`，不是宿主伪造身份；Bootstrap 与 native-form-target 的 entity、form 和 Form Release 坐标完全一致。
- Flow 原生动作解析返回取消、重置、保存、保存并发起流程，四个按钮均 `visible=true`、`enabled=true`。
- Launch Code、Access Token 和 release-resolution token 在验收脚本中只保存在内存，未写入日志或本文件。

## Comparison setup

- Reference: Flow native runtime page using the mapped Flow user.
- Prototype: third-party host iframe opened from the stable `/embed/v1/launches/{launchId}` URL.
- Both surfaces must use the same active Form Release, entry mode, record/context, locale, theme, viewport size and device pixel ratio.
- Capture the reference and iframe in one browser session and compose both full-resolution screenshots into one same-viewport comparison input.
- Record the exact `viewKey`, Form Release id/version, mapped user, mode, record id (if VIEW), viewport, DPR and capture time beside the evidence.
- Do not compare an admin design draft with a published runtime, or different users/records/states.

## Native component fidelity

- [x] 本次 V3 的字段数量、标签、顺序、必填标记和运行态状态与同一 Published Form 一致。
- [x] Embed 直接挂载 `EntityDataFormDialog` / `EntityApprovalDialog` 和完整 Flow Registry，不含 Embed 字段 `componentMap`。
- [x] 富文本工具栏来自 Flow 原生组件。
- [x] 日期/时间面板来自 Flow 原生组件并 Teleport 到 iframe body。
- [x] 状态下拉 Popper 来自 Flow 原生组件并 Teleport 到 iframe body。
- [x] 本次 CREATE 操作栏按映射用户的 Flow 权限解析。
- [ ] 文件、引用、级联、子表单、子列表、自定义扩展和确认框需要各准备一份 Published Form 样本做专项交互回归；它们不走单独 Embed 渲染器。

## Permission and data identity

- [x] Session 服务端身份为 Flow user id `2038628006255251457`，不是只传显示名。
- [x] 委托请求恢复该用户的 `UserContext` 后继续经过普通 EndpointAuthorization、对象权限和 DataScope。
- [x] Application Grant/Capability 只做上限求交，不能产生映射用户没有的按钮或数据权限。
- [ ] 第二个低权限用户的字段/按钮差异仍需产品验收样本。

## Single-runtime architecture evidence

- [x] Direct FORM 与 LIST→CREATE/VIEW 共用 `NativeEmbeddedFormPage`。
- [x] 页面直接挂载 Flow 原生 Dialog 和完整 Registry。
- [x] 活跃 FORM 路径没有 `EmbedFormRuntime`、`normalizeEmbedForm` 或 Embed `componentMap`。
- [x] 实际网络没有 `/api/embed/v1/runtime/form*` 请求。
- [x] 原生 API 携带 opaque Embed Bearer 与 `X-Flow-Embed-Protocol: 1`，坐标由 Session/不可变 Release 校验。
- [x] 宿主只接收 `embedUrl`、Launch 元数据和安全事件，不传 entity/form/release/API 坐标。
- [ ] 发布一个新的自定义扩展作为 canary，证明零 `src/embed/**` 修改；架构测试已验证主应用与 Embed 共用同一注册入口。

## Stable snapshot behavior

- [x] 代码与回归测试覆盖统一版本行为：每个新 Launch 解析最新 ACTIVE，已打开 Session 固定启动时运行快照。
- [x] 第三方始终使用 `zdwreq-form-demo` 和 `/embed/v1/launches/{launchId}`，不传表单字段或组件参数。

## Browser and CSP evidence

- [x] Chrome 完成日期、下拉、Dialog、长会话重置、关闭和重开，无 CSP 违规。
- [x] `script-src` 保持严格；未开放第三方 inline script 或 `unsafe-eval`。
- [x] Element Plus/Flow 的 Popper/Dialog 动态定位在 iframe 内正常。
- [x] Chrome 控制台无错误或警告。
- [x] 无普通登录刷新、Cookie、`/login` 跳转或 URL/日志中的 Token。
- [ ] Edge、Firefox、Safari 仍待浏览器矩阵。

## Required screenshots and traces

- [x] 同尺寸参考/实现比较输入和完整第三方宿主 + iframe 截图。
- [x] 日期/时间面板、单选状态下拉、富文本工具栏和当前用户操作栏的 DOM 证据。
- [x] 服务端网络日志证明原生端点、长时令牌、Logout 204，且无旧 FORM 投影链。
- [x] Chrome Console/CSP 证据。
- [ ] 第二权限用户、确认/审批状态、多选和未来组件 canary 的专项证据。

Final result: Chrome 当前 FORM 发布样本通过；浏览器矩阵和扩展样本待后续专项验收。
