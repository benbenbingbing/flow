import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

const browserSource = readFileSync(new URL('../public/app.js', import.meta.url), 'utf8')
  .replace(/^import FlowEmbed from [^\n]+\n/, '')

/** 最小 DOM 仅驱动真实宿主脚本的事件，不复制宿主的生命周期判断。 */
function createElement() {
  const handlers = new Map()
  return {
    children: [],
    dataset: {},
    value: '',
    disabled: false,
    hidden: false,
    classList: { contains() { return false } },
    addEventListener(type, handler) { handlers.set(type, handler) },
    dispatch(type) { return handlers.get(type)?.() },
    append(...children) {
      this.children.push(...children)
      if (!this.value && children[0]?.value) this.value = children[0].value
    },
    prepend(child) { this.children.unshift(child) },
    replaceChildren(...children) { this.children = children; this.value = '' },
    get firstElementChild() { return this.children[0] }
  }
}

/** 加载真实 Demo 前端，并记录后端 Launch 次数和 SDK 调用。 */
async function createBrowserHarness(destroy = async () => {}) {
  const elements = new Map()
  const commands = ['refresh', 'focus', 'theme'].map(command => ({
    ...createElement(), dataset: { command }
  }))
  const mounted = []
  const launchRequests = []
  let launchCount = 0
  const document = {
    querySelector(selector) {
      if (!elements.has(selector)) {
        const element = createElement()
        if (selector === '#form-presentation') element.value = 'seamless'
        elements.set(selector, element)
      }
      return elements.get(selector)
    },
    querySelectorAll() { return commands },
    createElement,
    documentElement: { dataset: {} }
  }
  const context = vm.createContext({
    document,
    window: createElement(),
    FlowEmbed: {
      mount(options) {
        const instance = {
          options,
          refreshCount: 0,
          destroy,
          refresh() { this.refreshCount += 1 },
          focus() {},
          setTheme() {}
        }
        mounted.push(instance)
        return instance
      }
    },
    async fetch(url, options = {}) {
      if (url === '/partner-api/embed-launch') {
        launchRequests.push(JSON.parse(options.body))
      }
      const data = url === '/partner-api/demo-config'
        ? {
            hostOrigin: 'https://localhost:3443',
            embedOrigin: 'https://localhost:8443',
            defaultTargetKey: 'list',
            targets: [{ key: 'list', label: '列表', viewKey: 'req-list', allowedEntryModes: ['LIST'] }]
          }
        : { launchId: `launch-${++launchCount}`, launchCode: 'one-time-code', channelId: 'channel-id' }
      return { ok: true, async text() { return JSON.stringify(data) } }
    }
  })
  vm.runInContext(browserSource, context)
  await new Promise(resolve => setImmediate(resolve))
  return {
    commands,
    mounted,
    launchRequests,
    launchCount: () => launchCount,
    element: selector => elements.get(selector),
    launch: () => elements.get('#launch-button').dispatch('click'),
    close: () => elements.get('#destroy-button').dispatch('click')
  }
}

test('浏览器把所选表单展示方式提交给自己的 Partner Backend', async () => {
  const defaultBrowser = await createBrowserHarness()
  assert.equal(defaultBrowser.element('#form-presentation').value, 'seamless')
  await defaultBrowser.launch()
  assert.equal(defaultBrowser.launchRequests[0].formPresentation, 'seamless')

  const dialogBrowser = await createBrowserHarness()
  dialogBrowser.element('#form-presentation').value = 'dialog'
  await dialogBrowser.launch()
  assert.equal(dialogBrowser.launchRequests[0].formPresentation, 'dialog')
})

test('安全通道 connected 时继续禁用命令，initialized 后才允许宿主刷新', async () => {
  const browser = await createBrowserHarness()
  await browser.launch()
  assert.equal(browser.launchCount(), 1)
  const widget = browser.mounted[0]
  widget.options.onEvent({ type: 'connected' })
  assert.ok(browser.commands.every(command => command.disabled))
  widget.options.onEvent({ type: 'initialized', payload: { viewKey: 'req-list' } })
  assert.ok(browser.commands.every(command => !command.disabled))
  browser.commands[0].dispatch('click')
  assert.equal(widget.refreshCount, 1)
})

test('注销确认在途时不允许额外 Launch，确认成功后才创建下一条', async () => {
  let acknowledgeLogout
  const browser = await createBrowserHarness(() => new Promise(resolve => { acknowledgeLogout = resolve }))
  await browser.launch()
  const reopening = browser.launch()
  await browser.launch()
  assert.equal(browser.launchCount(), 1)
  assert.equal(browser.element('#launch-button').disabled, true)
  assert.equal(browser.element('#form-presentation').disabled, true)
  acknowledgeLogout()
  await reopening
  assert.equal(browser.launchCount(), 2)
  assert.equal(browser.element('#launch-button').disabled, false)
  assert.equal(browser.element('#form-presentation').disabled, false)
})

for (const trigger of ['close', 'launch']) {
  test(`${trigger} 注销失败后保留回收未确认并阻止后续点击绕过`, async () => {
    const browser = await createBrowserHarness(async () => {
      throw Object.assign(new Error('注销确认超时'), { errorCode: 'FLOW_EMBED_DESTROY_TIMEOUT' })
    })
    await browser.launch()
    await browser[trigger]()
    assert.match(browser.element('#connection-badge').textContent, /回收未确认/)
    assert.equal(browser.element('#launch-button').disabled, true)
    assert.equal(browser.element('#destroy-button').disabled, true)
    assert.ok(browser.commands.every(command => command.disabled))
    await browser.launch()
    await browser.close()
    assert.equal(browser.launchCount(), 1)
  })
}
