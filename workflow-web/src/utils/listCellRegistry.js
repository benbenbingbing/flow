/**
 * 列表单元格渲染组件注册中心
 * 
 * 二次开发者可通过 registerCellComponent 注册自定义渲染组件。
 * 组件接收的 props：
 *   - value: 单元格值
 *   - row: 整行数据
 *   - field: 字段配置对象
 *   - config: 渲染配置（优先 renderConfig，兼容回退 dataSourceConfig）
 *   - context: 当前实体、列表与刷新方法等运行时上下文
 */

// 内置组件
import DefaultText from '@/components/list-cells/DefaultText.vue'
import StatusBadge from '@/components/list-cells/StatusBadge.vue'
import DateFormatter from '@/components/list-cells/DateFormatter.vue'
import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

const registry = new Map()

// 注册内置组件
registry.set('DefaultText', normalizeExtensionDescriptor('DefaultText', DefaultText, {
  label: '默认文本',
  description: '文本、数字和通用值的安全兜底显示。',
  configSchema: [
    { key: 'emptyText', label: '空值文本', type: 'text', defaultValue: '-' }
  ]
}))
registry.set('StatusBadge', normalizeExtensionDescriptor('StatusBadge', StatusBadge, {
  label: '状态标签',
  description: '使用标签展示状态，可配置标签尺寸和状态映射。',
  configSchema: [
    {
      key: 'size',
      label: '标签尺寸',
      type: 'select',
      defaultValue: 'small',
      options: [
        { label: '小', value: 'small' },
        { label: '默认', value: 'default' },
        { label: '大', value: 'large' }
      ]
    },
    { key: 'labelMap', label: '文本映射', type: 'json', description: '例如 {"DRAFT":"草稿"}' },
    { key: 'statusMap', label: '颜色映射', type: 'json', description: '例如 {"draft":"info","approved":"success"}' }
  ]
}))
registry.set('DateFormatter', normalizeExtensionDescriptor('DateFormatter', DateFormatter, {
  label: '日期格式',
  description: '按安全日期模板格式化，不执行表达式。',
  configSchema: [
    { key: 'pattern', label: '日期格式', type: 'text', defaultValue: 'yyyy-MM-dd HH:mm:ss' }
  ]
}))

/**
 * 注册自定义列表单元格组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCellComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  registry.set(descriptor.name, descriptor)
}

/**
 * 获取列表单元格组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCellComponent(name) {
  return registry.get(name)?.component
}

/**
 * 判断组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCellComponent(name) {
  return registry.has(name)
}

/**
 * 获取所有已注册的组件名称列表
 * @returns {string[]}
 */
export function getRegisteredCellNames() {
  return Array.from(registry.keys())
}

export function getCellDescriptor(name) {
  return registry.get(name)
}

export function getCellComponentOptions() {
  return Array.from(registry.values()).map(({ component, ...descriptor }) => descriptor)
}
