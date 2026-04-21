import request from '@/utils/request'

// 执行脚本
export const executeScript = (data) => {
  return request.post('/script-engine/execute', data)
}

// 验证脚本
export const validateScript = (data) => {
  return request.post('/script-engine/validate', data)
}

// 获取脚本模板
export const getScriptTemplates = () => {
  return request.get('/script-engine/templates')
}
