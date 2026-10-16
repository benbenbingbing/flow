function parseObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) {
    return { ...value }
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

export function templateSnapshot(snapshot, wrapperKey) {
  const parsed = parseObject(snapshot)
  const wrapped = wrapperKey ? parsed[wrapperKey] : null
  return wrapped && typeof wrapped === 'object' && !Array.isArray(wrapped)
    ? wrapped
    : parsed
}

export function applyTemplateSnapshot(
  target,
  snapshot,
  {
    wrapperKey,
    preserveKeys = []
  } = {}
) {
  const preserved = Object.fromEntries(
    preserveKeys
      .filter(key => Object.prototype.hasOwnProperty.call(target, key))
      .map(key => [key, target[key]])
  )
  Object.assign(target, templateSnapshot(snapshot, wrapperKey), preserved)
  return target
}

export function normalizeTemplateOverrides(value) {
  return parseObject(value)
}

export function setTemplateBinding(
  target,
  templateId,
  templateVersion,
  localOverrides = {}
) {
  target.templateId = templateId || null
  target.templateVersion = templateId ? Number(templateVersion) : null
  target.localOverridesDocument = templateId
    ? JSON.stringify(normalizeTemplateOverrides(localOverrides))
    : ''
  return target
}

export function clearTemplateBinding(target) {
  target.templateId = null
  target.templateVersion = null
  target.localOverridesDocument = ''
  return target
}

export function restoreButtonTemplateBinding(button) {
  const binding = parseObject(button?.__templateBinding)
  if (!binding.templateId) return button
  button.templateId = binding.templateId
  button.templateVersion = Number(binding.templateVersion) || null
  button.localOverridesDocument = JSON.stringify(
    normalizeTemplateOverrides(binding.localOverrides)
  )
  return button
}

export function serializeButtonTemplateBinding(button) {
  if (!button?.templateId) return null
  return {
    templateId: button.templateId,
    templateVersion: Number(button.templateVersion),
    localOverrides: normalizeTemplateOverrides(button.localOverridesDocument)
  }
}
