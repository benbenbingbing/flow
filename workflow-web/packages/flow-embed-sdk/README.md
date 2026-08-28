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
  body: JSON.stringify({ entry: { mode: 'LIST' }, channelId })
}).then(response => response.json())

const widget = FlowEmbed.mount({
  container: document.querySelector('#flow-work-orders'),
  embedUrl: launch.embedUrl,
  launchId: launch.launchId,
  launchCode: launch.launchCode,
  channelId,
  targetOrigin: 'https://embed.flow.example.com',
  height: { mode: 'auto', min: 480, max: 1200 },
  onEvent(event) {
    if (event.type === 'selection.changed') console.log(event.payload.selection)
    if (event.type === 'session.expired') requestANewLaunch()
  },
  onViolation(error) {
    console.warn(error.errorCode)
  }
})

widget.on('form.saved', event => updateHostRecord(event.payload))
widget.refresh()
widget.setTheme('dark')
widget.setLocale('zh-CN')

// 页面卸载或框架组件卸载时必须销毁。
widget.destroy()
```

`embedUrl` 必须是 HTTPS、Origin 必须与 `targetOrigin` 完全相等，且 path 必须精确为
`/embed/v1/launches/{launchId}`。URL 不允许 query 或 fragment，因此 `launchCode` 不会出现在
iframe URL、浏览器历史、Referrer 或服务端访问日志中。SDK 在通过 MessageChannel 传输 code 后
立即清除自己的明文引用；宿主也应丢弃 Launch 响应对象，不要把它写入任何浏览器存储。

V1 只允许 `refresh`、`set-theme`、`set-locale`、`focus`、`destroy` 五种命令。
改变用户、业务 Context、View 或记录入口必须由第三方后端重新创建 Launch。

## TypeScript 契约

包内声明从 `docs/api/embed-v1.yaml` 的封闭消息协议确定性生成，包含
`FlowEmbedCapability`、`FlowEmbedCommandPayloadMap`、`FlowEmbedEventPayloadMap` 和按事件名收窄的
`FlowEmbedEventFor<T>`。修改 OpenAPI 后运行 `npm run generate:embed-sdk-types`；CI 中的
`npm run test:embed-sdk-types` 会在声明或 SDK 运行时常量发生漂移时失败。
