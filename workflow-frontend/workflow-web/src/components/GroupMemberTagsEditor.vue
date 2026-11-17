<template>
  <div class="member-tags-editor" v-loading="loading">
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link @click="load">重新加载</el-button>
    </el-alert>
    <template v-else>
      <div class="tag-toolbar">
        <strong>组内标签</strong>
        <el-button link type="primary" :disabled="creatingTag" @click="editTag()">新增标签</el-button>
      </div>
      <div class="tag-list">
        <span v-for="tag in tags" :key="tag.id" class="tag-item">
          <el-tag>{{ tag.tagName }}</el-tag>
          <el-button link size="small" :disabled="creatingTag" @click="editTag(tag)">改名</el-button>
          <el-button link size="small" type="danger" :disabled="creatingTag" @click="removeTag(tag)">删除</el-button>
        </span>
        <span v-if="!tags.length" class="help">暂无标签，按需新增即可。</span>
      </div>
      <div v-if="memberIds.length" class="tag-toolbar">
        <el-select :model-value="batchTags" multiple filterable clearable allow-create default-first-option :reserve-keyword="false"
          :disabled="creatingTag" placeholder="批量选择或输入标签（空值清空）" style="flex:1"
          @update:model-value="values => updateTags(values)">
          <el-option v-for="tag in tags" :key="tag.id" :value="tag.id" :label="tag.tagName" />
        </el-select>
        <el-button :disabled="creatingTag || !selectedRows.length" @click="applyBatch">应用到已勾选 {{ selectedRows.length }} 人</el-button>
      </div>
      <el-table :data="rows" row-key="id" max-height="320" @selection-change="value => selectedRows = value">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="name" label="成员" min-width="120" />
        <el-table-column label="成员标签" min-width="250">
          <template #default="{ row }">
            <el-select :model-value="assignments[row.id] || []" multiple filterable clearable allow-create default-first-option
              :reserve-keyword="false" :disabled="creatingTag" placeholder="选择或输入标签后回车" style="width:100%"
              @update:model-value="values => updateTags(values, row.id)">
              <el-option v-for="tag in tags" :key="tag.id" :value="tag.id" :label="tag.tagName" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <div class="help">同一成员可有多个标签；离组会清除标签。新标签确认后立即保存；成员和标签分配在点击“保存成员”后一起生效。</div>
    </template>
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGroupById } from '@/api/system/group'
import { getGroupTags, getGroupMemberTags, createGroupTag, renameGroupTag, deleteGroupTag } from '@/api/system/groupTags'
const props = defineProps({ groupId: { type: String, required: true }, memberIds: { type: Array, default: () => [] }, selectedUsers: { type: Array, default: () => [] } })
const tags = ref([]), assignments = ref({}), names = ref({}), loading = ref(false), loadError = ref('')
const batchTags = ref([]), selectedRows = ref([])
const creatingTag = ref(false)
let loadVersion = 0
const rows = computed(() => props.memberIds.map(id => ({ id, name: props.selectedUsers.find(user => String(user.id) === id)?.name || props.selectedUsers.find(user => String(user.id) === id)?.nickname || names.value[id] || id })))
/** 只在切换组时加载；标签载入失败时禁止提交，不能把未知状态当成清空标签。 */
async function load() {
  const version = ++loadVersion
  creatingTag.value = false
  loading.value = true; loadError.value = ''
  try {
    const [directory, bindings, group] = await Promise.all([getGroupTags(props.groupId), getGroupMemberTags(props.groupId), getGroupById(props.groupId)])
    if (version !== loadVersion) return
    tags.value = directory
    assignments.value = Object.fromEntries(bindings.map(row => [row.userId, row.tagIds]))
    for (const id of props.memberIds) assignments.value[id] ||= []
    names.value = Object.fromEntries((group.users || []).map(user => [String(user.id), user.nickname || user.username]))
  } catch { if (version === loadVersion) loadError.value = '成员标签加载失败，请重新加载后再保存' }
  finally { if (version === loadVersion) loading.value = false }
}
/** 新名称先保存为稳定 ID 再选中；请求失败保留原分配，不能将名称当成标签 ID 提交。 */
async function updateTags(values, userId) {
  if (creatingTag.value) return
  const version = loadVersion, groupId = props.groupId
  creatingTag.value = true
  try {
    const ids = []
    for (const value of values) {
      const name = String(value).trim()
      let tag = tags.value.find(row => row.id === value) || tags.value.find(row => row.tagName === name)
      if (!tag) {
        if (!name || name.length > 50) {
          ElMessage.warning('标签名称请输入1到50个字符')
          return
        }
        tag = await createGroupTag(groupId, name)
        // 切换组或关闭弹窗后，旧请求结果不能写入新的编辑状态。
        if (version !== loadVersion) return
        tags.value = [...tags.value, tag]
      }
      ids.push(tag.id)
    }
    if (userId === undefined) batchTags.value = [...new Set(ids)]
    else assignments.value[userId] = [...new Set(ids)]
  } catch { /* 接口错误由请求层提示，保留原分配，允许重新输入。 */ }
  finally { if (version === loadVersion) creatingTag.value = false }
}
async function editTag(tag) {
  try {
    const { value } = await ElMessageBox.prompt('标签仅在当前用户组内使用，最多50个字符。', tag ? '修改标签名称' : '新增标签', {
      inputValue: tag?.tagName || '', inputValidator: value => (value?.trim().length > 0 && value.trim().length <= 50) || '请输入1到50个字符'
    })
    const saved = tag ? await renameGroupTag(props.groupId, tag.id, value.trim()) : await createGroupTag(props.groupId, value.trim())
    tags.value = tag ? tags.value.map(row => row.id === tag.id ? saved : row) : [...tags.value, saved]
  } catch { /* 取消不变更，接口错误由请求层提示，保留尚未保存的分配。 */ }
}
async function removeTag(tag) {
  try {
    await ElMessageBox.confirm(`删除“${tag.tagName}”会移除成员绑定；引用该标签的流程条件将失效，后续进入节点会按空审批人策略处理。`, '删除组内标签', { type: 'warning' })
    await deleteGroupTag(props.groupId, tag.id)
    tags.value = tags.value.filter(row => row.id !== tag.id)
    for (const id of Object.keys(assignments.value)) assignments.value[id] = assignments.value[id].filter(value => value !== tag.id)
    batchTags.value = batchTags.value.filter(value => value !== tag.id)
  } catch { /* 保留当前编辑，允许用户取消或修正后重试。 */ }
}
function applyBatch() { selectedRows.value.forEach(row => { assignments.value[row.id] = [...batchTags.value] }) }
watch(() => props.groupId, load, { immediate: true })
watch(() => props.memberIds, ids => { for (const id of ids) assignments.value[id] ||= [] }, { deep: true })
onBeforeUnmount(() => { loadVersion++ })
// 创建完成前禁止提交，避免“保存成员”抢先发送尚未取得 ID 的标签。
defineExpose({ ready: computed(() => !loading.value && !loadError.value && !creatingTag.value), getAssignments: () => props.memberIds.map(userId => ({ userId, tagIds: assignments.value[userId] || [] })) })
</script>
<style scoped>
.member-tags-editor { margin-top:18px; }
.tag-toolbar, .tag-list, .tag-item { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.tag-toolbar { margin:12px 0; }
.tag-list { margin-bottom:12px; }
.help { color:var(--el-text-color-secondary); font-size:12px; margin-top:8px; }
</style>
