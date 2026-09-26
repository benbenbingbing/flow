// 只识别运行时协议字段；业务 data/input/formData 内的同名字段绝不能被改写。
const TOKEN_KEYS = new Set([
  'releaseResolutionToken', 'formReleaseResolutionToken', 'listReleaseResolutionToken',
  'targetFormReleaseResolutionToken', 'targetReleaseResolutionToken',
  'targetListReleaseResolutionToken', 'targetDefaultFormReleaseResolutionToken'
])
const CONTEXT_KEYS = [
  'purpose', 'parentFormId', 'parentReleaseId', 'parentReleaseVersion', 'depth', 'userId',
  'processVersionHistoryId', 'nodeId', 'taskId', 'processInstanceId', 'entityCode', 'recordId'
]

/** 仅解码到期时间和比对坐标，不替代服务端验签、当前权限或活动任务校验。 */
function readClaims(token) {
  try {
    const parts = String(token).split('.')
    if (parts.length !== 2) return null
    const encoded = parts[0].replaceAll('-', '+').replaceAll('_', '/')
    const claims = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0))))
    if (!claims.parentFormId || !claims.parentReleaseId || !claims.purpose
      || !Number.isFinite(claims.expiresAt) || !Number.isFinite(claims.issuedAt)
      || claims.embedSessionId || claims.embedViewReleaseId) return null
    return claims
  } catch { return null }
}

/** 固定表单/任务身份及有效热修复快照，防止续期把用户草稿切到另一版本。 */
function identity(claims, owner) {
  return JSON.stringify([
    ...CONTEXT_KEYS.map(key => claims[key] ?? null),
    owner.effectiveReleaseId || owner.effectiveFormReleaseId || claims.parentReleaseId,
    owner.targetRecordId ?? null
  ])
}

function collectTokens(value, result = []) {
  if (!value || typeof value !== 'object') return result
  for (const [key, child] of Object.entries(value)) {
    if (TOKEN_KEYS.has(key)) {
      const claims = readClaims(child)
      if (claims) result.push({ token: child, claims, identity: identity(claims, value) })
    } else if (!['data', 'input', 'formData', 'snapshotDocument'].includes(key)) {
      collectTokens(child, result)
    }
  }
  return result
}

function contextChanged() {
  return Object.assign(new Error('当前表单版本或任务上下文已变化，填写内容已保留，请重新打开后核对'), {
    errorCode: 'FORM_RELEASE_CONTEXT_CHANGED'
  })
}

function objectPayload(value) {
  if (typeof value === 'string') {
    try { value = JSON.parse(value) } catch { return null }
  }
  return value && Object.getPrototypeOf(value) === Object.prototype ? value : null
}

/**
 * 续期仅重新执行这些明确只读的运行时解析入口，不能重放保存、事件脚本或审批请求。
 * 保留原入口及参数，使服务端重新检查当前用户对流程、任务、列表和父子表单的权限。
 */
function sourceRequest(config) {
  const path = String(config.url || '').split(/[?#]/, 1)[0]
  const method = String(config.method || 'get').toLowerCase()
  const readOnly = method === 'get' && [
    /\/entity-form-resolve\/new-data\/[^/]+$/,
    /\/entity-form-resolve\/view-data\/[^/]+\/[^/]+$/,
    /\/process-instance\/[^/]+\/progress$/,
    /\/process-task\/detail\/[^/]+$/,
    /\/entity-lists\/[^/]+\/[^/]+\/schema$/,
    /\/entity-forms\/[^/]+\/runtime-release$/
  ].some(pattern => pattern.test(path))
  const resolveComposition = method === 'post' && path.endsWith('/ui-runtime/view-compositions/resolve')
  if (!readOnly && !resolveComposition) return null
  return {
    url: config.url, method,
    params: config.params ? JSON.parse(JSON.stringify(config.params)) : undefined,
    data: resolveComposition ? JSON.parse(JSON.stringify(objectPayload(config.data))) : undefined
  }
}

/**
 * 每个请求运行时独享的发布令牌续期器。只缓存授权来源和令牌，不保存表单数据或替换页面对象。
 * 请求临近到期（最多提前 30 秒）或休眠恢复后，先通过原只读入口续期，再发送一次业务请求。
 * 子表单沿原请求依赖先续父令牌；同一来源的并发续期合并，失败后下次操作可重试。
 * @param {object} options request 为原 transport；getIdentity 隔离账号；now 用于到期判断和测试。
 */
export function createReleaseTokenRenewal({ request, getIdentity = () => '', now = Date.now }) {
  const tokens = new Map()
  // transport 在 Pinia 初始化前创建，身份必须延迟到第一次请求读取。
  let generation = 0, account, nextSourceId = 0

  function reset() { generation++; tokens.clear(); account = undefined }
  function syncIdentity() {
    const current = getIdentity()
    if (account !== current) { reset(); account = current }
  }

  /** 成功的只读响应建立续期来源；续期响应由 renew 原子比对后接收。 */
  function observe(value, config) {
    syncIdentity()
    if (config._releaseTokenGeneration !== generation || config._releaseTokenRenewal) return
    const query = sourceRequest(config)
    if (!query) return
    const source = { id: ++nextSourceId, query, entries: new Set(), pending: null }
    for (const candidate of collectTokens(value)) {
      // 已知令牌保留最初的授权入口，防止子接口回显父令牌造成续期自循环。
      if (tokens.has(candidate.token)) continue
      const entry = { ...candidate, source, invalid: false }
      source.entries.add(entry)
      tokens.set(candidate.token, entry)
    }
  }

  async function renew(token, trail) {
    const entry = tokens.get(token)
    if (!entry) return token
    if (entry.invalid) throw contextChanged()
    const skew = Math.min(30, Math.max(0, (entry.claims.expiresAt - entry.claims.issuedAt) / 2))
    if (entry.claims.expiresAt * 1000 > now() + skew * 1000) return entry.token
    const source = entry.source
    // 必须在复用 pending 前检查依赖，避免循环等待自己的续期 Promise。
    if (trail.includes(source.id) || trail.length >= 8) throw contextChanged()
    if (!source.pending) {
      const version = generation
      const pending = (async () => {
        const fresh = await request({
          ...source.query,
          silentError: true,
          _releaseTokenRenewal: true,
          _releaseTokenTrail: [...trail, source.id]
        })
        syncIdentity()
        if (version !== generation) throw contextChanged()
        const candidates = collectTokens(fresh)
        for (const sibling of source.entries) {
          const match = candidates.find(candidate => candidate.identity === sibling.identity
            && candidate.claims.expiresAt * 1000 > now())
          if (!match) {
            sibling.invalid = true
            continue
          }
          sibling.token = match.token
          sibling.claims = match.claims
          // 旧页面/子表行持有的副本仍映射到同一 entry，新响应无需覆盖用户草稿。
          tokens.set(match.token, sibling)
        }
      })()
      source.pending = pending
      pending.finally(() => { if (source.pending === pending) source.pending = null }).catch(() => {})
    }
    await source.pending
    if (entry.invalid) throw contextChanged()
    return entry.token
  }

  /** 只改协议顶层令牌，保留业务数据、版本号及写请求幂等信息。 */
  async function prepare(config) {
    syncIdentity()
    const version = generation
    config._releaseTokenGeneration = version
    for (const location of ['params', 'data']) {
      const payload = objectPayload(config[location])
      if (!payload) continue
      for (const key of TOKEN_KEYS) {
        if (!payload[key]) continue
        const token = await renew(payload[key], config._releaseTokenTrail || [])
        if (version !== generation) throw contextChanged()
        if (token !== payload[key]) config[location] = { ...objectPayload(config[location]), [key]: token }
      }
    }
    return config
  }

  return { prepare, observe, reset }
}
