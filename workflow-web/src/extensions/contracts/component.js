/**
 * UI 扩展的注册元数据。这里只描述现有协议，不执行注册。
 * @typedef {Object} ExtensionMetadata
 * @property {string} label 设计器显示名称。
 * @property {string} [description] 适用场景、限制及参数说明。
 * @property {number} [version=1] 正整数制品版本；整表单/整列表发布后变更实现应升版。
 * @property {number} [snapshotVersion=1] 配置结构版本，不等同于制品版本。
 * @property {ConfigProperty[]} [configSchema=[]] 参数编辑器定义；具体值由宿主传入。
 * @property {Object} [capabilities={}] 能力说明，不会自动为组件增加方法。
 * @property {string[]} [supportedModes=[]] create/edit/approve/view。
 * @property {string[]} [supportedFieldTypes=[]] 字段扩展的兼容实体字段类型。
 * @property {string[]} [supportedEntityCodes=[]] 空数组或 ['*'] 表示所有实体。
 * @property {string} [artifactDigest] 仅整表单/整列表注册支持，64 位十六进制 SHA-256。
 * @property {string[]} [nodeTypes=[]] 仅节点注册支持，如 ['FIELD']。
 * @property {string[]} [supportedBindings=[]] 仅节点注册支持，如 ['ENTITY_FIELD']。
 * @property {function({fromVersion: number, toVersion: number, config: Object}): Object} [migrateConfig]
 *   仅节点注册支持；同步返回升级后的配置，不修改入参、不发请求。
 *
 * @typedef {Object} ConfigProperty
 * @property {string} key 参数键，如 emptyText。
 * @property {string} label 参数名称。
 * @property {'text'|'number'|'boolean'|'select'|'json'} type 编辑控件类型。
 * @property {*} [defaultValue] 设计器默认值；手工使用组件时仍应自行兜底。
 * @property {string} [description] 参数用途。
 * @property {Array<{label: string, value: *}>} [options] select 候选值。
 * @property {number} [min] 数值下限。
 * @property {number} [max] 数值上限。
 * @property {'object'|'array'} [jsonShape] JSON 结构约束。
 * @property {*} [example] JSON 配置示例。
 * @property {string} [helpKey] 系统帮助项标识。
 */

/** 表单场景枚举；组件仍须结合 readonly/disabled 和字段权限决定可编辑性。 */
export const FORM_MODES = Object.freeze({
  CREATE: 'create', EDIT: 'edit', APPROVE: 'approve', VIEW: 'view'
})

/** 列表按钮位置，与宿主传入的 mode 一致。 */
export const LIST_BUTTON_MODES = Object.freeze({ TOOLBAR: 'toolbar', ROW: 'row' })
