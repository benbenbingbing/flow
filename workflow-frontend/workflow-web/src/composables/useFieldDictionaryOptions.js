import { ref, toValue, watch } from 'vue'
import { getItemTreeByDictCode } from '@/api/system/dict'

/** 按代码表去重加载字段候选；只用于当前页面展示，不把候选写回实体或表单配置。 */
export function useFieldDictionaryOptions(fields) {
  const optionsByCode = ref({})
  watch(() => [...new Set((toValue(fields) || []).map(field => field.dictType).filter(Boolean))],
    async (codes, _, onCleanup) => {
      let current = true
      onCleanup(() => { current = false })
      optionsByCode.value = {}
      const entries = await Promise.all(codes.map(async code => {
        try { return [code, flatten(await getItemTreeByDictCode(code))] }
        catch { return [code, []] }
      }))
      // 字段切换或组件卸载后，不得用迟到响应覆盖新来源。
      if (current) optionsByCode.value = Object.fromEntries(entries)
    }, { immediate: true })
  return optionsByCode
}

function flatten(items) {
  return (Array.isArray(items) ? items : []).flatMap(item => [
    { value: item.itemCode, label: item.itemLabel, disabled: String(item.status) !== '0' },
    ...flatten(item.children)
  ])
}
