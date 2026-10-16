/** 级联字段统一使用完整路径数组；选项编码保留 string/number 类型，不能隐式互转。 */
export function cascaderPath(value) {
  return Array.isArray(value) ? value : value == null || value === '' ? [] : [value]
}

export function readCascaderConfig(field = {}) {
  let config = field.componentProps || {}
  if (typeof config === 'string') {
    try { config = JSON.parse(config) } catch { config = {} }
  }
  return {
    source: config.cascaderSource || 'STATIC',
    loadMode: config.cascaderLoadMode || 'ALL',
    options: config.cascaderOptions ?? field.cascaderOptions ?? []
  }
}

function validValue(value) {
  return (typeof value === 'string' && value.trim() !== '')
    || (typeof value === 'number' && Number.isFinite(value))
}

/** 拒绝含糊的节点结构，避免将错误响应展示成“没有下级”并保存错误路径。 */
export function normalizeCascaderOptions(value, loadMode = 'ALL', depth = 0, budget = { nodes: 0 }) {
  if (!Array.isArray(value)) throw new Error('级联接口必须返回节点数组')
  if (depth >= 32 && value.length) throw new Error('级联数据层级不能超过 32 层')
  const siblingValues = new Set()
  return value.map((item) => {
    if (++budget.nodes > 10000) throw new Error('级联数据不能超过 10000 个节点')
    if (!item || typeof item !== 'object' || !validValue(item.value)) {
      throw new Error('级联节点 value 必须是非空字符串或有限数字')
    }
    if (typeof item.label !== 'string' || !item.label.trim()) throw new Error('级联节点 label 必须是非空文本')
    const key = JSON.stringify(item.value)
    if (siblingValues.has(key)) throw new Error(`同一级级联节点 value 重复：${item.value}`)
    siblingValues.add(key)
    if (Object.hasOwn(item, 'disabled') && typeof item.disabled !== 'boolean') throw new Error('级联节点 disabled 必须是布尔值')
    if (Object.hasOwn(item, 'leaf') && typeof item.leaf !== 'boolean') throw new Error('级联节点 leaf 必须是布尔值')
    const node = { value: item.value, label: item.label, disabled: Boolean(item.disabled) }
    if (loadMode === 'CHILDREN') {
      if (typeof item.leaf !== 'boolean') throw new Error('逐级加载的每个节点必须提供 leaf 布尔值')
      if (Object.hasOwn(item, 'children')) throw new Error('逐级加载只返回直接下级节点，请勿返回 children')
      node.leaf = item.leaf
    } else {
      const children = Object.hasOwn(item, 'children') ? normalizeCascaderOptions(item.children, 'ALL', depth + 1, budget) : []
      node.leaf = children.length === 0
      if (item.leaf != null && item.leaf !== node.leaf) throw new Error('级联节点 leaf 与 children 不一致')
      if (children.length) node.children = children
    }
    return node
  })
}

/**
 * PC/移动共用的级联加载器。loadOptions 仅调用已发布 FIELD_OPTIONS 绑定；缓存限定当前配置和业务上下文。
 * configure 必须在依赖变化时调用，revision 隔离所有旧请求；错误不会清空字段值或把节点标记成叶子。
 */
export function createCascaderRuntime({ onChange = () => {} } = {}) {
  let config = {}
  let state = { options: [], loading: false, error: '', revision: 0 }
  let cache = new Map()
  let pending = new Map()
  let activePathKey = '[]'
  let failedPath = null
  let loadedNodes = 0
  let restoreRevision = 0
  const countNodes = nodes => nodes.reduce((count, node) => count + 1 + countNodes(node.children || []), 0)
  const snapshot = () => ({ ...state, options: [...state.options] })
  const publish = () => onChange(snapshot())
  function clearError() {
    // 切换到无需加载的叶节点时，也应让旧分支的失败失去提示权。
    activePathKey = null
    restoreRevision++
    state.error = ''
    failedPath = null
    publish()
  }

  function configure(next) {
    config = next
    cache = new Map()
    pending = new Map()
    failedPath = null
    loadedNodes = 0
    restoreRevision++
    activePathKey = '[]'
    state = { options: [], loading: false, error: '', revision: state.revision + 1 }
    publish()
  }

  function resolvePath(value) {
    const path = cascaderPath(value)
    const nodes = []
    let siblings = state.options
    for (const segment of path) {
      const node = siblings.find(item => item.value === segment)
      if (!node) break
      nodes.push(node)
      siblings = node.children || []
    }
    return { nodes, labels: nodes.map(node => node.label), complete: nodes.length === path.length }
  }

  async function loadChildren(value = []) {
    const path = cascaderPath(value)
    const pathKey = JSON.stringify(path)
    activePathKey = pathKey
    state.error = ''
    failedPath = null
    if (cache.has(pathKey)) { publish(); return cache.get(pathKey) }
    if (pending.has(pathKey)) { publish(); return pending.get(pathKey) }
    const revision = state.revision
    const requestConfig = config
    const parent = path.length ? resolvePath(path) : null
    const request = Promise.resolve().then(async () => {
      if (revision !== state.revision) return []
      if (path.length > 32 || path.some(segment => !validValue(segment))) throw new Error('级联路径格式错误')
      if (parent && !parent.complete) throw new Error('无法加载不属于当前级联树的节点')
      if (parent?.nodes.at(-1)?.leaf) return []
      if (path.length >= 32) throw new Error('级联数据层级不能超过 32 层')
      let result
      if (requestConfig.source === 'STATIC') {
        if (path.length) return parent.nodes.at(-1)?.children || []
        result = normalizeCascaderOptions(requestConfig.options || [])
      } else if (requestConfig.source === 'INTERFACE') {
        if (typeof requestConfig.loadOptions !== 'function') throw new Error('请为级联字段配置 FIELD_OPTIONS 数据接口')
        const mode = requestConfig.loadMode || 'ALL'
        if (!['ALL', 'CHILDREN'].includes(mode)) throw new Error('不支持的级联加载方式')
        if (mode === 'ALL' && path.length) return parent.nodes.at(-1)?.children || []
        const response = await requestConfig.loadOptions({
          loadMode: mode, parentValue: path.length ? path.at(-1) : null, path: [...path], level: path.length
        })
        result = normalizeCascaderOptions(response, mode)
        const oldNodes = parent ? parent.nodes.at(-1).children || [] : state.options
        if (mode === 'CHILDREN' && loadedNodes - countNodes(oldNodes) + result.length > 10000) throw new Error('级联数据不能超过 10000 个节点')
        if (path.length && !result.length) throw new Error('非末级节点没有返回下级数据，请检查接口的 leaf 标记')
      } else throw new Error('不支持的级联数据来源')
      if (revision !== state.revision) return []
      // 重试替换一个分支时，其下级缓存必须同步移除，不能将旧子项挂到新父节点上。
      for (const key of cache.keys()) {
        const cachedPath = JSON.parse(key)
        if (cachedPath.length > path.length && path.every((segment, index) => segment === cachedPath[index])) cache.delete(key)
      }
      cache.set(pathKey, result)
      if (parent) parent.nodes.at(-1).children = result
      else state.options = result
      loadedNodes = countNodes(state.options)
      return result
    }).catch(error => {
      if (revision !== state.revision) return []
      if (activePathKey === pathKey) {
        state.error = error?.message || '级联数据加载失败'
        failedPath = [...path]
      }
      throw error
    }).finally(() => {
      if (revision === state.revision) {
        pending.delete(pathKey)
        state.loading = pending.size > 0
        publish()
      }
    })
    pending.set(pathKey, request)
    state.loading = true
    publish()
    return request
  }

  /** 编辑/只读回显按完整路径逐层读取，不能仅凭末级编码跨分支寻找名称。 */
  async function ensurePath(value = [], { requireLeaf = false } = {}) {
    const path = cascaderPath(value)
    const revision = state.revision
    const restoration = ++restoreRevision
    const current = () => revision === state.revision && restoration === restoreRevision
    const superseded = { nodes: [], labels: [], complete: false }
    try {
      await loadChildren([])
      for (let index = 0; index < path.length; index++) {
        if (!current()) return superseded
        const prefix = path.slice(0, index + 1)
        if (!resolvePath(prefix).complete) {
          state.error = '已保存的级联路径在当前选项中不可用，请重新选择或重试'
          failedPath = path.slice(0, index)
          publish()
          throw new Error(state.error)
        }
        if (index < path.length - 1) await loadChildren(prefix)
      }
      if (!current()) return superseded
      const result = resolvePath(path)
      // 选择草稿允许停留在父级；已保存值必须是可选叶路径，但校验失败仍保留其名称和原值。
      if (requireLeaf && path.length && (result.nodes.some(node => node.disabled) || !result.nodes.at(-1)?.leaf)) {
        state.error = result.nodes.some(node => node.disabled)
          ? '已保存的级联路径包含禁用选项，请重新选择或重试'
          : '已保存的级联路径未选择完整末级，请重新选择'
        failedPath = []
        publish()
        throw new Error(state.error)
      }
      return result
    } catch (error) {
      // 新值/用户展开已经替代旧回显时，旧链路不再继续请求后续层或回传失败。
      if (!current()) return superseded
      throw error
    }
  }

  function retry() {
    const path = failedPath || []
    cache.delete(JSON.stringify(path))
    return loadChildren(path)
  }
  return { configure, snapshot, resolvePath, loadChildren, ensurePath, clearError, retry }
}
