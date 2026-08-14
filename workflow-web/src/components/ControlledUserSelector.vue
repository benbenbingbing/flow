<template>
  <div class="controlled-user-selector">
    <div
      class="controlled-user-selector__input"
      :class="{ 'is-disabled': disabled }"
      role="button"
      :tabindex="disabled ? -1 : 0"
      :aria-disabled="disabled"
      @click="open"
      @keydown.enter="open"
      @keydown.space.prevent="open"
    >
      <div v-if="displayUsers.length" class="controlled-user-selector__values">
        <el-tag
          v-for="user in displayUsers"
          :key="user.userKey"
          :closable="!disabled"
          size="small"
          @close.stop="removeUser(user)"
        >
          {{ user.displayName || user.userKey }}
        </el-tag>
      </div>
      <span v-else class="controlled-user-selector__placeholder">
        {{ placeholder }}
      </span>
      <el-icon><ArrowDown /></el-icon>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="title"
      width="min(880px, 94vw)"
      top="6vh"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="controlled-user-selector__toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索姓名或用户名"
          @keyup.enter="search"
          @clear="search"
        >
          <template #append>
            <el-button aria-label="搜索人员" title="搜索人员" @click="search">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>

      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        :title="loadError"
        class="controlled-user-selector__error"
      />

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="records"
        :row-key="userKey"
        max-height="390"
        @selection-change="onPageSelectionChange"
        @row-click="onRowClick"
      >
        <el-table-column v-if="multiple" type="selection" width="52" />
        <el-table-column prop="displayName" label="姓名" min-width="180">
          <template #default="{ row }">
            {{ row.displayName || row.username || row.userId }}
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="180" />
        <el-table-column v-if="!multiple" label="操作" width="86">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="selectSingle(row)">
              选择
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="multiple && ordered"
        class="controlled-user-selector__ordered"
      >
        <div class="controlled-user-selector__ordered-title">
          <span>多实例参与人顺序</span>
          <span>按下列顺序保存参与人</span>
        </div>
        <div
          v-if="!draftUsers.length"
          class="controlled-user-selector__ordered-empty"
        >
          请先从候选人员中选择审批人
        </div>
        <template v-else>
          <div
            v-for="(user, index) in draftUsers"
            :key="userKey(user)"
            class="controlled-user-selector__ordered-item"
          >
            <span class="controlled-user-selector__ordered-index">
              {{ index + 1 }}
            </span>
            <span class="controlled-user-selector__ordered-name">
              {{ user.displayName || user.username || user.userId || userKey(user) }}
            </span>
            <el-button
              link
              type="primary"
              :disabled="index === 0"
              @click="moveDraftUser(index, -1)"
            >
              上移
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="index === draftUsers.length - 1"
              @click="moveDraftUser(index, 1)"
            >
              下移
            </el-button>
            <el-button
              link
              type="danger"
              @click="removeDraftUser(index)"
            >
              移除
            </el-button>
          </div>
        </template>
      </div>

      <div class="controlled-user-selector__pagination">
        <span v-if="multiple">已选 {{ draftUsers.length }} 人</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="changePageSize"
          @current-change="changePage"
        />
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="multiple" type="primary" @click="confirmMultiple">
          确认选择（{{ draftUsers.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, Search } from '@element-plus/icons-vue'
import { getNextApproverOptions } from '@/api/processTask'
import {
  createNextApproverOptionsRequestSignature,
  nextApproverUserKey,
  normalizeNextApproverUser,
  normalizeUserKeys,
  reorderNextApproverValues
} from '@/shared/next-approver'

const props = defineProps({
  modelValue: {
    type: [String, Array],
    default: ''
  },
  multiple: {
    type: Boolean,
    default: false
  },
  ordered: {
    type: Boolean,
    default: false
  },
  initialOptions: {
    type: Array,
    default: () => []
  },
  taskId: {
    type: String,
    required: true
  },
  targetNodeId: {
    type: String,
    required: true
  },
  scopeKey: {
    type: String,
    required: true
  },
  action: {
    type: String,
    default: ''
  },
  actionLabel: {
    type: String,
    default: ''
  },
  comment: {
    type: String,
    default: ''
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  placeholder: {
    type: String,
    default: '请选择审批人'
  },
  title: {
    type: String,
    default: '选择审批人'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])
const dialogVisible = ref(false)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])
const knownUsers = ref(new Map())
const draftUsers = ref([])
const tableRef = ref()
const restoringSelection = ref(false)
let optionsRequestGeneration = 0
let activeOptionsRequestSignature = ''
let selectionRestoreGeneration = 0

const modelKeys = computed(() => normalizeUserKeys(
  props.multiple
    ? (Array.isArray(props.modelValue) ? props.modelValue : [])
    : [props.modelValue]
))

const displayUsers = computed(() => modelKeys.value.map(key =>
  knownUsers.value.get(key) || normalizeNextApproverUser({
    username: key,
    displayName: key
  })
))

watch(
  () => props.initialOptions,
  options => rememberUsers(options),
  { immediate: true, deep: true }
)

watch(
  modelKeys,
  keys => {
    if (!dialogVisible.value) return
    draftUsers.value = keys.map(key =>
      knownUsers.value.get(key) || normalizeNextApproverUser(key))
  },
  { immediate: true }
)

watch(
  () => [
    props.taskId,
    props.targetNodeId,
    props.scopeKey,
    props.action,
    props.actionLabel,
    props.comment,
    props.formData
  ],
  () => {
    if (!dialogVisible.value) return
    invalidateOptionsRequests()
    pageNum.value = 1
    records.value = []
    total.value = 0
    void load()
  },
  { deep: true }
)

watch(dialogVisible, visible => {
  if (!visible) invalidateOptionsRequests()
})

function rememberUsers(users = []) {
  const next = new Map(knownUsers.value)
  for (const rawUser of users || []) {
    const user = normalizeNextApproverUser(rawUser)
    if (user.userKey) next.set(user.userKey, user)
  }
  knownUsers.value = next
}

function userKey(user) {
  return nextApproverUserKey(user)
}

async function open() {
  if (props.disabled || !props.scopeKey) return
  draftUsers.value = displayUsers.value.map(user => ({ ...user }))
  keyword.value = ''
  pageNum.value = 1
  loadError.value = ''
  dialogVisible.value = true
  await load()
}

function snapshotFormData() {
  return JSON.parse(JSON.stringify(props.formData || {}))
}

function createOptionsRequest() {
  const body = {
    targetNodeId: props.targetNodeId,
    scopeKey: props.scopeKey,
    action: props.action,
    actionLabel: props.actionLabel || undefined,
    comment: props.comment ?? '',
    formData: snapshotFormData(),
    keyword: keyword.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize.value
  }
  const taskId = props.taskId
  return {
    taskId,
    body,
    signature: createNextApproverOptionsRequestSignature({
      taskId,
      ...body
    })
  }
}

function invalidateOptionsRequests() {
  optionsRequestGeneration += 1
  activeOptionsRequestSignature = ''
  selectionRestoreGeneration += 1
  restoringSelection.value = false
  loading.value = false
}

function isOptionsRequestCurrent(generation, signature) {
  if (
    !dialogVisible.value
    || generation !== optionsRequestGeneration
    || signature !== activeOptionsRequestSignature
  ) {
    return false
  }
  try {
    return createOptionsRequest().signature === signature
  } catch {
    return false
  }
}

async function load() {
  if (!props.taskId || !props.targetNodeId || !props.scopeKey) {
    invalidateOptionsRequests()
    records.value = []
    total.value = 0
    return
  }
  const generation = ++optionsRequestGeneration
  selectionRestoreGeneration += 1
  restoringSelection.value = false
  activeOptionsRequestSignature = ''
  loading.value = true
  loadError.value = ''
  let requestSignature = ''
  try {
    const request = createOptionsRequest()
    requestSignature = request.signature
    activeOptionsRequestSignature = requestSignature
    const result = await getNextApproverOptions(request.taskId, request.body)
    if (!isOptionsRequestCurrent(generation, requestSignature)) return
    const nextRecords = (result?.records || []).map(normalizeNextApproverUser)
      .filter(user => user.userKey)
    records.value = nextRecords
    rememberUsers(records.value)
    total.value = Number(result?.total || 0)
    await restorePageSelection({ generation, signature: requestSignature })
  } catch (error) {
    const stale = requestSignature
      ? !isOptionsRequestCurrent(generation, requestSignature)
      : generation !== optionsRequestGeneration || !dialogVisible.value
    if (stale) {
      return
    }
    records.value = []
    total.value = 0
    loadError.value = error?.message || '加载可选人员失败'
  } finally {
    if (
      generation === optionsRequestGeneration
      && (!requestSignature
        || requestSignature === activeOptionsRequestSignature)
    ) {
      loading.value = false
    }
  }
}

function search() {
  pageNum.value = 1
  load()
}

function changePageSize() {
  pageNum.value = 1
  load()
}

function changePage() {
  load()
}

function onPageSelectionChange(selectedRows) {
  if (!props.multiple || restoringSelection.value) return
  const pageKeys = new Set(records.value.map(userKey))
  const selectedKeys = new Set((selectedRows || []).map(userKey))
  // 候选表只增删人员。已经选中的人员继续保留草稿顺序，避免翻页、搜索或
  // 表格自身排序意外改变串行多实例的办理次序。
  const retained = draftUsers.value.filter(user => {
    const key = userKey(user)
    return !pageKeys.has(key) || selectedKeys.has(key)
  })
  const merged = [...retained]
  const seen = new Set(retained.map(userKey))
  for (const user of selectedRows || []) {
    const key = userKey(user)
    if (!key || seen.has(key)) continue
    seen.add(key)
    merged.push(user)
  }
  draftUsers.value = merged
}

function moveDraftUser(index, offset) {
  draftUsers.value = reorderNextApproverValues(
    draftUsers.value,
    index,
    index + offset
  )
}

function removeDraftUser(index) {
  draftUsers.value = draftUsers.value.filter((_, itemIndex) =>
    itemIndex !== index)
  void restorePageSelection()
}

function onRowClick(row) {
  if (props.multiple) {
    tableRef.value?.toggleRowSelection(row)
    return
  }
  selectSingle(row)
}

function selectSingle(user) {
  rememberUsers([user])
  const key = userKey(user)
  emit('update:modelValue', key)
  emit('change', key, user)
  dialogVisible.value = false
}

function confirmMultiple() {
  rememberUsers(draftUsers.value)
  const keys = normalizeUserKeys(draftUsers.value.map(userKey))
  emit('update:modelValue', keys)
  emit('change', keys, draftUsers.value)
  dialogVisible.value = false
}

function removeUser(user) {
  if (props.disabled) return
  const key = userKey(user)
  const keys = modelKeys.value.filter(value => value !== key)
  const nextValue = props.multiple ? keys : ''
  emit('update:modelValue', nextValue)
  emit('change', nextValue, displayUsers.value.filter(item =>
    userKey(item) !== key))
}

async function restorePageSelection(request = null) {
  if (!props.multiple || !tableRef.value) return
  const restoreGeneration = ++selectionRestoreGeneration
  restoringSelection.value = true
  try {
    await nextTick()
    if (
      restoreGeneration !== selectionRestoreGeneration
      || !dialogVisible.value
      || (request && !isOptionsRequestCurrent(
        request.generation,
        request.signature
      ))
    ) {
      return
    }
    tableRef.value.clearSelection()
    const selectedKeys = new Set(draftUsers.value.map(userKey))
    for (const row of records.value) {
      if (selectedKeys.has(userKey(row))) {
        tableRef.value.toggleRowSelection(row, true)
      }
    }
    await nextTick()
  } finally {
    if (restoreGeneration === selectionRestoreGeneration) {
      restoringSelection.value = false
    }
  }
}
</script>

<style scoped>
.controlled-user-selector {
  width: 100%;
  min-width: 0;
}

.controlled-user-selector__input {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  cursor: pointer;
}

.controlled-user-selector__input:hover {
  border-color: var(--el-color-primary);
}

.controlled-user-selector__input.is-disabled {
  background: var(--el-disabled-bg-color);
  color: var(--el-disabled-text-color);
  cursor: not-allowed;
}

.controlled-user-selector__values {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

.controlled-user-selector__placeholder {
  flex: 1;
  color: var(--el-text-color-placeholder);
}

.controlled-user-selector__toolbar,
.controlled-user-selector__error {
  margin-bottom: 14px;
}

.controlled-user-selector__ordered {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.controlled-user-selector__ordered-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.controlled-user-selector__ordered-title span:last-child,
.controlled-user-selector__ordered-empty {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.controlled-user-selector__ordered-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--el-bg-color);
}

.controlled-user-selector__ordered-index {
  display: inline-flex;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.controlled-user-selector__ordered-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.controlled-user-selector__pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 720px) {
  .controlled-user-selector__pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
