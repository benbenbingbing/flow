import { registerCustomFormComponent, registerCustomListComponent } from '../registries/customComponentRegistry.js'
import { registerFormFieldComponent } from '../registries/formFieldRegistry.js'
import { registerFormNodeComponent } from '../registries/formNodeRegistry.js'
import { registerCellComponent } from '../registries/listCellRegistry.js'
import { registerListButtonComponent } from '../registries/listButtonComponentRegistry.js'
import { registerListToolbarAction, registerListRowAction } from '../registries/listActionRegistry.js'
import { registerEntityActionRuleCondition, registerEntityPermissionOptionProvider } from '../registries/entityActionRuleRegistry.js'
import { registerCustomValidator } from '@flow/workflow-core/extensions/core/registries/validatorRegistry'

/** 平台内部注册适配器；业务新增实现只写 JSON，value 已经由安装器校验并实例化。 */
export const extensionAdapters = {
  FORM: (entry, value, metadata) => registerCustomFormComponent(entry.name, value, metadata),
  LIST: (entry, value, metadata) => registerCustomListComponent(entry.name, value, metadata),
  FIELD: (entry, value, metadata) => registerFormFieldComponent(entry.name, value, { ...metadata, aliases: entry.aliases || [] }),
  NODE: (entry, value, metadata) => registerFormNodeComponent(entry.name, value, metadata),
  LIST_CELL: (entry, value, metadata) => registerCellComponent(entry.name, value, metadata),
  LIST_BUTTON: (entry, value) => registerListButtonComponent(entry.name, value),
  VALIDATOR: (entry, value, metadata) => registerCustomValidator(entry.name, value, metadata),
  LIST_ACTION(entry, value) {
    if (entry.targets.includes('TOOLBAR')) registerListToolbarAction(entry.name, value)
    if (entry.targets.includes('ROW')) registerListRowAction(entry.name, value)
  },
  ACTION_CONDITION(entry, value, metadata) {
    registerEntityActionRuleCondition({
      type: entry.name,
      label: entry.label,
      component: value,
      // 每次新建条件深复制，避免多个按钮共享可变编辑状态。
      createDefault: metadata.createDefault || (() => JSON.parse(JSON.stringify(entry.defaultConfig || {})))
    })
  },
  PERMISSION_PROVIDER: (_entry, value) => registerEntityPermissionOptionProvider(value)
}
