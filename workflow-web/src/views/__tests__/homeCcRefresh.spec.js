import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'

const source = await readFile(new URL('../Home.vue', import.meta.url), 'utf8')
const inboxTemplate = source.slice(source.indexOf('<el-table v-else :data="ccList"'),
  source.indexOf('<!-- 分页 -->'))
assert.match(inboxTemplate, /prop="processName" label="流程名称"/, '流程名称直接展示知会快照字段')
assert.match(inboxTemplate, /prop="dataName" label="流程数据名称"/, '流程数据名称直接展示知会快照字段')
assert.doesNotMatch(inboxTemplate, /prop="operatorName"|label="知会人"/, '个人知会列表不再显示知会人列')
const script = parse(source).descriptor.scriptSetup.content
const ast = babelParse(script, { sourceType: 'module' })
const functions = new Set(['loadActiveTab', 'loadCcList', 'onApprovalSuccess'])
const code = ast.program.body.filter(node =>
  (node.type === 'FunctionDeclaration' && functions.has(node.id.name))
  || (node.type === 'ExpressionStatement' && node.expression.callee?.name === 'watch'
    && node.expression.arguments[0]?.name === 'activeTab')
).map(node => script.slice(node.start, node.end)).join('\n')

/** 执行首页真实页签切换和审批回调，复现先看空收件箱、后收到知会时缓存不更新。 */
function createPage() {
  let tabChanged
  let records = []
  const calls = { cc: 0, statistics: 0 }
  const state = {
    activeTab: { value: 'cc' },
    loadedTabs: { todo: true, done: true, started: true, cc: false },
    queryParams: { pageNum: 1 },
    selectedTodoRows: { value: [] },
    ccList: { value: [] }, ccTotal: { value: 0 },
    tabErrors: { cc: '' }, loading: { value: false }
  }
  const dependencies = {
    ...state,
    watch: (_ref, callback) => { tabChanged = callback },
    buildQueryParams: () => ({ pageNum: state.queryParams.pageNum }),
    getMyCcList: async () => { calls.cc++; return { records, total: records.length } },
    loadStatistics: async () => { calls.statistics++ },
    loadTodoList: async () => {},
    loadDoneList: async () => {},
    loadStartedList: async () => {}
  }
  const page = new Function(...Object.keys(dependencies),
    `${code}\nreturn { loadActiveTab, onApprovalSuccess }`
  )(...Object.values(dependencies))
  return {
    ...state, ...page, calls,
    setRecords(value) { records = value },
    async switchTab(value) {
      state.activeTab.value = value
      tabChanged()
      await Promise.resolve()
    }
  }
}

const page = createPage()
await page.loadActiveTab()
assert.deepEqual(page.ccList.value, [])
await page.switchTab('todo')
page.setRecords([{ id: 'created-cc', readStatus: 'UNREAD' }])
await page.switchTab('cc')
assert.equal(page.calls.cc, 2, '再次打开知会页签应重新查询，不能保留首次打开时的空列表')
assert.equal(page.ccList.value[0]?.id, 'created-cc')
assert.ok(page.calls.statistics > 0, '切换页签时应同步刷新未读徽标')

page.setRecords([{ id: 'completed-cc', readStatus: 'UNREAD' }])
await page.onApprovalSuccess()
assert.equal(page.ccList.value[0]?.id, 'completed-cc', '审批结束后当前知会页签应立即刷新')
console.log('首页知会刷新回归测试通过')
