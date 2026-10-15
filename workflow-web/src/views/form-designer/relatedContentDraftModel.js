import { buildRelatedContentPayload } from '../../shared/related-content.js'

const clone = value => JSON.parse(JSON.stringify(value))
export const relatedContentIdentity = item => item.compositionKey || String(item.id)
export const relatedContentFingerprint = item => {
  const { expectedRevision, expectedOwnerRevision, ...payload } = buildRelatedContentPayload(item, 'FORM', '')
  return JSON.stringify(payload)
}

/**
 * 只识别服务端普通关系中已移除的接口配置；回显名称、默认布局等普通归一化差异
 * 不应触发自动写入。原始配置必须在 normalize 清理前传入，否则无法判断需要落库。
 */
function requiresInterfaceCleanup(item) {
  let config = item.config ?? item.configDocument
  if (typeof config === 'string') {
    try { config = JSON.parse(config) } catch { return false }
  }
  if (!config || ['SAME_RECORD', 'INTERFACE_SERVICE'].includes(config.relation?.type)) return false
  const special = config.specialHandling || {}
  if (['INTERFACE_SERVICE', 'BOTH'].includes(special.mode)) return true
  if (Array.isArray(special.actionServices) && special.actionServices.length) return true
  // 空的共享编辑器占位不需要保存；映射、旧服务标识和固定接口快照都属于待移除配置。
  return Object.values(special.interfaceService || {}).some(value => {
    if (typeof value === 'string') return Boolean(value.trim())
    if (value && typeof value === 'object') return Object.keys(value).length > 0
    return value != null && value !== false
  })
}

/**
 * 关联实例与节点共享设计器生命周期；基线只在服务端确认保存后推进。
 * 删除保留原修订号，失败时可重试，未持久化的新项删除则无需调用服务端。
 * 原始记录需要清理的接口独立标记为待保存，不能被归一化后的相同指纹抵消。
 */
export function createRelatedContentDraftState() {
  const baselines = new Map()
  const deleted = new Map()
  const pendingInterfaceCleanup = new Set()
  return {
    reset(items, { sourceItems = items } = {}) {
      baselines.clear()
      deleted.clear()
      pendingInterfaceCleanup.clear()
      items.forEach(item => baselines.set(relatedContentIdentity(item), relatedContentFingerprint(item)))
      sourceItems.filter(requiresInterfaceCleanup).forEach(item => pendingInterfaceCleanup.add(relatedContentIdentity(item)))
    },
    dirty(item) { return pendingInterfaceCleanup.has(relatedContentIdentity(item)) || baselines.get(relatedContentIdentity(item)) !== relatedContentFingerprint(item) },
    hasChanges(items) { return deleted.size > 0 || items.some(item => this.dirty(item)) },
    remove(item) {
      pendingInterfaceCleanup.delete(relatedContentIdentity(item))
      if (item.id) deleted.set(relatedContentIdentity(item), clone(item))
      else baselines.delete(relatedContentIdentity(item))
    },
    deleted,
    accept(item) {
      pendingInterfaceCleanup.delete(relatedContentIdentity(item))
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
