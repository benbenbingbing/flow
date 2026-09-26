import { ref } from 'vue'
import { entityVersionApi } from '@/api/entityVersion'
import { normalizeEntityVersionCapabilities } from '@/shared/entity-version-capabilities'

/**
 * 单独拥有版本能力及请求代次。列表刷新/Embed reload 的完成时机不在此模块改变；
 * 切换实体时先清空能力，迟到结果不能打开另一个实体的版本入口。
 */
export function useEntityVersionCapabilities({ entityCode, entityDefinition, isSystemEntity, canViewVersions }) {
  const versionCapabilities = ref(normalizeEntityVersionCapabilities())

  let versionCapabilitiesGeneration = 0

  /**
   * 实体或列表上下文变化时先关闭入口；代次递增用于丢弃旧请求的迟到响应。
   */
  function resetVersionCapabilities() {
    versionCapabilitiesGeneration += 1
    versionCapabilities.value = normalizeEntityVersionCapabilities()
    return versionCapabilitiesGeneration
  }

  /**
   * 仅为有权查看版本的普通实体读取运行能力。能力接口失败不阻断列表，
   * 并保持默认关闭，避免出现可见但点击后必然失败的入口。
   */
  async function loadVersionCapabilities(
    requestedEntityCode,
    generation
  ) {
    if (!requestedEntityCode
        || requestedEntityCode !== entityCode.value
        || generation !== versionCapabilitiesGeneration
        || !entityDefinition.value?.id
        || isSystemEntity.value
        || !canViewVersions.value) {
      return
    }
    try {
      const capabilities = await entityVersionApi.recordCapabilities(
        requestedEntityCode
      )
      if (generation !== versionCapabilitiesGeneration
          || requestedEntityCode !== entityCode.value) {
        return
      }
      versionCapabilities.value = normalizeEntityVersionCapabilities(
        capabilities
      )
    } catch (error) {
      if (generation === versionCapabilitiesGeneration
          && requestedEntityCode === entityCode.value) {
        versionCapabilities.value = normalizeEntityVersionCapabilities()
      }
      console.warn('加载实体版本能力失败，版本入口保持隐藏:', error)
    }
  }

  return {
    versionCapabilities,
    resetVersionCapabilities,
    loadVersionCapabilities
  }
}
