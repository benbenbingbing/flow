/** 业务时机顺序取后端目录，不按动作排序号把不同生命周期混在一起。 */
export function groupFlowActions(actions, timings) {
  const groups = []
  const timingCodes = [...new Set([...timings.map(item => item.value), ...actions.map(item => item.triggerTiming)])]
  for (const timing of timingCodes) {
    for (const mode of ['IN_TRANSACTION', 'AFTER_COMMIT']) {
      const items = actions.filter(item => item.triggerTiming === timing && item.executionMode === mode)
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || String(a.id).localeCompare(String(b.id)))
      if (items.length) groups.push({ key: `${timing}:${mode}`, timing, mode, actions: items })
    }
  }
  return groups
}

function sameGroup(a, b) {
  return a.processConfigId === b.processConfigId && a.scopeType === b.scopeType
    && (a.elementId || '') === (b.elementId || '') && a.triggerTiming === b.triggerTiming
    && a.executionMode === 'AFTER_COMMIT' && b.executionMode === 'AFTER_COMMIT'
}

/** 排除自身和依赖自己的后继，避免下拉选择产生循环；后端仍执行完整权威校验。 */
export function predecessorOptions(actions, editing) {
  const byId = new Map(actions.map(item => [item.id, item]))
  return actions.filter(item => {
    if (!item.enabled || item.id === editing.id || !sameGroup(item, editing)) return false
    const visited = new Set()
    let current = item
    while (current) {
      if (current.id === editing.id || visited.has(current.id)) return false
      visited.add(current.id)
      current = byId.get(current.predecessorActionId)
    }
    return true
  })
}

export function dependencyProblem(actions, editing) {
  if (!editing.predecessorActionId) return ''
  return predecessorOptions(actions, editing).some(item => item.id === editing.predecessorActionId)
    ? '' : '前置动作必须是同一绑定位置、同一时机、同一执行方式下启用的动作，且不能形成循环'
}

/** 固定结构引用，不接受表达式；输出为 JSON 原始类型，数字、布尔和对象均不转为文本。 */
export function parseActionParams(value) {
  if (!value) return []
  return Object.entries(JSON.parse(value)).map(([name, raw]) => {
    if (raw && typeof raw === 'object' && Object.hasOwn(raw, '$predecessor')) {
      return { name, type: 'predecessor', value: raw.$predecessor }
    }
    let type = typeof raw
    if (type === 'string' && raw.startsWith('${') && raw.endsWith('}')) type = 'variable'
    if (!['number', 'boolean', 'variable'].includes(type)) type = 'string'
    return { name, type, value: String(raw) }
  })
}

export function serializeActionParams(rows, predecessorActionId) {
  const params = {}
  for (const param of rows) {
    if (!param.name) continue
    if (param.type === 'predecessor') {
      if (!predecessorActionId || !/^[A-Za-z_][A-Za-z0-9_]*(\.(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*$/.test(param.value)
          || param.value.length > 256) throw new Error('引用前置结果时，请先选择前置动作，并填写字段路径，如 contractNo')
      params[param.name] = { $predecessor: param.value }
    } else if (param.type === 'number') params[param.name] = Number(param.value)
    else if (param.type === 'boolean') params[param.name] = param.value === true || param.value === 'true'
    else if (param.type === 'variable' || param.type === 'expression') {
      params[param.name] = String(param.value).startsWith('${') ? param.value : `\${${param.value}}`
    } else params[param.name] = param.value
  }
  return JSON.stringify(params)
}
