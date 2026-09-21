import AsyncValidator from 'async-validator'
import { getFieldKey, buildRuntimeFieldRules } from './index.js'
import { normalizeRuntimeNodes, resolveRuntimeNodeField } from './nodeProjection.js'
import { resolveCrossFieldRuntimeState } from '../form-cross-field-runtime.js'
import LinkageEngine from '../../utils/linkageEngine.js'

const Schema = AsyncValidator.default || AsyncValidator
const flag = value => value === true || value === 1 || value === '1'

/** 补齐实体字段信息，仍以发布表单规则为准；根表单不混入子表字段。 */
export function projectRuntimeFields(form = {}, entityFields = [], { rootParentId = '', hasComponent = () => false } = {}) {
  const fields = (form.fields || []).map(field => {
    const metadata = entityFields.find(item => (field.fieldCode && item.fieldCode === field.fieldCode) || ((field.fieldId || field.id) != null && String(item.id) === String(field.fieldId || field.id))) || {}
    return { ...metadata, ...field, isRequired: flag(metadata.isRequired) || flag(field.isRequired), fileItems: field.fileItems?.length ? field.fileItems : metadata.fileItems || [], entityFileItems: metadata.fileItems || [] }
  }).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
  const nodes = normalizeRuntimeNodes(form.nodes || [])
  if (!nodes.length) return { fields, nodes }
  const descendants = id => {
    const found = [], seen = new Set([String(id)])
    const visit = parent => {
      for (const node of nodes.filter(item => String(item.parentId || '') === String(parent))) {
        if (seen.has(String(node.id))) throw new Error('表单节点存在循环引用')
        seen.add(String(node.id)); found.push(node); visit(node.id)
      }
    }
    visit(id); return found
  }
  const projected = []
  const seen = new Set()
  const visit = parent => {
    for (const node of nodes.filter(item => String(item.parentId || '') === String(parent))) {
      if (seen.has(String(node.id))) throw new Error('表单节点存在循环引用')
      seen.add(String(node.id))
      const field = resolveRuntimeNodeField(node, fields, descendants(node.id), hasComponent)
      if (field) projected.push(field)
      // 子表有独立记录、发布上下文和校验作用域，不能当成父表扁平字段。
      if (!['SUB_FORM', 'REPEATER'].includes(node.nodeType)) visit(node.id)
    }
  }
  visit(rootParentId)
  return { fields: projected, nodes }
}

/** 计算规则到稳定状态；循环联动明确失败，避免持续触发 UI watcher。 */
export function settleRuntimeLinkages(fields, record) {
  const result = { ...record }
  for (let round = 0; round < 32; round++) {
    const linkage = LinkageEngine.processAllLinkages(fields, result)
    let changed = false
    for (const [key, value] of Object.entries(linkage.values || {})) {
      if (value == null || JSON.stringify(result[key]) === JSON.stringify(value)) continue
      result[key] = value; changed = true
    }
    if (!changed) return { record: result, linkage }
  }
  throw new Error('表单联动未能收敛，请联系管理员检查循环计算规则')
}

/** 折叠只影响布局，不改变字段的业务可见性、可编辑性及必填状态。 */
export function runtimeFieldState(field, options, linkage = {}) {
  const key = getFieldKey(field)
  const state = resolveCrossFieldRuntimeState(field, options)
  return {
    ...state,
    visible: state.visible && linkage.visibility?.[key] !== false,
    editable: state.editable && linkage.disabled?.[key] !== true,
    required: linkage.required?.[key] ?? flag(field.isRequired),
    attachmentItemRequiredState: linkage.attachmentItemRequired?.[key] || {}
  }
}

/** 使用 PC 相同的规则构造器及 async-validator，对未挂载字段同样执行校验。 */
export async function validateRuntimeFields(fields, record, options, linkage = {}) {
  const errors = {}
  await Promise.all(fields.map(async field => {
    const key = getFieldKey(field), state = runtimeFieldState(field, options, linkage)
    if (!key || !state.visible || !state.editable) return
    const rules = buildRuntimeFieldRules(field, state.required, field.fieldLabel || field.fieldName, state.attachmentItemRequiredState)
    if (!rules.length) return
    try { await new Schema({ [key]: rules }).validate(record, { firstFields: true }) }
    catch (error) { errors[key] = error.errors?.[0]?.message || error.message || '字段校验失败' }
  }))
  // 异步任务的完成顺序不应改变“第一个错误”的定位顺序。
  return Object.fromEntries(fields.map(field => getFieldKey(field)).filter(key => errors[key]).map(key => [key, errors[key]]))
}
