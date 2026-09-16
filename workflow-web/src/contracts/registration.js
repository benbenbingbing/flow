/**
 * 现有注册中心的统一入口，转导出原函数，保留原注册表和行为。
 * 只有调用 register* 才会注册项目扩展；导入会加载原组件模块及其内置注册项。
 * 不调用 registerApplicationExtensions，不扫描 templates/examples，不自动安装校验器。
 * 在项目初始化入口明确注册一次；按需导入，普通校验代码只依赖 index.js 即可。
 */
export {
  registerCustomFormComponent, getCustomFormComponent, getCustomFormDescriptor,
  getCustomFormComponentOptions, getCustomFormComponentVersionOptions,
  registerCustomListComponent, getCustomListComponent, getCustomListDescriptor,
  getCustomListComponentOptions, getCustomListComponentVersionOptions
} from '../utils/customComponentRegistry.js'
export {
  registerFormFieldComponent, getFormFieldComponent, getFormFieldComponentDescriptor,
  getFormFieldComponentOptions
} from '../components/form-fields/index.js'
export {
  registerFormNodeComponent, getFormNodeComponent, getFormNodeDescriptor,
  getFormNodeComponentOptions
} from '../utils/formNodeRegistry.js'
export {
  registerCellComponent, getCellComponent, getCellDescriptor, getCellComponentOptions
} from '../utils/listCellRegistry.js'
export {
  registerListToolbarAction, getListToolbarAction,
  registerListRowAction, getListRowAction
} from '../utils/listActionRegistry.js'
export {
  registerListButtonComponent, getListButtonComponent
} from '../utils/listButtonComponentRegistry.js'
export {
  registerEntityActionRuleCondition, getEntityActionRuleCondition, getEntityActionRuleConditions,
  registerEntityPermissionOptionProvider, resolveEntityPermissionOptions
} from '../utils/entityActionRuleRegistry.js'
