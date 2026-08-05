const DEFAULT_INPUT_TARGETS = Object.freeze({
  filters: 'filters',
  pageNum: 'pageNum',
  pageSize: 'pageSize',
  scene: 'scene',
  context: 'context'
})

const DEFAULT_OUTPUT_PATHS = Object.freeze({
  records: '',
  total: '',
  pageNum: '',
  pageSize: ''
})

function parseJson(value, fallback) {
  if (!value) return fallback
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function bindingSteps(binding) {
  const steps = parseJson(binding?.stepsDocument, binding?.steps || [])
  return Array.isArray(steps) ? steps : []
}

export function findListLoadBinding(bindings = []) {
  return bindings.find(binding =>
    String(binding?.eventCode).toUpperCase() === 'LIST_LOAD'
      && String(binding?.targetType || 'OWNER').toUpperCase() === 'OWNER'
  ) || null
}

export function isSimpleListQueryBinding(binding) {
  const steps = bindingSteps(binding)
  if (!binding || binding.enabled === false) return false
  if (String(binding.inheritanceMode).toUpperCase() !== 'REPLACE') return false
  if (steps.length !== 1) return false
  const [step] = steps
  return String(step.strategy).toUpperCase() === 'REPLACE'
    && Boolean(step.serviceId || step.sourceId)
    && (!step.condition || Object.keys(step.condition).length === 0)
}

export function createListQueryEditor(binding) {
  const defaults = {
    serviceId: '',
    operationCode: '',
    inputTargets: { ...DEFAULT_INPUT_TARGETS },
    outputPaths: { ...DEFAULT_OUTPUT_PATHS }
  }
  if (!isSimpleListQueryBinding(binding)) return defaults
  const [step] = bindingSteps(binding)
  const inputMapping = step.inputMapping || {}
  const outputMapping = Array.isArray(step.outputMapping)
    ? step.outputMapping
    : Object.entries(step.outputMapping || {}).map(([targetPath, sourcePath]) => ({
        targetPath,
        sourcePath
      }))
  const inputTargets = { ...DEFAULT_INPUT_TARGETS }
  Object.entries({
    filters: 'input.filters',
    pageNum: 'input.pageNum',
    pageSize: 'input.pageSize',
    scene: 'input.scene',
    context: 'context'
  }).forEach(([key, sourcePath]) => {
    const target = Object.entries(inputMapping)
      .find(([, source]) => source === sourcePath)?.[0]
    if (target) inputTargets[key] = target
  })
  const outputPaths = { ...DEFAULT_OUTPUT_PATHS }
  Object.keys(outputPaths).forEach((targetPath) => {
    const row = outputMapping.find(item => item.targetPath === targetPath)
    if (row?.sourcePath) outputPaths[targetPath] = row.sourcePath
  })
  return {
    serviceId: step.serviceId || step.sourceId || '',
    operationCode: step.operationCode || 'default',
    inputTargets,
    outputPaths
  }
}

export function buildListQueryBindingPayload(
  editor,
  ownerId,
  currentBinding = null
) {
  const inputSources = {
    filters: 'input.filters',
    pageNum: 'input.pageNum',
    pageSize: 'input.pageSize',
    scene: 'input.scene',
    context: 'context'
  }
  const inputMapping = {}
  Object.entries(editor.inputTargets || {}).forEach(([key, targetPath]) => {
    if (targetPath && inputSources[key]) {
      inputMapping[targetPath] = inputSources[key]
    }
  })
  const outputMapping = Object.entries(editor.outputPaths || {})
    .filter(([, sourcePath]) => Boolean(sourcePath))
    .map(([targetPath, sourcePath]) => ({
      sourcePath,
      targetPath,
      transform: 'IDENTITY',
      overwrite: 'ALWAYS',
      clearOnEmpty: true
    }))
  return {
    expectedRevision: currentBinding?.revision ?? null,
    ownerType: 'LIST',
    ownerId: String(ownerId),
    targetType: 'OWNER',
    targetKey: '',
    eventCode: 'LIST_LOAD',
    inheritanceMode: 'REPLACE',
    enabled: true,
    steps: [{
      strategy: 'REPLACE',
      serviceId: editor.serviceId,
      operationCode: editor.operationCode,
      order: 10,
      condition: {},
      inputMapping,
      outputMapping,
      failurePolicy: 'STOP'
    }]
  }
}

export function listQueryEditorFingerprint(editor, complex = false) {
  return JSON.stringify({
    complex,
    serviceId: editor?.serviceId || '',
    operationCode: editor?.operationCode || '',
    inputTargets: editor?.inputTargets || DEFAULT_INPUT_TARGETS,
    outputPaths: editor?.outputPaths || DEFAULT_OUTPUT_PATHS
  })
}
