/**
 * BPMN.js 汉化配置
 * 使用 diagram-js 的翻译系统
 */

// 翻译字典
const translations = {
  // 上下文菜单（小扳手）
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append task': '追加任务',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Change type': '更改类型',
  'Remove': '删除',
  'Connect using sequence/message flow or association': '连接',
  'Activate the global connect tool': '全局连接工具',
  
  // 左侧工具栏
  'Create start event': '创建开始事件',
  'Create end event': '创建结束事件',
  'Create task': '创建任务',
  'Create user task': '创建用户任务',
  'Create service task': '创建服务任务',
  'Create gateway': '创建网关',
  'Create exclusive gateway': '创建排他网关',
  'Create parallel gateway': '创建并行网关',
  'Create pool/participant': '创建泳道',
  'Create expanded sub-process': '创建子流程',
  'Create data object reference': '创建数据对象',
  'Create data store reference': '创建数据存储',
  
  // 工具栏提示
  'Activate hand tool': '手型工具 (H)',
  'Activate lasso tool': '套索工具 (L)',
  'Activate create/remove space tool': '空间工具',
  'Global connect tool': '全局连接',
  
  // 元素类型
  'Start event': '开始事件',
  'End event': '结束事件',
  'Task': '任务',
  'User task': '用户任务',
  'Service task': '服务任务',
  'Gateway': '网关',
  'Exclusive gateway': '排他网关',
  'Parallel gateway': '并行网关',
  'Intermediate throw event': '中间抛出事件',
  'Intermediate catch event': '中间捕获事件',
  
  // 其他
  'Sub-process': '子流程',
  'Sub-process (expanded)': '子流程（展开）',
  'Sub-process (collapsed)': '子流程（折叠）',
  'Call activity': '调用活动',
  'Transaction': '事务',
  'Event sub-process': '事件子流程',
  'Pool': '泳道',
  'Lane': '通道',
  'Data object': '数据对象',
  'Data store': '数据存储',
  'Text annotation': '文本注释',
  'Group': '分组',
  'Connection': '连接',
  'Sequence flow': '顺序流',
  'Message flow': '消息流',
  'Association': '关联',
  'Data input association': '数据输入关联',
  'Data output association': '数据输出关联'
}

/**
 * 翻译函数
 */
export function translate(template, replacements = {}) {
  let result = translations[template] || template
  
  // 替换变量如 {element}
  Object.keys(replacements).forEach(key => {
    result = result.replace(new RegExp('{' + key + '}', 'g'), replacements[key])
  })
  
  return result
}

/**
 * 创建 bpmn-js 汉化模块
 * 符合 bpmn-js 的依赖注入格式
 */
export function createBpmnI18nModule() {
  // 返回一个函数，bpmn-js 会用它来实例化翻译服务
  return function(module) {
    // 覆盖 translate 服务
    module.translate = translate
    return module
  }
}

/**
 * 另一种方式：直接覆盖原型（简单粗暴但有效）
 */
export function overrideBpmnI18n() {
  // 在 BPMN Modeler 创建后调用
  return function(modeler) {
    const injector = modeler.get('injector')
    if (injector) {
      // 尝试获取 translate 服务并覆盖
      try {
        const originalTranslate = injector.get('translate', false)
        if (originalTranslate) {
          // 创建包装函数
          const wrappedTranslate = function(template, replacements) {
            return translate(template, replacements)
          }
          // 替换服务
          injector._instances.translate = wrappedTranslate
        }
      } catch (e) {
        console.warn('翻译服务覆盖失败:', e)
      }
    }
  }
}

export default {
  translate,
  createBpmnI18nModule,
  overrideBpmnI18n
}
