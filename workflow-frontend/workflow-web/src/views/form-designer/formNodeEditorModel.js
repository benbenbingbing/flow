import { restoreRelationEditorMetadata } from '@/shared/relation-content'
import { getFormFieldComponentDescriptor, hasFormFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'
import { normalizeFormNodeType } from '@flow/workflow-core/form-node-hierarchy'
import { buildFormNodePayload, extractFormNodeComponentConfig, formNodeSupports, getFormNodeDataSourceUsages, mergeFormNodeFieldMetadata, resolveFormNodeBinding } from '@flow/workflow-core/form-node-property-schema'
import { safeParseConfig, stringifyConfig } from '@flow/workflow-core/config-runtime'
import { normalizeSubListDisplayConfig, SUB_LIST_ACTION_DISPLAY_VERSION } from '@/shared/sub-list'
import { readFieldScripts } from '@flow/workflow-core/browser/field-event-scripts'

/**
 * 统一组件目录按宿主节点、绑定与实体类型筛选；平台预置和项目组件遵守相同约束。
 * 未声明 nodeTypes 的字段控件只适用于 FIELD，避免误用于子表单的整行数据。
 */
export function compatibleFormComponentOptions(field, options) {
  const nodeType = normalizeFormNodeType(field?.nodeType || 'FIELD')
  const { bindingType } = resolveFormNodeBinding(field, nodeType)
  const fieldType = String(field?.fieldType || '').toUpperCase()
  return options.filter(option => {
    const nodeTypes = option.nodeTypes?.length ? option.nodeTypes : ['FIELD']
    return nodeTypes.map(type => String(type).toUpperCase()).includes(nodeType)
      && (!option.supportedBindings?.length || option.supportedBindings
        .map(type => String(type).toUpperCase()).includes(bindingType))
      && (!option.supportedFieldTypes?.length || option.supportedFieldTypes
        .map(type => String(type).toUpperCase()).includes(fieldType))
  })
}

/**
 * 设计节点与发布协议的双向转换。只读取实体元数据和扩展目录，不负责请求、保存或修改基线。
 * 两个只读引用由设计会话持有，确保目录刷新后序列化仍读取最新字段约束和扩展版本。
 */
export function createFormNodeEditorModel({ entityFields, activeExtensionMap }) {
  function parseDocument(value) {
    return safeParseConfig(value)
  }

  function isSubFormField(field) {
    const nodeType = String(field?.nodeType || '').toUpperCase()
    const componentType = String(
      field?.componentType || field?.fieldType || ''
    ).toUpperCase()
    return ['SUB_FORM', 'REPEATER'].includes(nodeType)
      || componentType === 'SUB_FORM'
  }

  function isSubListField(field) {
    const fieldType = String(field?.fieldType || '').toUpperCase()
    const componentType = String(field?.componentType || '').toLowerCase()
    return fieldType === 'SUB_LIST'
      || componentType === 'sub_list'
  }

  function legacyNodeType(field) {
    const fieldType = String(field?.fieldType || '').toUpperCase()
    const componentType = String(field?.componentType || '').toUpperCase()
    if (fieldType === 'SECTION' || componentType === 'SECTION') return 'SECTION'
    if (fieldType === 'SUB_FORM' || componentType === 'SUB_FORM') return 'SUB_FORM'
    return 'FIELD'
  }

  /** 将服务端节点恢复为设计草稿，补齐实体元数据但不覆盖节点已有的显式配置。 */
  function nodeToField(node, fieldMetadata) {
    const props = parseDocument(node.propsDocument)
    const rules = parseDocument(node.rulesDocument)
    const bindings = parseDocument(node.dataSourceBindingsDocument)
    const nodeType = normalizeFormNodeType(node.nodeType)
    const sourceField = mergeFormNodeFieldMetadata(
      entityFields.value,
      fieldMetadata,
      props,
      node.nodeKey
    )
    const allowedDataSourceUsages = getFormNodeDataSourceUsages(nodeType)
    const firstBinding = Object.entries(bindings)
      .find(([usage]) =>
        allowedDataSourceUsages.includes(String(usage).toUpperCase())
      ) || []
    const componentConfig = extractFormNodeComponentConfig(nodeType, props)
    const rulesSupported = formNodeSupports(nodeType, 'rules')
    const isChildFormNode = ['SUB_FORM', 'REPEATER'].includes(nodeType)
    const field = {
      ...sourceField,
      id: node.id,
      nodeId: node.id,
      formId: node.formId,
      parentId: node.parentId || '',
      nodeType,
      nodeKey: node.nodeKey,
      bindingType: node.bindingType || 'NONE',
      bindingRef: node.bindingRef || '',
      revision: node.revision,
      orderKey: node.orderKey,
      componentVersion: node.componentVersion,
      snapshotVersion: node.snapshotVersion,
      legacyProps: parseDocument(node.legacyPropsDocument),
      dataSourceBindings: bindings,
      fieldId: props.fieldId ?? sourceField.fieldId ?? sourceField.id,
      fieldCode: props.fieldCode || node.nodeKey,
      fieldName: props.fieldName || props.label || sourceField.fieldName || node.nodeKey,
      fieldLabel: props.label || sourceField.fieldLabel || sourceField.fieldName || node.nodeKey,
      fieldType: isChildFormNode
        ? 'SUB_FORM'
        : (props.fieldType || sourceField.fieldType || node.nodeType),
      componentType: props.componentType
        || (isChildFormNode ? 'sub_form' : sourceField.componentType)
        || node.nodeType.toLowerCase(),
      placeholder: props.placeholder ?? sourceField.placeholder,
      defaultValue: props.defaultValue ?? sourceField.defaultValue,
      // 旧发布曾使用 props.span；读取后统一投影成 gridSpan，下一次保存自动规范化。
      gridSpan: props.gridSpan ?? props.span ?? sourceField.gridSpan ?? 24,
      childFormId:
        props.childFormId
        || props.refFormId
        || props.publishedFormId
        || sourceField.childFormId
        || sourceField.refFormId
        || '',
      // 子表单节点的发布协议只保存 componentProps；关系必填性需从关系配置回读到设计态。
      isRequired: isChildFormNode && node.bindingType === 'RELATION'
        && componentConfig.subFormConfig?.relationRequired === true
        ? 1
        : (Object.hasOwn(props, 'required')
            ? (props.required === true ? 1 : 0)
            : (sourceField.isRequired || 0)),
      isReadonly: Object.hasOwn(props, 'readonly')
        ? (props.readonly === true ? 1 : 0)
        : (sourceField.isReadonly || 0),
      isHidden: Object.hasOwn(props, 'hidden')
        ? (props.hidden === true ? 1 : 0)
        : (sourceField.isHidden || 0),
      componentProps: stringifyConfig(componentConfig),
      validationRules: rulesSupported
        ? stringifyConfig(rules.validation || rules)
        : '',
      extensionConfig: rulesSupported
        ? stringifyConfig(rules.extension || {})
        : '',
      dataSourceUsage: firstBinding[0] || allowedDataSourceUsages[0] || '',
      interfaceExtensionId: firstBinding[1]?.extensionId || '',
      dataSourceInputMappingText: stringifyConfig(
        firstBinding[1]?.inputMapping || {}
      ),
      dataSourceOutputMappingText: stringifyConfig(
        firstBinding[1]?.outputMapping || {}
      )
    }
    const normalizedBinding = resolveFormNodeBinding(field, nodeType)
    field.bindingType = normalizedBinding.bindingType
    field.bindingRef = normalizedBinding.bindingRef || ''
    restoreFieldConfig(field)
    return field
  }

  /** 序列化草稿而不修改原对象，保留发布引用、节点标识和字段约束。 */
  function fieldToNodePayload(field, options = {}) {
    const effectiveRequired = isEntityFieldFixedRequired(field)
      ? 1
      : field.isRequired
    const isCustomComponent = hasFormFieldComponent(field.componentType)
    const descriptor = getFormFieldComponentDescriptor(field.componentType, field.componentVersion)
    const definition = activeExtensionMap.value.get(`FIELD:${field.componentType}`)
    // 保存唯一组件标识，读取后无需再将“节点扩展”翻译成设计器的第二套字段。
    const persistedField = {
      ...field,
      isRequired: effectiveRequired,
      componentVersion: isCustomComponent
        ? (field.componentVersion || definition?.version || descriptor?.version || 1)
        : null,
      snapshotVersion: isCustomComponent
        ? (field.snapshotVersion || definition?.snapshotVersion || descriptor?.snapshotVersion || 1)
        : null
    }
    return buildFormNodePayload(
      {
        ...persistedField,
        nodeType: field.nodeType || legacyNodeType(field)
      },
      {
        componentProps: buildSerializedFieldComponentProps(field),
        forPatch: options.forPatch === true
      }
    )
  }

  // 从 componentProps 恢复子表单和事件配置
  function restoreFieldConfig(field) {
    if (!field.componentProps) return
    try {
      const compProps = typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : field.componentProps

      if ((!Array.isArray(field.fileItems) || field.fileItems.length === 0)
          && Array.isArray(compProps.fileItems)) {
        field.fileItems = cloneAttachmentItems(compProps.fileItems)
      }

      // 恢复子表单配置
      if (compProps.subFormConfig) {
        const subFormConfig = compProps.subFormConfig
        restoreRelationEditorMetadata(field, subFormConfig)
        field.layout = subFormConfig.layout || 'form'
        field.refEntityId = subFormConfig.refEntityId || field.childEntityId || field.refEntityId || ''
        field.childFormId = field.childFormId
          || subFormConfig.childFormId
          || subFormConfig.refFormId
          || subFormConfig.publishedFormId
          || ''
        field.refFormId = field.childFormId
        field.repeatable = field.relationType !== 'ONE_TO_ONE'
        field.childEntityId = field.childEntityId || field.refEntityId || ''
        field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
      }
      if (compProps.subListConfig) {
        const subListConfig = normalizeSubListDisplayConfig(
          compProps.subListConfig
        )
        field.refEntityId =
          subListConfig.targetEntityId
          || field.refEntityId
          || ''
        field.refEntityCode =
          subListConfig.targetEntityCode
          || field.refEntityCode
          || ''
        field.refListKey =
          subListConfig.listKey
          || field.refListKey
          || ''
        field.refListId = subListConfig.listId || ''
        field.refListReleaseId = subListConfig.listReleaseId || ''
        field.refListReleaseVersion =
          subListConfig.listReleaseVersion ?? null
        field.subListShowSearch = subListConfig.showSearch
        field.subListShowPagination = subListConfig.showPagination
        field.subListShowToolbar = subListConfig.showToolbar
        field.subListShowRowActions = subListConfig.showRowActions
        field.subListPageSize = subListConfig.pageSize
        field.subListMaxHeight =
          Number(subListConfig.maxHeight) >= 120
            ? Number(subListConfig.maxHeight)
            : 420
      }
      // 恢复实体引用配置
      if (compProps.refConfig) {
        field.refEntityType = compProps.refConfig.refEntityType || ''
        field.refEntityId = String(compProps.refConfig.refEntityId || '')
        field.refEntityCode = compProps.refConfig.entityCode || ''
        field.refListKey = compProps.refConfig.listKey || ''
      }

      // 恢复事件配置
      if (compProps.events) {
        Object.keys(compProps.events).forEach(key => {
          const rootKey = 'eventOn' + key.charAt(2).toUpperCase() + key.slice(3)
          field[rootKey] = compProps.events[key] || ''
        })
      }
    } catch (e) {
      // 忽略解析错误
    }
  }

  // 将子表单和事件配置纯函数序列化到 componentProps
  function buildSerializedFieldComponentProps(field) {
    try {
      const compProps = field.componentProps
        ? (typeof field.componentProps === 'string'
          ? JSON.parse(field.componentProps)
          : JSON.parse(JSON.stringify(field.componentProps)))
        : {}

      const attachmentItems = attachmentItemsForField(field)
      if (isAttachmentField(field) && attachmentItems.length > 0) {
        compProps.fileItems = cloneAttachmentItems(attachmentItems)
      } else {
        delete compProps.fileItems
        delete compProps.attachmentItemRequiredRules
      }

      // 序列化子表单配置
      if (isSubFormField(field)) {
        const childFormId = field.childFormId || field.refFormId || ''
        const subFormConfig = {
          ...(compProps.subFormConfig || {})
        }
        compProps.subFormConfig = {
          ...subFormConfig,
          layout: field.layout || 'form',
          refEntityId: field.childEntityId || field.refEntityId || '',
          childFormId,
          refFormId: childFormId,
          publishedFormId: childFormId,
          repeatable: field.relationType !== 'ONE_TO_ONE',
          relationType: field.relationType || 'ONE_TO_MANY',
          childRefFieldCode: field.childRefFieldCode || field.refFieldCode || ''
        }
        // 发布引用只保存稳定表单 ID；旧节点再次保存时清除所有版本别名。
        for (const key of ['childFormReleaseId', 'refFormReleaseId', 'publishedFormReleaseId',
          'childFormReleaseVersion', 'refFormReleaseVersion', 'publishedFormReleaseVersion']) {
          delete compProps[key]
          delete compProps.subFormConfig[key]
        }
        delete compProps.fields
        delete compProps.subFields
      }
      if (isSubListField(field)) {
        compProps.subListConfig = {
          ...(compProps.subListConfig || {}),
          targetEntityId: field.refEntityId || '',
          targetEntityCode: field.refEntityCode || '',
          listId: field.refListId || '',
          listKey: field.refListKey || '',
          listReleaseId: field.refListReleaseId || '',
          listReleaseVersion: field.refListReleaseVersion == null
            ? null
            : Number(field.refListReleaseVersion),
          actionDisplayVersion: SUB_LIST_ACTION_DISPLAY_VERSION,
          showSearch: field.subListShowSearch !== false,
          showPagination: field.subListShowPagination !== false,
          showToolbar: field.subListShowToolbar !== false,
          showRowActions: field.subListShowRowActions !== false,
          pageSize: Number(field.subListPageSize) || 10,
          maxHeight: Number(field.subListMaxHeight) || 420
        }
        delete compProps.subFormConfig
      }
      // 序列化实体引用配置
      if ((field.componentType || '').toUpperCase() === 'REFERENCE' || (field.componentType || '').toUpperCase() === 'MULTI_REFERENCE') {
        compProps.refConfig = {
          refEntityType: field.refEntityType || '',
          refEntityId: field.refEntityId || '',
          entityCode: field.refEntityCode || '',
          listKey: field.refListKey || ''
        }
      }

      // 根草稿覆盖持久化副本（含显式清空），同时保留处理函数引用和未展开过的旧节点事件。
      const events = readFieldScripts(field)
      if (Object.keys(events).length > 0) {
        compProps.events = events
      } else {
        delete compProps.events
      }

      // 序列化选项配置（optionsJson → componentProps.options）
      if (field.optionsJson) {
        try {
          const options = JSON.parse(field.optionsJson)
          if (Array.isArray(options) && options.length > 0) {
            compProps.options = options
          }
        } catch (e) {}
      }

      return compProps
    } catch (e) {
      console.error('序列化字段配置失败:', e)
      return parseDocument(field.componentProps)
    }
  }

  // 解析 componentProps
  function parseComponentProps(propsStr) {
    if (!propsStr) return {}
    try {
      return JSON.parse(propsStr)
    } catch (e) {
      return {}
    }
  }

  function isAttachmentField(field) {
    return ['FILE', 'IMAGE'].includes(String(
      field?.fieldType || field?.componentType || ''
    ).toUpperCase())
  }

  function entityFieldForFormField(field) {
    if (!field) return null
    return entityFields.value.find(item =>
      (field.fieldId != null && String(item.id) === String(field.fieldId))
        || (field.fieldCode && item.fieldCode === field.fieldCode)
        || (field.bindingRef && item.fieldCode === field.bindingRef)
    ) || null
  }

  function isEntityFieldFixedRequired(field) {
    const entityField = entityFieldForFormField(field)
    return entityField?.isRequired === true || entityField?.isRequired === 1
  }

  function attachmentItemsForField(field) {
    if (!field || !isAttachmentField(field)) return []
    const entityField = entityFieldForFormField(field)
    const componentItems = parseComponentProps(field.componentProps).fileItems
    return [entityField?.fileItems, field.fileItems, componentItems]
      .find(items => Array.isArray(items) && items.length > 0) || []
  }

  function cloneAttachmentItems(items) {
    return (Array.isArray(items) ? items : []).map((item, index) => ({
      itemKey: item.itemKey,
      itemName: item.itemName || `附件项${index + 1}`,
      nameAliases: Array.isArray(item.nameAliases)
        ? [...item.nameAliases]
        : safeParseConfig(item.nameAliases, []),
      required: item.required === true
        || item.required === 1
        || item.required === '1',
      fileTypes: Array.isArray(item.fileTypes)
        ? [...item.fileTypes]
        : item.fileTypes,
      maxSize: item.maxSize,
      maxCount: item.maxCount,
      sortOrder: item.sortOrder ?? index
    }))
  }

  return {
    parseDocument,
    isSubFormField,
    isSubListField,
    legacyNodeType,
    nodeToField,
    fieldToNodePayload,
    restoreFieldConfig,
    entityFieldForFormField,
    isEntityFieldFixedRequired,
    attachmentItemsForField,
    cloneAttachmentItems
  }
}
