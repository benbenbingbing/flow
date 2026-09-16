/**
 * 全部现有注册入口的示例清单，仅供复制和按需调用；导入本文件不会安装示例。
 * 本次没有在 project/index.js 或应用入口调用 registerContractExamples。
 * 示例条件与权限编码需相应后端实现后才能生效，前端注册本身不赋权。
 */
import {
  registerCustomFormComponent, registerCustomListComponent, registerFormFieldComponent,
  registerFormNodeComponent, registerCellComponent, registerListButtonComponent,
  registerListToolbarAction, registerListRowAction, registerEntityActionRuleCondition,
  registerEntityPermissionOptionProvider
} from '../registration.js'
import CustomFormTemplate from '../templates/CustomFormTemplate.vue'
import CustomFieldTemplate from '../templates/CustomFieldTemplate.vue'
import CustomNodeTemplate from '../templates/CustomNodeTemplate.vue'
import CustomListTemplate from '../templates/CustomListTemplate.vue'
import CustomCellTemplate from '../templates/CustomCellTemplate.vue'
import CustomButtonTemplate from '../templates/CustomButtonTemplate.vue'
import CustomConditionTemplate from '../templates/CustomConditionTemplate.vue'
import RelatedContentTemplate from '../templates/RelatedContentTemplate.vue'

/**
 * JS 动作示例：工具栏、行按钮和 selectionHandler 都可用同一函数。
 * @param {import('../list-action.js').ListActionContext} context 宿主上下文。
 * @returns {Promise<void>} 当前宿主不等待返回，故内部捕获异常。
 */
export async function refreshAction(context) {
  try {
    await context.refresh()
  } catch (error) {
    // 接入业务页面时可改用项目统一的消息提示，不能抛出无人处理的 Promise rejection。
    console.error('列表刷新失败', error)
  }
}

/**
 * 注册所有示例，供明确接入时参考；必须在项目初始化阶段由调用方保证仅调用一次。
 * 生产项目只注册选中的业务实现，按 name 配置并发布后，平台页面才会装载。
 */
export function registerContractExamples() {
  registerCustomFormComponent('ContractExampleForm', CustomFormTemplate, {
    label: '契约示例·整表单', version: 1, supportedModes: ['create', 'edit', 'approve', 'view'],
    capabilities: { exposesValidate: true },
    configSchema: [{ key: 'title', label: '标题', type: 'text' }]
  })
  registerCustomListComponent('ContractExampleList', CustomListTemplate, {
    label: '契约示例·整列表', version: 1
  })
  registerFormFieldComponent('contract_example_text', CustomFieldTemplate, {
    label: '契约示例·文本字段', supportedFieldTypes: ['STRING', 'TEXT'],
    configSchema: [{ key: 'suffix', label: '后缀', type: 'text' }]
  })
  registerFormNodeComponent('ContractExampleNode', CustomNodeTemplate, {
    label: '契约示例·摘要节点', version: 1, snapshotVersion: 1,
    nodeTypes: ['FIELD'], supportedBindings: ['ENTITY_FIELD'],
    configSchema: [{ key: 'fieldCodes', label: '摘要字段', type: 'json', jsonShape: 'array', example: ['name'] }]
  })
  registerCellComponent('ContractExampleCell', CustomCellTemplate, {
    label: '契约示例·单元格',
    configSchema: [{ key: 'suffix', label: '后缀', type: 'text' }]
  })
  registerListButtonComponent('ContractExampleButton', CustomButtonTemplate)
  registerListToolbarAction('contractExampleRefresh', refreshAction)
  registerListRowAction('contractExampleRefresh', refreshAction)
  registerEntityActionRuleCondition({
    type: 'CONTRACT_EXAMPLE:FIELD_EQUALS', label: '契约示例·字段条件',
    component: CustomConditionTemplate,
    createDefault: () => ({ field: '', operator: 'EQ', value: '' })
  })
  registerEntityPermissionOptionProvider(({ entityCode }) => entityCode ? [{
    code: `entity:${String(entityCode).toLowerCase()}:custom:contract-example`,
    label: '契约示例权限', description: '需要对应后端权限实现', category: 'CUSTOM'
  }] : [])
  // 关联目标与普通页面的 props 不同，使用独立注册名，避免误用普通整列表模板。
  registerCustomFormComponent('ContractExampleRelatedForm', RelatedContentTemplate, {
    label: '契约示例·关联表单', version: 1
  })
  registerCustomListComponent('ContractExampleRelatedList', RelatedContentTemplate, {
    label: '契约示例·关联列表', version: 1
  })
}
