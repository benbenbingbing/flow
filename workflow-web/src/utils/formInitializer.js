import request from '@/utils/request'
import { entityDataApi } from '@/api/entity'
import { hasFormInitializer, getFormInitializer } from './formInitializerRegistry'

/**
 * 根据 form.initConfig 执行初始化，返回要回填到表单的数据
 * @param {Object} initConfig form.initConfig 配置对象
 * @param {Object} context 上下文
 * @returns {Promise<Record<string, any>>}
 */
export async function executeFormInitializer(initConfig, context = {}) {
  if (!initConfig) return {}
  if (typeof initConfig === 'string') {
    try {
      initConfig = JSON.parse(initConfig)
    } catch (e) {
      console.warn('[formInitializer] initConfig 不是有效 JSON:', initConfig)
      return {}
    }
  }

  const type = initConfig.type
  switch (type) {
    case 'api':
      return executeApiInitializer(initConfig.api, context)
    case 'entity':
      return executeEntityInitializer(initConfig.entity, context)
    case 'static':
      return executeStaticInitializer(initConfig.static, context)
    case 'custom':
      return executeCustomInitializer(initConfig.custom, context)
    default:
      console.warn(`[formInitializer] 未知的初始化类型: ${type}`)
      return {}
  }
}

/**
 * 调用 HTTP 接口初始化
 */
async function executeApiInitializer(config, context) {
  if (!config) return {}

  const url = renderTemplate(config.url, context)
  const method = (config.method || 'GET').toLowerCase()
  const params = renderObjectTemplate(config.params || {}, context)
  const data = renderObjectTemplate(config.data || {}, context)
  const headers = config.headers || {}

  try {
    const res = await request({
      url,
      method,
      params,
      data,
      headers
    })
    const sourceData = config.responsePath ? getPathValue(res, config.responsePath) : res
    return applyMapping(sourceData, config.mapping)
  } catch (e) {
    console.warn('[formInitializer] API 初始化失败:', e)
    return {}
  }
}

/**
 * 查询实体数据初始化
 */
async function executeEntityInitializer(config, context) {
  if (!config || !config.entityCode) return {}

  try {
    const params = renderObjectTemplate(config.params || {}, context)
    const res = await entityDataApi.getList(config.entityCode, params)
    const records = Array.isArray(res) ? res : (res.records || [])
    const sourceData = config.index != null ? records[config.index] : records[0]
    if (!sourceData) return {}
    return applyMapping(sourceData.data || sourceData, config.mapping)
  } catch (e) {
    console.warn('[formInitializer] Entity 初始化失败:', e)
    return {}
  }
}

/**
 * 静态值初始化
 */
function executeStaticInitializer(config) {
  if (!config) return {}
  return { ...config }
}

/**
 * 自定义初始化器
 */
async function executeCustomInitializer(config, context) {
  if (!config || !config.name) {
    console.warn('[formInitializer] custom 初始化缺少 name')
    return {}
  }
  if (!hasFormInitializer(config.name)) {
    console.warn(`[formInitializer] 未找到自定义初始化器: ${config.name}`)
    return {}
  }
  try {
    const executor = getFormInitializer(config.name)
    return await executor(config, context)
  } catch (e) {
    console.warn('[formInitializer] custom 初始化执行失败:', e)
    return {}
  }
}

/**
 * 按 mapping 把源数据转换为目标表单数据
 * mapping: { targetFieldCode: 'sourceFieldPath' }
 */
function applyMapping(sourceData, mapping) {
  if (!sourceData || !mapping) return {}
  const result = {}
  Object.entries(mapping).forEach(([targetKey, sourcePath]) => {
    const value = getPathValue(sourceData, sourcePath)
    if (value !== undefined) {
      result[targetKey] = value
    }
  })
  return result
}

/**
 * 按路径取值，支持 a.b.c 和 a[0].b
 */
function getPathValue(obj, path) {
  if (!obj || !path) return undefined
  const keys = String(path).split(/\.|\[(\d+)\]/).filter(Boolean)
  let value = obj
  for (const key of keys) {
    if (value == null) return undefined
    value = value[key]
  }
  return value
}

/**
 * 简单模板渲染，支持 {{routeQuery.xxx}}、{{userStore.userInfo.xxx}}
 */
function renderTemplate(template, context) {
  if (typeof template !== 'string') return template
  return template.replace(/\{\{(.*?)\}\}/g, (_, expr) => {
    const value = getPathValue(context, expr.trim())
    return value !== undefined ? String(value) : ''
  })
}

function renderObjectTemplate(obj, context) {
  if (obj == null) return obj
  if (typeof obj === 'string') return renderTemplate(obj, context)
  if (Array.isArray(obj)) return obj.map(item => renderObjectTemplate(item, context))
  if (typeof obj === 'object') {
    const result = {}
    Object.entries(obj).forEach(([key, value]) => {
      result[key] = renderObjectTemplate(value, context)
    })
    return result
  }
  return obj
}
