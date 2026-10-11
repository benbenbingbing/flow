import { buildRelatedContentPayload } from '../../shared/related-content.js'

const clone = value => JSON.parse(JSON.stringify(value))
export const relatedContentIdentity = item => item.compositionKey || String(item.id)
export const relatedContentFingerprint = item => {
  const { expectedRevision, expectedOwnerRevision, ...payload } = buildRelatedContentPayload(item, 'FORM', '')
  return JSON.stringify(payload)
}

/**
 * 关联实例与节点共享设计器生命周期；基线只在服务端确认保存后推进。
 * 删除保留原修订号，失败时可重试，未持久化的新项删除则无需调用服务端。
 */
export function createRelatedContentDraftState() {
  const baselines = new Map()
  const deleted = new Map()
  return {
    reset(items) {
      baselines.clear()
      deleted.clear()
      items.forEach(item => baselines.set(relatedContentIdentity(item), relatedContentFingerprint(item)))
    },
    dirty(item) { return baselines.get(relatedContentIdentity(item)) !== relatedContentFingerprint(item) },
    hasChanges(items) { return deleted.size > 0 || items.some(item => this.dirty(item)) },
    remove(item) {
      if (item.id) deleted.set(relatedContentIdentity(item), clone(item))
      else baselines.delete(relatedContentIdentity(item))
    },
    deleted,
    accept(item) { baselines.set(relatedContentIdentity(item), relatedContentFingerprint(item)) }
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
