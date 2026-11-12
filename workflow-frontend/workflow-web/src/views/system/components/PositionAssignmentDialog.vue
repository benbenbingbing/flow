<template>
  <el-dialog :model-value="modelValue" title="设置职务人员" width="620px" append-to-body destroy-on-close
    :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)" @open="initialize" @closed="invalidate">
    <el-alert title="保存后立即生效，已生成的审批任务保持原办理人。" type="info" :closable="false" show-icon />
    <el-table v-if="lockedUser && userAssignments.length" :data="userAssignments" class="current-positions">
      <el-table-column prop="organizationUnitName" label="组织或部门" />
      <el-table-column prop="positionName" label="当前职务" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }"><el-button link type="danger" :disabled="submitting" @click="removeCurrent(row)">移除</el-button></template>
      </el-table-column>
    </el-table>
    <el-form label-width="100px" class="member-form" v-loading="loading">
      <el-form-item v-if="lockedUser" label="人员">{{ lockedUser.nickname || lockedUser.username }}</el-form-item>
      <el-form-item label="组织或部门" required>
        <el-tree-select v-model="form.organizationUnitId" :data="organizationTree" node-key="id"
          :props="{ label: 'orgName', children: 'children' }" check-strictly filterable default-expand-all
          :disabled="Boolean(lockedUnit) || submitting" placeholder="请选择组织或部门" style="width: 100%" @change="loadMembers" />
      </el-form-item>
      <el-form-item label="职务" required>
        <el-select v-model="form.positionCode" filterable :disabled="Boolean(lockedPosition) || submitting"
          placeholder="请选择职务" style="width: 100%" @change="loadMembers">
          <el-option v-for="position in selectablePositions" :key="position.positionCode"
            :label="position.positionName" :value="position.positionCode" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!lockedUser" label="当前人员">
        <UserSelector v-if="selectedPosition?.holderMode === 'SINGLE'" v-model="singleUserId" value-key="id"
          :disabled="!membersLoaded || submitting" placeholder="请选择人员，清空可移除" title="选择职务人员" />
        <UserSelector v-else v-model="form.userIds" multiple value-key="id" :disabled="!membersLoaded || submitting"
          placeholder="请选择人员，清空可移除全部" title="选择职务人员" />
      </el-form-item>
    </el-form>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!membersLoaded || loading" @click="submit">
        {{ lockedUser ? '设置职务' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserSelector from '@/components/UserSelector.vue'
import { getOrgTree } from '@/api/system/org'
import { getEnabledPositions, getOrganizationPositionAssignments, getUserPositionAssignments,
  removePositionAssignment, setPositionMembers } from '@/api/system/position'

const props = defineProps({ modelValue: Boolean, lockedUser: { type: Object, default: null },
  lockedUnit: { type: Object, default: null }, lockedPosition: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const organizationTree = ref<any[]>([])
const positionOptions = ref<any[]>([])
const userAssignments = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)
const membersLoaded = ref(false)
const loadError = ref('')
const expectedUserIds = ref<string[]>([])
const form = reactive({ organizationUnitId: '', positionCode: '', userIds: [] as string[] })
let sequence = 0
const selectedPosition = computed(() => positionOptions.value.find(p => p.positionCode === form.positionCode))
const singleUserId = computed({ get: () => form.userIds[0] || '', set: value => { form.userIds = value ? [value] : [] } })
const selectedUnit = computed(() => {
  const find = (nodes: any[]): any => {
    for (const node of nodes) { if (node.id === form.organizationUnitId) return node; const match = find(node.children || []); if (match) return match }
  }
  return find(organizationTree.value)
})
const selectablePositions = computed(() => positionOptions.value.filter(position => !selectedUnit.value
  || position.applicableUnitType === 'ANY' || position.applicableUnitType.toLowerCase() === selectedUnit.value.type))

/** 关闭和切换条件均使旧请求失效，防止慢响应覆盖新选择或提交错误范围的人员。 */
function invalidate() { sequence++; membersLoaded.value = false }
async function initialize() {
  invalidate()
  const current = sequence
  loading.value = true
  loadError.value = ''
  Object.assign(form, { organizationUnitId: props.lockedUnit?.id || props.lockedUser?.deptId || props.lockedUser?.orgId || '',
    positionCode: props.lockedPosition?.positionCode || '', userIds: [] })
  userAssignments.value = []
  try {
    const [tree, positions, user] = await Promise.all([getOrgTree(), getEnabledPositions(),
      props.lockedUser ? getUserPositionAssignments(props.lockedUser.id) : Promise.resolve(null)])
    if (current !== sequence) return
    organizationTree.value = Array.isArray(tree) ? tree : []
    positionOptions.value = Array.isArray(positions) ? positions : []
    userAssignments.value = user?.assignments || []
    loading.value = false
    await loadMembers()
  } catch (error: any) {
    if (current === sequence) { loadError.value = error?.message || '职务数据加载失败'; loading.value = false }
  }
}

/** 编辑前读取完整集合供后台并发检查；用户入口只添加本人，保留同职务的其他人员。 */
async function loadMembers() {
  invalidate()
  form.userIds = []
  expectedUserIds.value = []
  loadError.value = ''
  if (!selectablePositions.value.some(position => position.positionCode === form.positionCode)) form.positionCode = ''
  if (!form.organizationUnitId || !form.positionCode) { loading.value = false; return }
  const current = sequence
  loading.value = true
  try {
    const result = await getOrganizationPositionAssignments(form.organizationUnitId)
    if (current !== sequence) return
    expectedUserIds.value = (result.assignments || []).filter((row: any) => row.positionCode === form.positionCode).map((row: any) => row.userId)
    form.userIds = [...expectedUserIds.value]
    membersLoaded.value = true
  } catch (error: any) {
    if (current === sequence) loadError.value = error?.message || '当前人员加载失败'
  } finally { if (current === sequence) loading.value = false }
}

async function submit() {
  if (!membersLoaded.value || loading.value || submitting.value) return
  let userIds = [...form.userIds]
  if (props.lockedUser) {
    userIds = selectedPosition.value?.holderMode === 'SINGLE' ? [props.lockedUser.id]
      : [...new Set([...expectedUserIds.value, props.lockedUser.id])]
    if (selectedPosition.value?.holderMode === 'SINGLE' && expectedUserIds.value.some(id => id !== props.lockedUser.id)) {
      try { await ElMessageBox.confirm('该职务已有人员，保存后将替换为当前用户。', '更换职务人员', { type: 'warning' }) }
      catch { return }
    }
  }
  submitting.value = true
  try {
    await setPositionMembers({ positionCode: form.positionCode, organizationUnitId: form.organizationUnitId,
      userIds, expectedUserIds: expectedUserIds.value })
    ElMessage.success('职务人员已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally { submitting.value = false }
}

/** 移除当前用户的一条职务关系，不影响该组织同职务的其他人员。 */
async function removeCurrent(row: any) {
  try { await ElMessageBox.confirm(`移除「${row.organizationUnitName} · ${row.positionName}」？`, '移除职务', { type: 'warning' }) }
  catch { return }
  submitting.value = true
  try {
    await removePositionAssignment(row.id, row.revision)
    userAssignments.value = (await getUserPositionAssignments(props.lockedUser.id)).assignments || []
    await loadMembers()
    emit('saved')
  } finally { submitting.value = false }
}
</script>

<style scoped>
.member-form { margin-top: 20px; }
.current-positions { margin-top: 12px; }
</style>
