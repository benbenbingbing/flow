import request from '@/utils/request'

const base = '/system/settings'
const keyPath = (key) => encodeURIComponent(key)

/** 读取已合并的个人偏好，同时返回个人覆盖版本供后续条件写入。 */
export const readMySetting = (key) => request.get(`${base}/mine/${keyPath(key)}`, { silentError: true })

/** 值以文本提交，由服务端校验；归属和描述字段由服务端填写。 */
export const saveMySetting = (key, data) => request.post(`${base}/mine/${keyPath(key)}`, data, { silentError: true })

export const resetMySetting = (key, data) => request.post(`${base}/mine/${keyPath(key)}/reset`, data, { silentError: true })
export const listSystemSettings = () => request.get(base, { silentError: true })
export const saveSystemSetting = (key, data) => request.post(`${base}/${keyPath(key)}`, data, { silentError: true })
export const resetSystemSetting = (key, data) => request.post(`${base}/${keyPath(key)}/reset`, data, { silentError: true })

/** 只发送当前编辑作用域的版本；继承到系统值时个人预期版本仍为空。 */
export function settingVersion(view) {
  return { expectedId: view?.override?.id ?? null, expectedVersion: view?.override?.version ?? null }
}
