import { restoreRelationEditorMetadata } from '@/shared/relation-content'
import { getFormFieldComponentDescriptor, hasFormFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'
import { normalizeFormNodeType } from '@flow/workflow-core/form-node-hierarchy'
import { buildFormNodePayload, extractFormNodeComponentConfig, formNodeSupports, getFormNodeDataSourceUsages, mergeFormNodeFieldMetadata, resolveFormNodeBinding } from '@flow/workflow-core/form-node-property-schema'
import { getDefaultFormFieldComponentType as getDefaultComponentType } from '@flow/workflow-core/extensions/core/fieldPolicy'
import { FORM_FIELD_EXTENSION_TYPE, resolveFormFieldExtensionName } from '@flow/workflow-core/form-field-extension'
import { safeParseConfig, stringifyConfig } from '@flow/workflow-core/config-runtime'
import { normalizeSubListDisplayConfig, SUB_LIST_ACTION_DISPLAY_VERSION } from '@/shared/sub-list'

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
    const fieldComponentName = resolveFormFieldExtensionName({
      ...node,
      props
    })
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
      componentName: fieldComponentName
        ? ''
        : (node.componentName || ''),
      componentVersion: fieldComponentName
        ? null
        : node.componentVersion,
      snapshotVersion: fieldComponentName
        ? null
        : node.snapshotVersion,
      fieldComponentName,
      fieldComponentVersion: fieldComponentName
        ? node.componentVersion
        : null,
      fieldComponentSnapshotVersion: fieldComponentName
        ? node.snapshotVersion
        : null,
      componentExtensionType: fieldComponentName
        ? FORM_FIELD_EXTENSION_TYPE
        : '',
      legacyProps: parseDocument(node.legacyPropsDocument),
      dataSourceBindings: bindings,
      fieldId: props.fieldId ?? sourceField.fieldId ?? sourceField.id,
      fieldCode: props.fieldCode || node.nodeKey,
      fieldName: props.fieldName || props.label || sourceField.fieldName || node.nodeKey,
      fieldLabel: props.label || sourceField.fieldLabel || sourceField.fieldName || node.nodeKey,
      fieldType: isChildFormNode
        ? 'SUB_FORM'
        : (props.fieldType || sourceField.fieldType || node.nodeType),
      componentType: isChildFormNode
        ? 'sub_form'
        : (
            fieldComponentName
            || props.componentType
            || sourceField.componentType
            || node.nodeType.toLowerCase()
          ),
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
      childFormReleaseId:
        props.childFormReleaseId
        || props.refFormReleaseId
        || props.publishedFormReleaseId
        || sourceField.childFormReleaseId
        || '',
      childFormReleaseVersion:
        props.childFormReleaseVersion
        ?? props.refFormReleaseVersion
        ?? props.publishedFormReleaseVersion
        ?? sourceField.childFormReleaseVersion
        ?? null,
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
    const selectedFieldComponent =
      field.fieldComponentName
      || (
        hasFormFieldComponent(field.componentType)
          ? field.componentType
          : ''
      )
    const fieldComponentDescriptor = selectedFieldComponent
      ? getFormFieldComponentDescriptor(selectedFieldComponent)
      : null
    const fieldComponentDefinition = selectedFieldComponent
      ? activeExtensionMap.value.get(
          `FIELD:${selectedFieldComponent}`
        )
      : null
    const persistedField = selectedFieldComponent
        ? {
          ...field,
          isRequired: effectiveRequired,
          componentType: getDefaultComponentType(field.fieldType),
          componentExtensionType: FORM_FIELD_EXTENSION_TYPE,
          componentName: selectedFieldComponent,
          componentVersion:
            field.fieldComponentVersion
            || fieldComponentDefinition?.version
            || fieldComponentDescriptor?.version
            || 1,
          snapshotVersion:
            field.fieldComponentSnapshotVersion
            || fieldComponentDefinition?.snapshotVersion
            || fieldComponentDescriptor?.snapshotVersion
            || 1
        }
      : {
          ...field,
          isRequired: effectiveRequired,
          componentExtensionType: undefined
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
        field.childFormReleaseId = field.childFormReleaseId
          || subFormConfig.childFormReleaseId
          || subFormConfig.refFormReleaseId
          || subFormConfig.publishedFormReleaseId
          || ''
        field.childFormReleaseVersion = field.childFormReleaseVersion
          ?? subFormConfig.childFormReleaseVersion
          ?? subFormConfig.refFormReleaseVersion
          ?? subFormConfig.publishedFormReleaseVersion
          ?? null
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
        const childFormReleaseId = field.childFormReleaseId || ''
        const childFormReleaseVersion = field.childFormReleaseVersion == null
          ? null
          : Number(field.childFormReleaseVersion)
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
          childFormReleaseId,
          refFormReleaseId: childFormReleaseId,
          publishedFormReleaseId: childFormReleaseId,
          childFormReleaseVersion,
          refFormReleaseVersion: childFormReleaseVersion,
          publishedFormReleaseVersion: childFormReleaseVersion,
          repeatable: field.relationType !== 'ONE_TO_ONE',
          relationType: field.relationType || 'ONE_TO_MANY',
          childRefFieldCode: field.childRefFieldCode || field.refFieldCode || ''
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

      // 序列化事件配置
      const events = {}
      Object.keys(field).forEach(key => {
        if (key.startsWith('eventOn') && field[key]) {
          const eventName = 'on' + key.slice(7)
          events[eventName] = field[key]
        }
      })
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
