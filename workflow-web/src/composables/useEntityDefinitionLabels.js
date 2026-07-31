import { ref } from 'vue'
import { entityApi } from '@/api/entity'

export function useEntityDefinitionLabels() {
  const labels = ref({})

  function remember(option) {
    if (!option || Array.isArray(option) || !option.entityCode) return
    labels.value = {
      ...labels.value,
      [option.entityCode]: option.entityName || option.entityCode
    }
  }

  async function resolveCodes(values) {
    const entityCodes = [...new Set((values || []).filter(Boolean))]
    if (!entityCodes.length) return
    const options = await entityApi.resolveOptions({ entityCodes }).catch(() => [])
    labels.value = Object.fromEntries(
      (options || []).map(item => [item.entityCode, item.entityName || item.entityCode])
    )
  }

  function label(entityCode) {
    return labels.value[entityCode]
      ? `${labels.value[entityCode]} (${entityCode})`
      : entityCode
  }

  return { label, remember, resolveCodes }
}
