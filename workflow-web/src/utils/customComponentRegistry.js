/**
 * 自定义组件注册中心
 *
 * 二次开发者可通过 registerCustomListComponent / registerCustomFormComponent
 * 注册自定义列表/表单组件，替代默认渲染。
 *
 * 自定义列表组件运行时契约 v2：
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
 *   - config: viewConfig.customComponentProps
 *   - runtime: reload/search/reset/create/view/edit/delete/approve/exportData、
 *              canAction/getActionReason/viewConfig 聚合对象
 *   - 兼容事件: search/reset/sizeChange/pageChange/create/view/edit/delete/approve
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
 *   - mode: 'create' | 'edit' | 'approve' | 'view'
 *   - config: viewConfig.customComponentProps
 *   - context: 当前模式、实体、表单和记录等场景上下文
 * 组件通过 update:modelValue 更新业务字段对象，并通过 defineExpose({ validate })
 * 暴露异步提交校验。
 */

import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

const listRegistry = new Map()
const formRegistry = new Map()

function normalizeVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : 1
}

function parseRequestedVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : undefined
}

function registerVersionedComponent(registry, descriptor) {
  descriptor.version = normalizeVersion(descriptor.version)
  const versions = registry.get(descriptor.name) || new Map()
  versions.set(descriptor.version, descriptor)
  registry.set(descriptor.name, versions)
}

function getVersionedDescriptor(registry, name, version) {
  const versions = registry.get(name)
  if (!versions) return undefined
  if (version !== undefined && version !== null && version !== '') {
    const requestedVersion = parseRequestedVersion(version)
    return requestedVersion === undefined ? undefined : versions.get(requestedVersion)
  }
  return Array.from(versions.values())
    .sort((left, right) => right.version - left.version)[0]
}

function getVersionedOptions(registry) {
  return Array.from(registry.values())
    .map(versions => Array.from(versions.values())
      .sort((left, right) => right.version - left.version)[0])
    .sort((left, right) => left.name.localeCompare(right.name))
}

// ========== 自定义列表组件 ==========

/**
 * 注册自定义列表组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomListComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  registerVersionedComponent(listRegistry, descriptor)
}

/**
 * 获取自定义列表组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomListComponent(name, version) {
  return getCustomListDescriptor(name, version)?.component
}

/**
 * 判断自定义列表组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomListComponent(name, version) {
  return Boolean(getCustomListDescriptor(name, version))
}

/**
 * 获取所有已注册的自定义列表组件名
 * @returns {string[]}
 */
export function getRegisteredCustomListNames() {
  return Array.from(listRegistry.keys())
}

export function getCustomListDescriptor(name, version) {
  return getVersionedDescriptor(listRegistry, name, version)
}

export function getCustomListComponentOptions() {
  return getVersionedOptions(listRegistry)
    .map(({ component, ...descriptor }) => descriptor)
}

// ========== 自定义表单组件 ==========

/**
 * 注册自定义表单组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomFormComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  registerVersionedComponent(formRegistry, descriptor)
}

/**
 * 获取自定义表单组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomFormComponent(name, version) {
  return getCustomFormDescriptor(name, version)?.component
}

/**
 * 判断自定义表单组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomFormComponent(name, version) {
  return Boolean(getCustomFormDescriptor(name, version))
}

/**
 * 获取所有已注册的自定义表单组件名
 * @returns {string[]}
 */
export function getRegisteredCustomFormNames() {
  return Array.from(formRegistry.keys())
}

export function getCustomFormDescriptor(name, version) {
  return getVersionedDescriptor(formRegistry, name, version)
}

export function getCustomFormComponentOptions() {
  return getVersionedOptions(formRegistry)
    .map(({ component, ...descriptor }) => descriptor)
}
