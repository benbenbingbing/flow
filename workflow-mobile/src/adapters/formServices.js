import { showConfirmDialog, showSuccessToast } from 'vant'
import { forms, entities, files, ui, request, compositions } from './services.js'
import { resolveEntityFileUploadContext } from '@flow/workflow-core/entity-file-upload-context'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { buildFieldEventPayload, applyRuntimeFieldEffects } from '@flow/workflow-core/form-runtime/fieldEvents'
import { createMemberChangeApi } from '@flow/workflow-api/memberChange'
import { createEntityListRuntimeApi } from '@flow/workflow-api/entityListRuntime'
import { assertRelatedContentResolveContract } from '@flow/workflow-core/related-content-runtime'
import { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'
import { normalizeEntityRecordForForm } from '@flow/workflow-core/form-runtime'
import { resolvePageParameters } from '@flow/workflow-core/page-parameters'

const lists = createEntityListRuntimeApi(request)

/** 所有选择器参数由发布字段配置推导；不把父实体身份用于子表查询。 */
function selectorContext(field) {
  const config = safeParseConfig(field.componentProps), ref = config.refConfig || {}
  const type = String(field.refEntityType || ref.refEntityType || ({ user: 'USER', dept: 'DEPT', role: 'ROLE', group: 'GROUP' })[String(field.componentType).toLowerCase()] || 'CUSTOM').toUpperCase()
  const refEntityId = field.refEntityId || ref.refEntityId, entityCode = field.refEntityCode || ref.entityCode
  if (type === 'CUSTOM' && !refEntityId && !entityCode) throw new Error('该字段尚未配置关联实体')
  return { type, refEntityId, entityCode }
}

/** 与 PC 表单保持一致：代码表树展开为平铺选项，停用项仍可回显，但不能新选。 */
function dictionaryOptions(items = []) {
  return items.flatMap(item => [
    { value: item.itemCode, label: item.itemLabel, disabled: item.status !== '0' },
    ...dictionaryOptions(item.children || [])
  ])
}
export const formServices = {
  ...createMemberChangeApi(entities.entityDataApi),
  getFormRuntimeRelease: forms.getFormRuntimeRelease, getEntityFields: forms.getEntityFields, precheckUnique: forms.precheckFormFieldUnique,
  async loadDictionaryOptions(dictCode) { return dictionaryOptions(await forms.getDictionaryItems(dictCode)) },
  async resolveEntityCode(id) { const options = await entities.entityApi.resolveOptions({ ids: [String(id)] }); return options.find(item => String(item.id) === String(id))?.entityCode || '' },
  async loadEntityOptions(field, query, runtimeContext = {}) {
    const { type, ...context } = selectorContext(field)
    const config = safeParseConfig(field.componentProps), listKey = field.refListKey || config.refConfig?.refListKey
    if (type === 'CUSTOM' && listKey) {
      const entityCode = context.entityCode || await formServices.resolveEntityCode(context.refEntityId)
      if (!entityCode) throw new Error('已发布选择列表缺少目标实体')
      // 配置的业务选择范围必须走 FORM_PICKER，绝不退回全量 entity-selector。
      return formServices.loadPublishedList({ entityCode, listKey, scene: 'FORM_PICKER', ...query, filters: query.keyword ? { keyword: query.keyword } : {}, context: runtimeContext.listContext || {} })
    }
    return forms.getEntityOptions(type, { ...query, ...context })
  },
  async resolveEntitySelection(field, values) {
    const { type, ...context } = selectorContext(field)
    return request.get(`/entity-selector/${type}/batch`, { params: { ...context, ids: values.join(',') } })
  },
  async loadPublishedList({ entityCode, listKey, scene, pageNum = 1, pageSize = 20, filters = {}, context = {}, ...release }) {
    const schema = await lists.getSchema(entityCode, listKey, scene, release)
    const result = await lists.query(entityCode, listKey, { ...release, releaseId: release.releaseId || schema.releaseId, releaseVersion: release.releaseVersion ?? schema.publishedVersion, scene, pageNum, pageSize, filters, context })
    const records = Array.isArray(result) ? result : result.records || result.list || result.rows || []
    return { records, total: result.total ?? records.length, columns: schema.columns || [] }
  },
  async resolveRelatedContent(input, parameters) {
    const resolved = assertRelatedContentResolveContract(await compositions.resolve(input))
    if (resolved.matchNone || resolved.targetContentType !== 'FORM') return { resolved, parameters }
    const release = await forms.getFormRuntimeRelease(resolved.targetContentId, resolved.targetReleaseId, resolved.targetReleaseVersion, resolved.targetReleaseResolutionToken)
    const form = normalizeRuntimeFormRelease(release, resolved.targetContentId, release.releaseResolutionToken || resolved.targetReleaseResolutionToken)
    const record = resolved.targetRecordId ? normalizeEntityRecordForForm(await entities.entityDataApi.getDetail(resolved.targetEntityCode, resolved.targetRecordId, null, resolved.targetContentId, {}, { releaseId: resolved.targetReleaseId, releaseVersion: resolved.targetReleaseVersion, releaseResolutionToken: form.releaseResolutionToken, viewCompositionTraversalToken: resolved.traversalContextToken })) : null
    return { resolved, form, record, parameters: resolvePageParameters(form.viewConfig, parameters) }
  },
  uploadFile(file, field, context, progress) {
    const uploadContext = resolveEntityFileUploadContext(context, field)
    const options = { silentError: true, onUploadProgress: event => progress?.(event.total ? Math.round(event.loaded * 100 / event.total) : 0) }
    return uploadContext ? files.uploadForEntity(file, uploadContext, options) : files.upload(file, options)
  },
  openFile(file) {
    const value = typeof file === 'string' ? file : file.url || file.fileUrl
    const url = new URL(value, location.origin)
    if (!['https:', 'http:', 'blob:'].includes(url.protocol)) throw new Error('附件地址无效')
    window.open(url.href, '_blank', 'noopener,noreferrer')
  },
  async fieldEvent(event, field, value, context, isCurrent) {
    const payload = buildFieldEventPayload(field, value, context, event === 'ENTITY_SELECTED' ? value : null)
    if (!payload) return
    if (context.eventSource) payload.input.source = context.eventSource
    const result = await ui.uiEventBindingApi.execute(event, payload)
    await applyRuntimeFieldEffects(result, {
      getRecord: context.getFormData, setField: context.setFormFieldValue, isCurrent,
      async confirmOverwrite() { try { await showConfirmDialog({ title: '确认回填', message: '将更新已有字段内容，是否继续？' }); return true } catch { return false } }
    })
    if (isCurrent?.() !== false && result?.message) showSuccessToast(result.message)
  }
}
