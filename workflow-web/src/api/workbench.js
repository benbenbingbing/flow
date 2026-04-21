import request from '@/utils/request'

// 获取工作台数据
export const getWorkbenchData = () => {
  return request.get('/workbench/data')
}
