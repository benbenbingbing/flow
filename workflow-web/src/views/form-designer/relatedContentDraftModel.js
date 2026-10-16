import { buildRelatedContentPayload } from '../../shared/related-content.js'

const clone = value => JSON.parse(JSON.stringify(value))
export const relatedContentIdentity = item => item.compositionKey || String(item.id)
export const relatedContentFingerprint = item => {
  const { expectedRevision, expectedOwnerRevision, ...payload } = buildRelatedContentPayload(item, 'FORM', '')
  return JSON.stringify(payload)
}

/**
 * 只识别服务端普通关系中已移除的接口和专属组件配置；回显名称、默认布局等普通归一化差异
 * 不应触发自动写入。原始配置必须在 normalize 清理前传入，否则无法判断需要落库。
 */
function requiresExtensionCleanup(item) {
  let config = item.config ?? item.configDocument
  if (typeof config === 'string') {
    try { config = JSON.parse(config) } catch { return false }
  }
  if (!config || ['SAME_RECORD', 'INTERFACE_SERVICE'].includes(config.relation?.type)) return false
  const special = config.specialHandling || {}
  if (['INTERFACE_SERVICE', 'CUSTOM_COMPONENT', 'BOTH'].includes(special.mode)) return true
  // 普通关系已没有失败策略编辑入口，旧隐藏/占位配置也需落库恢复可见错误。
  if (special.failurePolicy != null && special.failurePolicy !== 'ERROR') return true
  if (Array.isArray(special.actionServices) && special.actionServices.length) return true
  // 默认 version=1 是共享编辑占位；真实组件身份、参数或旧发布快照即使 mode=NONE 也需清理。
  if (Object.entries(special.customComponent || {}).some(([key, value]) =>
    key === 'version' ? value != null && Number(value) !== 1 : hasConfiguredValue(value))) return true
  // 空的共享编辑器占位不需要保存；映射、旧服务标识和固定接口快照都属于待移除配置。
  return Object.values(special.interfaceService || {}).some(hasConfiguredValue)
}

function hasConfiguredValue(value) {
  if (typeof value === 'string') return Boolean(value.trim())
  if (value && typeof value === 'object') return Object.keys(value).length > 0
  return value != null && value !== false
}

/**
 * 关联实例与节点共享设计器生命周期；基线只在服务端确认保存后推进。
 * 删除保留原修订号，失败时可重试，未持久化的新项删除则无需调用服务端。
 * 原始记录需要清理的扩展独立标记为待保存，不能被归一化后的相同指纹抵消。
 */
export function createRelatedContentDraftState() {
  const baselines = new Map()
  const deleted = new Map()
  const pendingExtensionCleanup = new Set()
  return {
    reset(items, { sourceItems = items } = {}) {
      baselines.clear()
      deleted.clear()
      pendingExtensionCleanup.clear()
      items.forEach(item => baselines.set(relatedContentIdentity(item), relatedContentFingerprint(item)))
      sourceItems.filter(requiresExtensionCleanup).forEach(item => pendingExtensionCleanup.add(relatedContentIdentity(item)))
    },
    dirty(item) { return pendingExtensionCleanup.has(relatedContentIdentity(item)) || baselines.get(relatedContentIdentity(item)) !== relatedContentFingerprint(item) },
    hasChanges(items) { return deleted.size > 0 || items.some(item => this.dirty(item)) },
    remove(item) {
      pendingExtensionCleanup.delete(relatedContentIdentity(item))
      if (item.id) deleted.set(relatedContentIdentity(item), clone(item))
      else baselines.delete(relatedContentIdentity(item))
    },
    deleted,
    accept(item) {
      pendingExtensionCleanup.delete(relatedContentIdentity(item))
      baselines.set(relatedContentIdentity(item), relatedContentFingerprint(item))
    }
  }
}

/**
 * 顺序保存各实例，每次使用上一操作返回的宿主修订号；不重读并吞掉并发冲突。
 * 成功项立即推进基线，失败项保留草稿及删除标记；调用方不得继续发布。
 * onSaved 负责替换当前项，不能重载整张表而丢失其他未保存修改。
 */
export async function persistRelatedContentDraft({ state, items, ownerId, api, getOwnerRevision, onRevision, onSaved, onlyKey }) {
  const pending = items.filter(item => (!onlyKey || relatedContentIdentity(item) === onlyKey) && state.dirty(item)).map(clone)
  // 在任何写入前预检全部待保存项，避免结构错误造成可预防的部分保存。
  const checked = []
  for (const item of pending) {
    const payload = buildRelatedContentPayload(item, 'FORM', ownerId)
    const result = await api.validate('FORM', ownerId, payload.config)
    if (result?.normalizedConfig) payload.config = result.normalizedConfig
    checked.push({ item, payload })
  }
  for (const { item, payload } of checked) {
    payload.expectedOwnerRevision = getOwnerRevision()
    const saved = item.id
      ? await api.update('FORM', ownerId, item.id, payload)
      : await api.create('FORM', ownerId, payload)
    onRevision(saved.ownerRevision)
    state.accept(saved)
    onSaved(saved)
  }
  if (!onlyKey) {
    for (const [key, item] of state.deleted) {
      const result = await api.remove('FORM', ownerId, item.id, item.revision, getOwnerRevision())
      onRevision(result?.ownerRevision)
      state.deleted.delete(key)
    }
  }
}
