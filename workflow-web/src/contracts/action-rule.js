/**
 * 操作条件编辑契约。registerEntityActionRuleCondition(definition) 注册。
 * 这只增加设计器中的条件配置 UI；条件判定需后端对应 Provider 支持同一个 type。
 * 当前前端没有 evaluate(context) 扩展入口，不能注册一个 JS 函数代替后端权限判定。
 *
 * @typedef {Object} ActionRuleCondition
 * @property {string} type 稳定标识，推荐大写命名空间，如 PROJECT:CUSTOM_CONDITION。
 * @property {string} label 条件类型的显示名称。
 * @property {Object|Function} component 实现 actionRuleConditionProps 的 Vue 组件。
 * @property {function(): Object} [createDefault] 返回全新条件参数对象；宿主补写 type。
 *
 * @typedef {Object} EntityPermissionOption
 * @property {string} code 必须与后端实际权限编码一致。
 * @property {string} label 设计器选项名称。
 * @property {string} [description] 权限含义。
 * @property {string} [category] 分组标识。
 *
 * @callback EntityPermissionOptionProvider
 * @param {{entityCode: string, type: string}} context type 为调用位置，如 form/toolbar/row。
 * @returns {EntityPermissionOption[]|Promise<EntityPermissionOption[]>}
 *   无适用项返回 []；异常向调用页传播。注册选项不会授予权限，也不会创建后端权限。
 *   registerEntityPermissionOptionProvider(provider) 追加 provider，初始化时只注册一次。
 */
export const actionRuleConditionProps = {
  modelValue: { type: Object, default: () => ({}) },
  // fields/statuses 是设计器选项，通常包含 value/label，不是记录数据。
  fields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] }
}

/** update:modelValue(nextCondition) 必须保留 type 和其他未编辑参数。 */
export const actionRuleConditionEmits = ['update:modelValue']
