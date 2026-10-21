import { updateBpmnExtensionProperty } from './bpmnExtensionProperties.js'

const registeredStacks = new WeakSet()

/** 每次选中节点都建立独立表单，防止上一个调用节点的实体/参数配置串入当前节点。 */
export function readCalledProcessConfig(businessObject, rawConfig) {
  const defaults = {
    calledElement: businessObject?.calledElement || '', callActivityType: 'bpmn',
    inputParameters: '', outputParameters: '', businessKey: '',
    entityBinding: rawConfig || businessObject?.calledElement ? '' : 'existing', entityRecordId: ''
  }
  return { ...defaults, ...(rawConfig ? JSON.parse(rawConfig) : {}) }
}

/** 与后端记录来源语法一致：只引用父流程变量或 Map 路径，不能执行方法。 */
export function validateCalledProcessConfig(config) {
  if (!config.calledElement?.trim()) return '请选择要调用的已有流程'
  if (config.entityBinding !== 'existing') return ''
  if (config.callActivityType !== 'bpmn') return '关联实体记录仅支持 BPMN 流程'
  let path = String(config.entityRecordId || '').trim()
  if (path.startsWith('${') && path.endsWith('}')) path = path.slice(2, -1).trim()
  if (!/^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/.test(path)) {
    return '请选择记录 ID 来源字段，或填写 ${relatedRecordId} 形式的父流程参数'
  }
  return ''
}

/**
 * 以单个命令将内嵌子流程替换为调用活动，保留外围连线、ID 和已有节点配置。
 * 内部节点由 bpmn-js 替换规则移除；整次操作可撤销，避免保存半转换的模型。
 */
export function replaceWithCalledProcess(modeler, element, config) {
  const stack = modeler.get('commandStack')
  const command = 'workflow.replaceWithCalledProcess'
  if (!registeredStacks.has(stack)) {
    stack.registerHandler(command, class {
      preExecute(context) {
        const id = context.element.id
        const replacement = modeler.get('bpmnReplace').replaceElement(context.element, { type: 'bpmn:CallActivity' })
        modeler.get('modeling').updateProperties(replacement, { id, calledElement: context.config.calledElement })
        updateBpmnExtensionProperty(replacement, modeler.get('moddle'), modeler.get('modeling'),
          'callConfig', JSON.stringify(context.config))
        context.replacement = replacement
      }
      execute() { }
      revert() { }
    })
    registeredStacks.add(stack)
  }
  const context = { element, config }
  stack.execute(command, context)
  return context.replacement
}
