import { computed, ref, watch } from 'vue'
import { entityApi } from '@/api/entity'
import { getEntityFields } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { normalizeRows } from '@/components/related-content/relatedContentOptions.js'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { listFilterParameterSchema, validateReferenceListParameterMappings } from '@flow/workflow-core/page-parameters'
import { isEntityListPicker, pickerListSchema } from '@flow/workflow-core/data-picker'

/** 配置自定义实体的选择列表和参数映射，列表契约始终取自当前发布版本。 */
export function useReferenceListSettings({ selectedField, fields, entityFields, nodeById, nodeTypeOf, rememberEntityOption }) {
  const lists = ref([])
  const parameterSchema = ref({})
  const parameterError = ref('')
  const parameterLoading = ref(false)
  const childEntityFields = ref([])
  let listLoadSequence = 0

  const isReference = computed(() => isReferenceComponent(selectedField.value))
  const canMapParameters = computed(() => supportsParameterMappings(selectedField.value))
  const sourceFields = computed(() => sourceFieldsFor(selectedField.value))
  const sourceScope = computed(() => dataScope(selectedField.value))
  const childEntityId = computed(() => (canMapParameters.value && selectedField.value.refListKey || entityPickerConfig(selectedField.value)?.listKey)
    ? entityIdForScope(sourceScope.value)
    : '')
  const sourceEntityFields = computed(() => sourceScope.value
    ? childEntityFields.value
    : usableEntityFields(entityFields.value))
  const selectedList = computed(() => canMapParameters.value
    ? lists.value.find(list => list.listKey === selectedField.value.refListKey)
    : null)

  watch(selectedList, async (list, _, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    parameterSchema.value = {}
    parameterError.value = ''
    parameterLoading.value = Boolean(list)
    if (!list) return
    try {
      const schema = await loadParameterSchema(list)
      if (current) parameterSchema.value = schema
    } catch (error) {
      if (current) parameterError.value = error.message || '读取选择列表的已发布参数失败'
    } finally {
      if (current) parameterLoading.value = false
    }
  })

  watch(childEntityId, async (id, _, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    childEntityFields.value = []
    if (!id) return
    try {
      const fields = await loadEntitySourceFields(id)
      if (current) childEntityFields.value = fields
    } catch (error) {
      if (current) console.error('加载来源实体属性失败:', error)
    }
  })

  function changeList(listKey) {
    const field = selectedField.value
    if (!isReference.value || (field.refListKey || '') === (listKey || '')) return
    field.refListKey = listKey || ''
    // 参数编码属于具体列表，换列表时不能沿用旧映射。
    field.refListParameterMappings = []
  }

  async function changeEntity(entity) {
    rememberEntityOption(entity)
    if (!isReference.value) return
    const field = selectedField.value
    field.refEntityCode = entity?.entityCode || ''
    changeList('')
    await loadLists(field)
  }

  /** 切换字段或目标实体后，旧请求不能填充当前字段的列表选项。 */
  async function loadLists(field) {
    const sequence = ++listLoadSequence
    const targetEntityId = field.refEntityId
    lists.value = []
    if (!targetEntityId) return
    try {
      const [response, options] = await Promise.all([
        entityListConfigApi.getByEntityId(targetEntityId),
        field.refEntityCode ? [] : entityApi.resolveOptions({ ids: [String(targetEntityId)] }).catch(() => [])
      ])
      if (sequence !== listLoadSequence || selectedField.value !== field || field.refEntityId !== targetEntityId) return
      const entity = options?.[0]
      if (entity && !field.refEntityCode) {
        rememberEntityOption(entity)
        field.refEntityCode = entity.entityCode || ''
      }
      lists.value = normalizeRows(response)
    } catch (error) {
      console.error('加载实体引用列表失败:', error)
    }
  }

  /** 布局容器共享表单数据，子表单和重复器各自使用独立的数据作用域。 */
  function dataScope(node) {
    let parent = nodeById(node?.parentId)
    const visited = new Set()
    while (parent && !visited.has(parent.id)) {
      if (['SUB_FORM', 'REPEATER'].includes(nodeTypeOf(parent))) return parent
      visited.add(parent.id)
      parent = nodeById(parent.parentId)
    }
    return null
  }

  function sourceFieldsFor(field) {
    if (!field) return []
    const scope = dataScope(field)
    return fields.value.filter(candidate => candidate.id !== field.id
      && nodeTypeOf(candidate) === 'FIELD' && candidate.fieldCode
      && candidate.fieldCode !== field.fieldCode && dataScope(candidate) === scope)
  }

  /** 保存前复核来源字段和目标发布版本，防止沿用失效参数或保存校验期间改动的映射。 */
  async function validateMappings(field) {
    if (String(field.componentType || '').toUpperCase() === 'DATA_PICKER') {
      const inputError = Object.values(field.pickerInputErrors || {})[0]
      if (inputError) throw new Error(inputError)
    }
    const picker = entityPickerConfig(field)
    const mappings = picker ? picker.parameterMappings || [] : field.refListParameterMappings
    const refListKey = picker ? picker.listKey : field.refListKey
    const refEntityId = picker ? picker.entityId : field.refEntityId
    if (!picker && (!supportsParameterMappings(field) || mappings == null || Array.isArray(mappings) && !mappings.length)) return
    if (!Array.isArray(mappings)) throw new Error('列表参数映射必须为数组')
    const scope = dataScope(field)
    const target = normalizeRows(await entityListConfigApi.getByEntityId(refEntityId))
      .find(list => list.listKey === refListKey)
    if (!target) throw new Error('列表参数映射需要有效的选择列表')
    const scopedEntityId = entityIdForScope(scope)
    const [published, availableEntityFields, targetFields] = await Promise.all([
      loadPublishedListSettings(target),
      scope && mappings.some(mapping => mapping?.sourceType === 'ENTITY_FIELD')
        ? loadEntitySourceFields(scopedEntityId)
        : usableEntityFields(entityFields.value),
      picker ? loadEntitySourceFields(refEntityId) : []
    ])
    const currentPicker = picker && entityPickerConfig(field)
    const unchanged = picker
      ? JSON.stringify(currentPicker) === JSON.stringify(picker)
      : field.refListParameterMappings === mappings && field.refListKey === refListKey && field.refEntityId === refEntityId
    if (!unchanged
        || dataScope(field) !== scope || entityIdForScope(scope) !== scopedEntityId) {
      throw new Error('列表参数映射已修改，请重新保存')
    }
    const error = validateReferenceListParameterMappings(mappings, listFilterParameterSchema(published.viewConfig), sourceFieldsFor(field), availableEntityFields)
    if (error) throw new Error(`${field.fieldLabel || field.fieldCode}的列表参数映射：${error}`)
    if (picker) {
      const config = safeParseConfig(field.componentProps)
      field.componentProps = JSON.stringify({ ...config,
        pickerList: { ...picker, entityCode: published.entityCode },
        pickerSchema: pickerListSchema(published.fields, targetFields)
      })
    } else {
      field.refEntityCode = target.entityCode || field.refEntityCode || ''
    }
  }

  return { lists, isReference, canMapParameters, sourceFields, sourceEntityFields, parameterSchema, parameterError, parameterLoading,
    changeList, changeEntity, loadLists, validateMappings }
}

const entityIdForScope = scope => scope?.childEntityId || scope?.refEntityId || ''
const usableEntityFields = fields => fields.filter(field => field.fieldCode && field.uiConfigurable !== false)

async function loadEntitySourceFields(entityId) {
  // 缺少子表实体时不使用父表字段，避免保存时将两个记录的同名属性混为一谈。
  return entityId ? usableEntityFields(normalizeRows(await getEntityFields(entityId))) : []
}

const isReferenceComponent = field => ['REFERENCE', 'MULTI_REFERENCE'].includes(String(field?.componentType || '').toUpperCase())
const supportsParameterMappings = field => isReferenceComponent(field)
  && String(field.refEntityType || '').toUpperCase() === 'CUSTOM'
function entityPickerConfig(field) {
  const config = safeParseConfig(field?.componentProps)
  return String(field?.componentType || '').toUpperCase() === 'DATA_PICKER' && isEntityListPicker(config)
    ? config.pickerList || {} : null
}

/** 使用启用版本的隐藏筛选声明，列表草稿的改动需要发布后才可映射。 */
async function loadParameterSchema(list) {
  return listFilterParameterSchema((await loadPublishedListSettings(list)).viewConfig)
}

/** 实体引用和数据选择器均基于激活快照，列表草稿里的列和参数不能参与配置。 */
export async function loadPublishedListSettings(list) {
  const releases = await entityListConfigApi.getReleases(list.id)
  const release = releases.find(item => item.status === 'ACTIVE')
  const snapshot = safeParseConfig(release?.snapshotDocument).list
  if (!snapshot) throw new Error('请选择一个已发布的选择列表')
  return snapshot
}
