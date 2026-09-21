/**
 * 现有子表单参数契约的复用入口，全部转导出原实现，避免出现第二套解析规则。
 * 子表单声明 inputParameterSchema；父级节点 parameterContract 包含：
 * { version: 1, parameterMapping: {...}, fieldInitializationMapping: {...} }。
 * parameterMapping 产生子表单 context.params；fieldInitializationMapping 只初始化业务字段。
 * 使用设计器声明映射，不把任意父字段隐式注入子表单。
 *
 * resolveSubFormParameters(contract, source, schema) 解析参数；随后通过
 * validateSubFormParameters(params, schema) 检查必填和类型，返回错误字符串数组（[] 通过）。
 * applySubFormFieldInitialization(row, contract, source, blockedFields) 会原地填充 row
 * 中的空字段，跳过被禁用的字段和已有值；返回是否修改，调用前按需复制业务对象。
 *
 * 映射示例：{ version: 1, parameterMapping: { projectId: 'parent.recordId',
 *   scene: { literal: 'DETAIL' } }, fieldInitializationMapping: { projectName: 'parent.data.name' } }。
 * source 示例：{ parent: { recordId: 'p1', data: { name: '项目 A' } } }。
 * 路径是以点分隔的属性读取；固定值使用 { literal: value }，没有脚本执行。
 */
export {
  SUBFORM_PARAMETER_CONTRACT_VERSION,
  normalizeInputParameterSchema, buildInputParameterSchema, getInputParameterDefinitions,
  normalizeSubFormParameterContract, resolveSubFormParameters, validateSubFormParameters,
  applySubFormFieldInitialization, buildSubFormParentContext
} from '@flow/workflow-core/subform-parameter-contract'
