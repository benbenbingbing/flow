import FlowEmbed from '/vendor/flow-embed-sdk/index.js'

const elements = {
  hostOrigin: document.querySelector('#host-origin'),
  embedOrigin: document.querySelector('#embed-origin'),
  viewKey: document.querySelector('#view-key'),
  runtimeViewKey: document.querySelector('#runtime-view-key'),
  subjectHint: document.querySelector('#subject-hint'),
  mode: document.querySelector('#entry-mode'),
  recordField: document.querySelector('#record-field'),
  recordId: document.querySelector('#record-id'),
  launch: document.querySelector('#launch-button'),
  destroy: document.querySelector('#destroy-button'),
  badge: document.querySelector('#connection-badge'),
  empty: document.querySelector('#embed-empty'),
  container: document.querySelector('#flow-embed-container'),
  eventLog: document.querySelector('#event-log'),
  commands: [...document.querySelectorAll('[data-command]')]
}

let config
let widget
let destroyPromise
let launchInProgress = false
let darkTheme = false

function setStatus(text, kind = 'idle') {
  elements.badge.textContent = text
  elements.badge.className = `status-badge ${kind}`
}

function setCommandsEnabled(enabled) {
  elements.destroy.disabled = !widget
  for (const button of elements.commands) button.disabled = !enabled
}

function safeEventSummary(event) {
  if (event.type === 'connected') return { launchId: event.launchId }
  if (event.type === 'resize') return { height: event.payload?.height }
  return event.payload || {}
}

function addEvent(type, details = {}) {
  if (elements.eventLog.firstElementChild?.classList.contains('muted')) {
    elements.eventLog.replaceChildren()
  }
  const item = document.createElement('li')
  const heading = document.createElement('div')
  const name = document.createElement('strong')
  const time = document.createElement('time')
  const payload = document.createElement('pre')
  name.textContent = type
  time.textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  payload.textContent = JSON.stringify(details, null, 2)
  heading.append(name, time)
  item.append(heading, payload)
  elements.eventLog.prepend(item)
  while (elements.eventLog.children.length > 12) elements.eventLog.lastElementChild.remove()
}

function publicError(error) {
  const code = error?.errorCode || error?.payload?.errorCode || 'FLOW_DEMO_ERROR'
  const message = error?.message || '嵌入启动失败'
  return { code, message, traceId: error?.traceId || error?.payload?.traceId || null }
}

function resetEmbedSurface() {
  elements.container.replaceChildren()
  elements.empty.hidden = false
  setCommandsEnabled(false)
}

/**
 * 等待 SDK 与 iframe 完成服务端 Session Logout，再清理宿主容器。
 * 同一次关闭会复用 Promise，避免重复 destroy 或在旧 Session 仍 ACTIVE 时创建新 Launch。
 */
async function destroyWidget({ showIdleStatus = true } = {}) {
  if (destroyPromise) return destroyPromise

  const retiringWidget = widget
  if (!retiringWidget) {
    resetEmbedSurface()
    if (showIdleStatus) setStatus('尚未挂载', 'idle')
    return
  }

  elements.launch.disabled = true
  elements.destroy.disabled = true
  for (const button of elements.commands) button.disabled = true
  setStatus('正在安全注销 Flow 会话…', 'loading')

  const pendingDestroy = (async () => {
    try {
      await retiringWidget.destroy()
      if (widget === retiringWidget) widget = undefined
      resetEmbedSurface()
      if (showIdleStatus) setStatus('尚未挂载', 'idle')
    } catch (error) {
      // SDK 在 Logout 失败或超时时会 fail-safe 拆除 iframe；宿主必须停止重开并显示错误。
      if (widget === retiringWidget) widget = undefined
      resetEmbedSurface()
      throw error
    } finally {
      if (destroyPromise === pendingDestroy) destroyPromise = undefined
      if (!launchInProgress) elements.launch.disabled = false
    }
  })()
  destroyPromise = pendingDestroy
  return pendingDestroy
}

async function readJson(response) {
  const text = await response.text()
  let document
  try {
    document = JSON.parse(text)
  } catch {
    throw Object.assign(new Error(`宿主后端返回 HTTP ${response.status}`), {
      errorCode: 'FLOW_DEMO_RESPONSE_INVALID'
    })
  }
  if (!response.ok) {
    throw Object.assign(new Error(document.message || `宿主后端返回 HTTP ${response.status}`), {
      errorCode: document.errorCode,
      traceId: document.traceId
    })
  }
  return document
}

async function launchEmbed() {
  if (launchInProgress || destroyPromise) return
  launchInProgress = true
  elements.launch.disabled = true
  try {
    try {
      // 重新打开必须先等旧 iframe 确认 Logout，否则会命中 Grant 的活动会话上限。
      await destroyWidget({ showIdleStatus: false })
    } catch (error) {
      const detail = publicError(error)
      addEvent('host.destroy.failed', detail)
      setStatus('旧会话注销失败，已停止重新打开', 'error')
      return
    }

    setStatus('第三方后端签发中…', 'loading')
    const mode = elements.mode.value
    const intent = mode === 'VIEW'
      ? { mode, recordId: elements.recordId.value.trim(), theme: darkTheme ? 'dark' : 'light' }
      : { mode, theme: darkTheme ? 'dark' : 'light' }
    const response = await fetch('/partner-api/embed-launch', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(intent)
    })
    const launch = await readJson(response)
    elements.empty.hidden = true
    setStatus('等待安全握手…', 'loading')
    widget = FlowEmbed.mount({
      container: elements.container,
      embedUrl: launch.embedUrl,
      launchId: launch.launchId,
      launchCode: launch.launchCode,
      channelId: launch.channelId,
      targetOrigin: config.embedOrigin,
      title: mode === 'LIST' ? 'Flow 需求列表' : 'Flow 需求表单',
      height: { mode: 'auto', min: 520, max: 1400, initial: 620 },
      onEvent(event) {
        addEvent(event.type, safeEventSummary(event))
        if (event.type === 'connected') {
          setStatus('安全通道已连接', 'connected')
          setCommandsEnabled(true)
        }
        if (event.type === 'initialized') setStatus('Flow 已加载', 'connected')
        if (event.type === 'form.saved') setStatus('表单已保存', 'success')
        if (event.type === 'close.requested') {
          // Published Form 的“关闭/取消”只提出宿主关闭请求；真正释放 Session 仍必须
          // 复用统一的 async destroy 链，不能由 iframe 直接删除宿主容器。
          setStatus('Flow 请求关闭，正在安全注销…', 'loading')
          void closeEmbed()
        }
        if (event.type === 'session.expired') {
          setStatus('会话已过期，请重新打开', 'error')
          setCommandsEnabled(false)
        }
        if (event.type === 'error') setStatus('嵌入运行错误', 'error')
      },
      onViolation(error) {
        addEvent('protocol.violation', publicError(error))
        setStatus('协议校验失败', 'error')
      }
    })
    // SDK 已复制一次性 code；立即清除宿主业务对象中的引用，且绝不写入 storage/URL/log。
    launch.launchCode = ''
    setCommandsEnabled(false)
    elements.destroy.disabled = false
  } catch (error) {
    const detail = publicError(error)
    addEvent('launch.failed', detail)
    setStatus(detail.code, 'error')
    elements.empty.hidden = false
  } finally {
    launchInProgress = false
    if (!destroyPromise) elements.launch.disabled = false
  }
}

async function closeEmbed() {
  if (launchInProgress || destroyPromise || !widget) return
  try {
    await destroyWidget()
    addEvent('host.destroyed', {})
  } catch (error) {
    const detail = publicError(error)
    addEvent('host.destroy.failed', detail)
    setStatus('会话注销失败，未重新打开', 'error')
  }
}

function updateEntryFields() {
  elements.recordField.hidden = elements.mode.value !== 'VIEW'
}

async function loadConfig() {
  try {
    config = await readJson(await fetch('/partner-api/demo-config', {
      credentials: 'same-origin',
      cache: 'no-store'
    }))
    elements.hostOrigin.textContent = config.hostOrigin
    elements.embedOrigin.textContent = config.embedOrigin
    elements.viewKey.textContent = config.viewKey
    elements.runtimeViewKey.textContent = config.viewKey
    elements.subjectHint.textContent = config.subjectHint
    for (const mode of config.allowedEntryModes) {
      const option = document.createElement('option')
      option.value = mode
      option.textContent = ({
        CREATE: '新建表单（CREATE）',
        VIEW: '查看记录（VIEW）',
        LIST: '列表（LIST）'
      })[mode] || mode
      elements.mode.append(option)
    }
    updateEntryFields()
  } catch (error) {
    const detail = publicError(error)
    addEvent('config.failed', detail)
    setStatus('示例配置加载失败', 'error')
    elements.launch.disabled = true
  }
}

elements.mode.addEventListener('change', updateEntryFields)
elements.launch.addEventListener('click', launchEmbed)
elements.destroy.addEventListener('click', closeEmbed)
for (const button of elements.commands) {
  button.addEventListener('click', () => {
    try {
      const command = button.dataset.command
      if (command === 'refresh') widget.refresh()
      if (command === 'focus') widget.focus()
      if (command === 'theme') {
        darkTheme = !darkTheme
        document.documentElement.dataset.theme = darkTheme ? 'dark' : 'light'
        widget.setTheme(darkTheme ? 'dark' : 'light')
      }
      addEvent(`host.${command}`, {})
    } catch (error) {
      addEvent('command.failed', publicError(error))
    }
  })
}
window.addEventListener('pagehide', () => {
  // pagehide 不能阻塞页面卸载；子端的 keepalive Logout 作为 best-effort 收尾。
  const pendingDestroy = destroyPromise || widget?.destroy()
  pendingDestroy?.catch(() => {})
}, { once: true })

loadConfig()
