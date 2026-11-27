<template>
  <el-dialog
    v-model="visible"
    title="配置用户组成员标签"
    width="min(640px, 94vw)"
    top="8vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    class="group-tag-config-dialog"
  >
    <div v-loading="groupTagOptionsLoading" class="group-tag-dialog-content">
      <el-alert v-if="groupTagOptionsError" :title="groupTagOptionsError" type="error" :closable="false">
        <el-button link type="primary" @click="loadGroupTagOptions">重新加载用户组</el-button>
      </el-alert>
      <template v-else-if="!groupTagOptionsLoading">
        <p class="group-tag-dialog-help">每组不选标签时包含全部有效成员，选择多个标签时匹配任意一个。</p>
        <GroupTagFilterEditor
          v-model="groupTagDraft"
          :group-codes="groupCodes"
          :group-options="groupTagOptions"
        />
      </template>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="groupTagOptionsLoading || !!groupTagOptionsError"
        @click="confirmGroupTagConfig"
      >确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import GroupTagFilterEditor from '@/components/GroupTagFilterEditor.vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  groupCodes: { type: Array, default: () => [] }
})
const visible = defineModel('visible', { type: Boolean, default: false })
const emit = defineEmits(['update:modelValue'])

const groupTagDraft = ref({})
const groupTagOptions = ref([])
const groupTagOptionsLoading = ref(false)
const groupTagOptionsError = ref('')

/** 组标签弹窗维护临时筛选，确认时才更新节点的审批范围。 */
watch(visible, isVisible => {
  if (!isVisible) return
  groupTagDraft.value = Object.fromEntries(Object.entries(props.modelValue)
    .map(([code, tagIds]) => [code, [...tagIds]]))
  loadGroupTagOptions()
})

/** 标签接口以用户组 ID 查询目录，流程配置仍使用可迁移的用户组编码。 */
async function loadGroupTagOptions() {
  const codes = props.groupCodes
  const draft = groupTagDraft.value
  const isCurrent = () => visible.value && draft === groupTagDraft.value
  groupTagOptions.value = []
  groupTagOptionsLoading.value = true
  groupTagOptionsError.value = ''
  try {
    const records = []
    // 只回查已选用户组，分批限制请求地址长度。
    for (let index = 0; index < codes.length; index += 50) {
      const params = new URLSearchParams({ ids: codes.slice(index, index + 50).join(','), valueKey: 'code' })
      const batch = await request.get(`/entity-selector/GROUP/batch?${params}`)
      if (!isCurrent()) return
      records.push(...batch)
    }
    groupTagOptions.value = records
      .filter(row => String(row.status) === '0')
      .map(row => ({ id: row.id, code: row.code, label: row.name }))
  } catch {
    if (isCurrent()) groupTagOptionsError.value = '用户组加载失败，请重试'
  } finally {
    if (isCurrent()) groupTagOptionsLoading.value = false
  }
}

function confirmGroupTagConfig() {
  emit('update:modelValue', groupTagDraft.value)
  visible.value = false
}
</script>

<style scoped>
.group-tag-dialog-content { min-height: 64px; }
.group-tag-dialog-help { margin: 0 0 12px; color: var(--el-text-color-regular); }
:global(.group-tag-config-dialog > .el-dialog__body) {
  max-height: 66vh;
  overflow-y: auto;
}
</style>
