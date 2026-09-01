import request from '@/utils/request'

const BASE_PATH = '/embed-management/v1'

const id = value => encodeURIComponent(String(value))
const viewPath = viewId => `${BASE_PATH}/views/${id(viewId)}`
const providerPath = providerId =>
  `${BASE_PATH}/identity-providers/${id(providerId)}`
const bindingPath = bindingId =>
  `${BASE_PATH}/identity-bindings/${id(bindingId)}`

/**
 * 兼容项目 request 已解包结果与测试/独立 transport 返回的 Axios 原始响应。
 */
export function unwrapEmbedManagementResponse(response) {
  const candidate = response?.data
    && typeof response.data === 'object'
    && typeof response.data.code !== 'undefined'
    ? response.data
    : response
  if (
    candidate
    && typeof candidate === 'object'
    && typeof candidate.code !== 'undefined'
  ) {
    if (![0, 200, '0', '200'].includes(candidate.code)) {
      const error = new Error(candidate.message || '请求失败')
      error.errorCode = candidate.errorCode
      error.currentData = candidate.data
      error.status = Number(candidate.code) || undefined
      throw error
    }
    return candidate.data
  }
  return candidate
}

/** Provider 验签材料不进入管理页面状态，编辑或轮换时必须重新输入。 */
export function sanitizeProvider(provider) {
  if (!provider || typeof provider !== 'object') return provider
  const { jwks: _discardedJwks, ...safe } = provider
  return safe
}

export function sanitizeProviderPage(page) {
  if (!page || typeof page !== 'object') return page
  const records = page.records || page.list || []
  return {
    ...page,
    records: records.map(sanitizeProvider),
    list: records.map(sanitizeProvider)
  }
}

function compact(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value
  return Object.fromEntries(
    Object.entries(value).filter(([, field]) => field !== undefined)
  )
}

/**
 * transport 可注入，便于对路径、CAS Body 和响应脱敏做无网络合同测试。
 */
export function createEmbedManagementApi(transport = request) {
  const call = (method, url, data, config = {}) => {
    const options = { silentError: true, ...config }
    const result = ['get', 'delete'].includes(method)
      ? transport[method](url, options)
      : transport[method](url, compact(data), options)
    return Promise.resolve(result).then(unwrapEmbedManagementResponse)
  }

  return {
    views: {
      page(params = {}) {
        return call('get', `${BASE_PATH}/views`, null, { params })
      },
      create(data) {
        return call('post', `${BASE_PATH}/views`, data)
      },
      get(viewId) {
        return call('get', viewPath(viewId))
      },
      draft(viewId) {
        return call('get', `${viewPath(viewId)}/draft`)
      },
      updateDraft(viewId, data) {
        return call('patch', `${viewPath(viewId)}/draft`, data)
      },
      changeStatus(viewId, data) {
        return call('post', `${viewPath(viewId)}/status`, data)
      },
    },
    grants: {
      list(viewId) {
        return call('get', `${viewPath(viewId)}/grants`)
      },
      upsert(viewId, applicationId, data) {
        return call(
          'put',
          `${viewPath(viewId)}/grants/${id(applicationId)}`,
          data
        )
      },
      changeStatus(viewId, applicationId, data) {
        return call(
          'post',
          `${viewPath(viewId)}/grants/${id(applicationId)}/status`,
          data
        )
      },
      revoke(viewId, applicationId, data) {
        return call(
          'post',
          `${viewPath(viewId)}/grants/${id(applicationId)}/revoke`,
          data
        )
      }
    },
    providers: {
      page(params = {}) {
        return call(
          'get',
          `${BASE_PATH}/identity-providers`,
          null,
          { params }
        ).then(sanitizeProviderPage)
      },
      create(data) {
        return call(
          'post',
          `${BASE_PATH}/identity-providers`,
          data
        ).then(sanitizeProvider)
      },
      get(providerId) {
        return call('get', providerPath(providerId))
          .then(sanitizeProvider)
      },
      update(providerId, data) {
        return call('patch', providerPath(providerId), data)
          .then(sanitizeProvider)
      },
      changeStatus(providerId, data) {
        return call(
          'post',
          `${providerPath(providerId)}/status`,
          data
        ).then(sanitizeProvider)
      },
      revoke(providerId, data) {
        return call(
          'post',
          `${providerPath(providerId)}/revoke`,
          data
        ).then(sanitizeProvider)
      },
      rotateKey(providerId, data) {
        return call(
          'post',
          `${providerPath(providerId)}/rotate-key`,
          data
        ).then(sanitizeProvider)
      }
    },
    bindings: {
      page(params = {}) {
        return call(
          'get',
          `${BASE_PATH}/identity-bindings`,
          null,
          { params }
        )
      },
      lookup(data) {
        return call(
          'post',
          `${BASE_PATH}/identity-bindings/lookup`,
          data
        )
      },
      create(data) {
        return call(
          'post',
          `${BASE_PATH}/identity-bindings`,
          data
        )
      },
      changeStatus(bindingId, data) {
        return call(
          'post',
          `${bindingPath(bindingId)}/status`,
          data
        )
      },
      revoke(bindingId, data) {
        return call(
          'post',
          `${bindingPath(bindingId)}/revoke`,
          data
        )
      }
    },
    operations: {
      launches(params = {}) {
        return call('get', `${BASE_PATH}/launches`, null, { params })
      },
      revokeLaunch(launchId) {
        return call(
          'post',
          `${BASE_PATH}/launches/${id(launchId)}/revoke`
        )
      },
      sessions(params = {}) {
        return call('get', `${BASE_PATH}/sessions`, null, { params })
      },
      revokeSession(sessionId, data) {
        return call(
          'post',
          `${BASE_PATH}/sessions/${id(sessionId)}/revoke`,
          data
        )
      },
      revokeViewSessions(viewId, data) {
        return call(
          'post',
          `${BASE_PATH}/views/${id(viewId)}/sessions/revoke`,
          data
        )
      },
      revokeApplicationSessions(applicationId, data) {
        return call(
          'post',
          `${BASE_PATH}/applications/${id(applicationId)}/sessions/revoke`,
          data
        )
      }
    }
  }
}

export const embedManagementApi = createEmbedManagementApi()
