import request from '@/utils/request'

// ==================== 字典类型 ====================

// 分页查询字典类型
export const getDictPage = (params: any) => {
  return request.get('/system/dict/page', { params })
}

// 查询所有启用的字典
export const getDictList = () => {
  return request.get('/system/dict/list')
}

// 根据ID获取字典
export const getDictById = (id: string) => {
  return request.get(`/system/dict/${id}`)
}

// 创建字典类型
export const createDict = (data: any) => {
  return request.post('/system/dict', data)
}

export const createDictWithItems = (data: any) => {
  return request.post('/system/dict/with-items', data)
}

// 更新字典类型
export const updateDict = (id: string, data: any) => {
  return request.put(`/system/dict/${id}`, data)
}

// 删除字典类型
export const deleteDict = (id: string) => {
  return request.delete(`/system/dict/${id}`)
}

// 更新字典类型状态
export const updateDictStatus = (id: string, status: string) => {
  return request.put(`/system/dict/${id}/status?status=${status}`)
}

// ==================== 字典项 ====================

// 根据字典ID查询字典项树
export const getItemTreeByDictId = (dictId: string) => {
  return request.get(`/system/dict/item/tree/${dictId}`)
}

// 根据字典编码查询字典项树
export const getItemTreeByDictCode = (dictCode: string) => {
  return request.get(`/system/dict/item/tree/code/${dictCode}`)
}

// 创建字典项
export const createDictItem = (data: any) => {
  return request.post('/system/dict/item', data)
}

// 更新字典项
export const updateDictItem = (id: string, data: any) => {
  return request.put(`/system/dict/item/${id}`, data)
}

// 删除字典项
export const deleteDictItem = (id: string) => {
  return request.delete(`/system/dict/item/${id}`)
}

// 更新字典项状态
export const updateDictItemStatus = (id: string, status: string) => {
  return request.put(`/system/dict/item/${id}/status?status=${status}`)
}
