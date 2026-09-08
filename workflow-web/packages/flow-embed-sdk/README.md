# @flow/embed-sdk

Flow Embed Runtime 的无框架宿主 SDK。它只负责创建 iframe、执行严格的
Window/MessageChannel 握手、发送白名单命令及分发受校验事件；不会写入
`localStorage`、`sessionStorage`、Cookie 或分析日志。

## 接入

```js
import FlowEmbed from '@flow/embed-sdk'

const channelId = crypto.randomUUID()
const launch = await fetch('/partner-api/work-orders/flow-embed-launch', {
  method: 'POST',
  credentials: 'same-origin',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    entry: { mode: 'LIST' },
    channelId,
    formPresentation: 'seamless'
  })
}).then(response => response.json())

let widget = FlowEmbed.mount({
  container: document.querySelector('#flow-work-orders'),
  embedUrl: launch.embedUrl,
  launchId: launch.launchId,
  launchCode: launch.launchCode,
  channelId,
  targetOrigin: 'https://embed.flow.example.com',
  height: { mode: 'auto', min: 480, max: 1200 },
  destroyTimeoutMs: 20000,
  onEvent(event) {
    if (event.type === 'selection.changed') console.log(event.payload.selection)
  },
  onViolation(error) {
    console.warn(error.errorCode)
  }
})
// SDK 已复制一次性 code，宿主不再保留 Launch 响应中的明文引用。
launch.launchCode = ''

widget.on('form.saved', event => updateHostRecord(event.payload))
widget.on('close.requested', () => {
  // Published Form 的关闭/取消只请求宿主关闭；等待 Logout 后才能重新 Launch。
  void widget.destroy().catch(reportEmbedLifecycleFailure)
})
widget.on('session.expired', () => {
  void widget.destroy().then(showRelaunchAction).catch(reportEmbedLifecycleFailure)
})
widget.on('initialized', () => {
  // mount 同步返回时还未握手；首屏初始化完成后才能启用宿主操作。
  widget.setTheme('dark')
  widget.setLocale('zh-CN')
  enableHostRefresh(() => widget.refresh())
})

async function reopenFlow() {
  // 在用户请求重新打开时调用；注销失败会抛出，阻止创建新 Launch。
  await widget.destroy()
  widget = await mountWithANewLaunch()
}
```

`formPresentation` 是创建 Launch 时提交给嵌入方自有后端的展示偏好，不是
`FlowEmbed.mount()` 参数。嵌入方后端必须只接受精确的小写 `seamless` 或 `dialog`，并把校验后的
值写入 Flow Launch 的 `ui.formPresentation`；缺失或 `null` 时使用默认值 `seamless`。
这个值也控制用户从嵌入列表内后续打开的表单。

`embedUrl` 必须是 HTTPS、Origin 必须与 `targetOrigin` 完全相等，且 path 必须精确为
`/embed/v1/launches/{launchId}`。URL 不允许 query 或 fragment，因此 `launchCode` 不会出现在
iframe URL、浏览器历史、Referrer 或服务端访问日志中。SDK 在通过 MessageChannel 传输 code 后
立即清除自己的明文引用；宿主也应丢弃 Launch 响应对象，不要把它写入任何浏览器存储。

V1 只允许 `refresh`、`set-theme`、`set-locale`、`focus`、`destroy` 五种命令。
改变用户、业务 Context、View 或记录入口必须由第三方后端重新创建 Launch。
`mount()` 同步返回实例，`connected` 只表示安全通道已建立；宿主操作应等到
`initialized` 后启用。普通命令返回 `requestId`，通过同一 `requestId` 的 `ack` / `error`
事件判断完成结果，不能把方法返回视为业务操作完成。
当前原生界面仅提供 `zh-CN` 文案；`setLocale` 对其它语言返回关联 `error`，并保持当前语言。

`destroy()` 是幂等的异步完成语义：iframe 只有在 `DELETE /api/embed/v1/session`
成功、服务端已释放活跃会话配额后才回传关联 ACK，SDK 随后才关闭
MessagePort 并移除 iframe。重复调用会返回同一个 Promise，不会重复发送命令。

如果 Logout 返回错误或超过 `destroyTimeoutMs`，SDK 会清理本地 iframe 并 reject；
握手 init 已发出但确认超时后调用 `destroy()` 也会 reject，因为子页可能已建立 Session。
宿主应保留“回收未确认”状态并禁用重新打开，等待 Flow 的 idle/absolute timeout 回收
或由管理员确认回收后再恢复。重复调用 `destroy()` 会复用原失败结果，不能当成重试注销。
整个宿主页面离开时浏览器无法等待 Promise；
iframe 会在 `pagehide` 中使用 keepalive Logout 尽力注销，服务端超时回收仍必须保留。

## TypeScript 契约

包内声明从 `docs/api/embed-v1.yaml` 的封闭消息协议确定性生成，包含
`FlowEmbedCapability`、`FlowEmbedCommandPayloadMap`、`FlowEmbedEventPayloadMap` 和按事件名收窄的
`FlowEmbedEventFor<T>`。修改 OpenAPI 后运行 `npm run generate:embed-sdk-types`；CI 中的
`npm run test:embed-sdk-types` 会在声明或 SDK 运行时常量发生漂移时失败。
