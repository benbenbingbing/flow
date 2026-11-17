<template>
  <div v-if="groupCodes.length" class="group-tag-filter">
    <div v-for="code in groupCodes" :key="code" class="group-tag-row">
      <div class="group-heading">{{ groupOptions.find(group => group.code === code)?.label || code }}</div>
      <el-alert v-if="errors[code]" :title="errors[code]" type="error" :closable="false">
        <el-button link @click="loadTags(code)">重新加载标签</el-button>
      </el-alert>
      <el-select :model-value="modelValue[code] || []" multiple filterable clearable
        placeholder="全部成员（不选择标签）" style="width:100%" :loading="loading[code]"
        @update:model-value="value => emit('update:modelValue', { ...modelValue, [code]: value })">
        <el-option v-for="tag in options(code)" :key="tag.id" :value="tag.id" :label="tag.tagName" :disabled="tag.invalid" />
      </el-select>
    </div>
    <div class="scope-preview" aria-live="polite">
      <strong>候选范围预览</strong>
      <span v-if="previewLoading"> 加载中…</span>
      <span v-else-if="previewError" class="preview-error"> {{ previewError }}</span>
      <template v-else>
        <span> {{ preview.length }} 人</span>
        <div>{{ preview.slice(0, 20).map(previewName).join('、') || '没有匹配的有效成员，将按空审批人策略处理' }}{{ preview.length > 20 ? '…' : '' }}</div>
      </template>
    </div>
  </div>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { getGroupTags, previewGroupTags } from '@/api/system/groupTags'
const props = defineProps({ modelValue: { type: Object, default: () => ({}) }, groupCodes: { type: Array, default: () => [] }, groupOptions: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const tags = ref({}), errors = ref({}), loading = ref({})
const preview = ref([]), previewError = ref(''), previewLoading = ref(false)
let timer, previewVersion = 0
/** 昵称用于辨认人员，登录名用于区分同名用户；未设置昵称时直接显示登录名。 */
function previewName(user) {
  const nickname = user.nickname?.trim()
  return nickname ? `${nickname}（${user.username}）` : user.username
}
/** 目录加载失败或标签失效时保留已配置 ID，阻止无意扩大审批范围。 */
async function loadTags(code) {
  const group = props.groupOptions.find(group => group.code === code)
  if (!group) { errors.value[code] = '用户组不存在或已停用'; return }
  loading.value[code] = true
  try { tags.value[code] = await getGroupTags(group.id); errors.value[code] = '' }
  catch { errors.value[code] = '标签加载失败，当前筛选条件已保留' }
  finally { loading.value[code] = false }
}
function options(code) {
  const rows = tags.value[code] || []
  return [...rows, ...(props.modelValue[code] || []).filter(id => !rows.some(tag => tag.id === id)).map(id => ({ id, tagName: `已失效或未加载的标签（${id}）`, invalid: true }))]
}
watch(() => [props.groupCodes, props.groupOptions], () => { props.groupCodes.forEach(loadTags) }, { immediate: true, deep: true })
// 快速切换标签时只展示最后一次请求；预览失败不能回退成全组名单。
watch(() => [props.groupCodes, props.modelValue], () => {
  clearTimeout(timer)
  const version = ++previewVersion
  previewLoading.value = true
  timer = setTimeout(async () => {
    try {
      const rows = await previewGroupTags(props.groupCodes.map(groupCode => ({ groupCode, tagIds: props.modelValue[groupCode] || [] })))
      if (version === previewVersion) { preview.value = rows; previewError.value = '' }
    } catch { if (version === previewVersion) { preview.value = []; previewError.value = '范围解析失败，请检查用户组和标签是否有效' } }
    finally { if (version === previewVersion) previewLoading.value = false }
  }, 250)
}, { immediate: true, deep: true })
onBeforeUnmount(() => { clearTimeout(timer); previewVersion++ })
</script>
<style scoped>
.group-tag-filter { width:100%; }
.group-tag-row { padding:12px; border:1px solid var(--el-border-color); border-radius:4px; margin-bottom:10px; }
.group-heading { font-weight:500; margin-bottom:8px; }
.scope-preview { padding:10px; background:var(--el-fill-color-light); border-radius:4px; overflow-wrap:anywhere; }
.scope-preview div { margin-top:6px; }
.preview-error { color:var(--el-color-danger); }
</style>
