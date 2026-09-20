import { registerCustomValidator } from '../../contracts/validator-registry.js'
import { AmountValidator } from './AmountValidator.js'

/** 应用启动时注册一次；发布绑定固定 name/version，修改业务规则或范围应新增版本。 */
export function registerProjectValidators() {
  registerCustomValidator('amount', new AmountValidator(), {
    version: 1,
    label: '金额校验',
    description: '金额必须为非负有限数字，且不超过当前字段配置的上限；空值交给必填规则。',
    // [] 或 ['*'] 表示全部实体；例如 ['expense', 'purchase_order'] 仅允许这两个实体。
    supportedEntityCodes: ['*'],
    supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'NUMBER','STRING'],
    configSchema: [{ key: 'maxAmount', label: '金额上限', type: 'number', required: true, min: 0, defaultValue: 1000 }]
  })
}
