const TOKEN_KEYS = ['releaseResolutionToken', 'formReleaseResolutionToken']
const CONTEXT_KEYS = ['purpose', 'processVersionHistoryId', 'nodeId', 'taskId', 'processInstanceId', 'entityCode', 'recordId', 'userId']

/** 只读取到期时间供客户端调度；签名、任务权限和发布版本仍由服务端校验。 */
function claims(token) {
  try {
    const payload = String(token).split('.')[0].replaceAll('-', '+').replaceAll('_', '/')
    return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(payload), char => char.charCodeAt(0))))
  } catch { return null }
}

/** 比较基础与有效发布坐标；续期仅可更新令牌，不能切到另一份热修复快照。 */
function coordinates(form = {}) {
  return [form.id || form.formId || form.entityFormId, form.runtimeReleaseId || form.formReleaseId, form.runtimeReleaseVersion ?? form.formReleaseVersion,
    form.effectiveReleaseId || form.effectiveFormReleaseId || form.runtimeReleaseId || form.formReleaseId].map(value => String(value ?? ''))
}

function contextChanged() {
  return Object.assign(new Error('当前任务或表单版本已变化，填写内容已保留，请重新核对任务后再提交'), { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
}

/**
 * 在流程详情的请求发出前更新根表单解析令牌，兼容子表行持有的根令牌副本。
 * loadForm 必须重新经过服务端流程/任务鉴权；只接收相同任务、相同发布快照的令牌。
 * 不替换表单定义或记录，不重放已经发送的写请求；并发字段操作共用一次续期。
 * @param {object} options getForm 读取当前表单，loadForm 重获授权，now 提供过期判断时间。
 * @returns {object} prepare 在请求发出前更新顶层令牌，reset 在切换任务或表单时清除旧续期状态。
 */
export function createFormReleaseSession({ getForm, loadForm, now = Date.now }) {
  const knownTokens = new Set()
  // generation 使切换页面前的在途续期失效；pending 让同一页面的并发请求共用一次鉴权查询。
  let generation = 0, pending = null

  function reset() { generation++; pending = null; knownTokens.clear() }

  /** 仅续已由当前表单持有的令牌；未知令牌原样返回，最终由服务端拒绝。 */
  async function renew(token) {
    const form = getForm(), current = form?.releaseResolutionToken
    if (current) knownTokens.add(current)
    if (!token || !knownTokens.has(token)) return token
    const before = claims(current)
    if (!Number.isFinite(before?.expiresAt) || before.expiresAt * 1000 > now() + 30_000) return current
    if (!pending) {
      const version = generation, identity = coordinates(form)
      const promise = (async () => {
        const fresh = await loadForm(), after = claims(fresh?.releaseResolutionToken)
        if (version !== generation || getForm() !== form) throw contextChanged()
        // 重新查询只续授权，不能把旧草稿悄悄迁移到另一个节点、热修复或新发布版本。
        if (JSON.stringify(coordinates(fresh)) !== JSON.stringify(identity)
          || !after || !Number.isFinite(after.expiresAt) || after.expiresAt * 1000 <= now()
          || CONTEXT_KEYS.some(key => String(before[key] ?? '') !== String(after[key] ?? ''))) throw contextChanged()
        knownTokens.add(fresh.releaseResolutionToken)
        form.releaseResolutionToken = fresh.releaseResolutionToken
        return fresh.releaseResolutionToken
      })()
      pending = promise
      // 网络失败后允许下一次操作再次续期；页面切换后的旧 finally 不得清除新页面的请求。
      promise.finally(() => { if (pending === promise) pending = null }).catch(() => {})
    }
    return pending
  }

  /** 仅处理协议顶层令牌，不遍历 formData/input，避免改写同名业务字段。 */
  async function prepare(config) {
    for (const location of ['data', 'params']) {
      const payload = config[location]
      if (!payload || Object.getPrototypeOf(payload) !== Object.prototype) continue
      for (const key of TOKEN_KEYS) {
        if (!payload[key]) continue
        const token = await renew(payload[key])
        if (token !== payload[key]) config[location] = { ...config[location], [key]: token }
      }
    }
    return config
  }
  return { prepare, reset }
}
