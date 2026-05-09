/**
 * 列表单元格渲染组件注册中心
 * 
 * 二次开发者可通过 registerCellComponent 注册自定义渲染组件。
 * 组件接收的 props：
 *   - value: 单元格值
 *   - row: 整行数据
 *   - field: 字段配置对象
 *   - config: 数据源配置（dataSourceConfig 解析后的对象）
 */

// 内置组件
import DefaultText from '@/components/list-cells/DefaultText.vue'
import StatusBadge from '@/components/list-cells/StatusBadge.vue'
import DateFormatter from '@/components/list-cells/DateFormatter.vue'

const registry = new Map()

// 注册内置组件
registry.set('DefaultText', DefaultText)
registry.set('StatusBadge', StatusBadge)
registry.set('DateFormatter', DateFormatter)

/**
 * 注册自定义列表单元格组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCellComponent(name, component) {
  registry.set(name, component)
}

/**
 * 获取列表单元格组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCellComponent(name) {
  return registry.get(name)
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
