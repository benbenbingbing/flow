/**
 * 自定义组件注册中心
 *
 * 二次开发者可通过 registerCustomListComponent / registerCustomFormComponent
 * 注册自定义列表/表单组件，替代默认渲染。
 *
 * 自定义列表组件接收的 props：
 *   - entityCode: 实体编码
 *   - entityDefinition: 实体定义对象
 *   - entityName: 实体名称
 *   - listConfig: 列表配置对象
 *   - listConfigFields: 列表字段配置数组
 *   - listFields: 实际显示的列表字段
 *   - queryFields: 查询字段数组
 *   - queryForm: 当前查询条件对象
 *   - dataList: 数据列表
 *   - loading: 页面加载状态
 *   - tableLoading: 表格加载状态
 *   - total: 总记录数
 *   - pageNum: 当前页码
 *   - pageSize: 每页大小
 *   - onSearch: 查询回调 () => void
 *   - onReset: 重置回调 () => void
 *   - onSizeChange: 分页大小变化 (size) => void
 *   - onPageChange: 页码变化 (page) => void
 *   - onCreate: 新增回调 () => void
 *   - onView: 查看回调 (row) => void
 *   - onEdit: 编辑回调 (row) => void
 *   - onDelete: 删除回调 (row) => void
 *   - onApprove: 审批回调 (row) => void
 *   - canApprove: 判断是否可审批 (row) => boolean
 *   - getStatusType: 获取状态样式 (status) => string
 *   - getStatusText: 获取状态文本 (status) => string
 *   - formatDate: 格式化日期 (dateStr) => string
 *
 * 自定义表单组件接收的 props：
 *   - form: 表单配置对象
 *   - modelValue: 表单数据对象
 *   - readonly: 是否只读
 *   - fields: 字段数组
 *   - linkageState: 联动状态对象 { visibility, disabled, required, options, values }
 *   - entityCode: 实体编码（数据录入场景）
 *   - entityDefinition: 实体定义对象（数据录入场景）
 *   - entityFields: 实体字段数组（数据录入场景）
 *   - mode: 'create' | 'edit'（数据录入场景）
 */

import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

const listRegistry = new Map()
const formRegistry = new Map()

// ========== 自定义列表组件 ==========

/**
 * 注册自定义列表组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomListComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  listRegistry.set(descriptor.name, descriptor)
}

/**
 * 获取自定义列表组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomListComponent(name) {
  return listRegistry.get(name)?.component
}

/**
 * 判断自定义列表组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomListComponent(name) {
  return listRegistry.has(name)
}

/**
 * 获取所有已注册的自定义列表组件名
 * @returns {string[]}
 */
export function getRegisteredCustomListNames() {
  return Array.from(listRegistry.keys())
}

export function getCustomListDescriptor(name) {
  return listRegistry.get(name)
}

export function getCustomListComponentOptions() {
  return Array.from(listRegistry.values()).map(({ component, ...descriptor }) => descriptor)
}

// ========== 自定义表单组件 ==========

/**
 * 注册自定义表单组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomFormComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  formRegistry.set(descriptor.name, descriptor)
}

/**
 * 获取自定义表单组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomFormComponent(name) {
  return formRegistry.get(name)?.component
}

/**
 * 判断自定义表单组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomFormComponent(name) {
  return formRegistry.has(name)
}

/**
 * 获取所有已注册的自定义表单组件名
 * @returns {string[]}
 */
export function getRegisteredCustomFormNames() {
  return Array.from(formRegistry.keys())
}

export function getCustomFormDescriptor(name) {
  return formRegistry.get(name)
}

export function getCustomFormComponentOptions() {
  return Array.from(formRegistry.values()).map(({ component, ...descriptor }) => descriptor)
}
