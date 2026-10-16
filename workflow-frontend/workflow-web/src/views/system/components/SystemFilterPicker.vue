<template>
  <div class="system-filter-picker">
    <div class="picker-control">
      <button type="button" class="picker-trigger" :aria-label="title" @click="openPicker">
        <span :class="{ 'is-placeholder': !selectedOption }" :title="selectedOption?.path || selectedOption?.label">
          {{ selectedOption?.label || placeholder }}
        </span>
        <el-icon><Search /></el-icon>
      </button>
      <button v-if="modelValue" type="button" class="picker-clear" :aria-label="`清空${title}`" @click="commit('')">
        <el-icon><CircleClose /></el-icon>
      </button>
    </div>

    <el-dialog
      v-model="visible"
      :title="title"
      width="min(960px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="system-filter-picker-dialog"
    >
      <div class="picker-layout">
        <section class="option-section">
          <el-input v-model="keyword" clearable :placeholder="`搜索${entityLabel}名称或编码`" :prefix-icon="Search" class="picker-search" />
          <PageState v-if="error" type="error" title="选项加载失败" :description="error" retryable @retry="emit('retry')" />
          <template v-else>
            <el-scrollbar v-if="tree" v-loading="loading" height="380px">
              <el-tree
                ref="treeRef"
                :data="treeOptions"
                :props="{ label: 'label', children: 'children' }"
                node-key="value"
                :current-node-key="draftValue"
                :default-expanded-keys="expandedKeys"
                highlight-current
                :expand-on-click-node="false"
                :filter-node-method="filterNode"
                empty-text="没有匹配的组织或部门"
                @node-click="selectOption"
              >
                <template #default="{ data }">
                  <div class="tree-option" :title="data.path">
                    <el-radio :model-value="draftValue" :value="data.value" :aria-label="data.label" @click.stop @change="selectOption(data)"><span /></el-radio>
                    <span class="tree-label">{{ data.label }}</span>
                    <el-tag size="small" effect="plain" :type="data.kind === 'dept' ? 'info' : 'primary'">{{ data.kind === 'dept' ? '部门' : '组织' }}</el-tag>
                  </div>
                </template>
              </el-tree>
            </el-scrollbar>
            <template v-else>
              <el-table v-loading="loading" :data="pageOptions" stripe height="380" row-key="value" empty-text="没有匹配的选项" @row-click="selectOption">
                <el-table-column label="选择" width="60" align="center">
                  <template #default="{ row }">
                    <el-radio :model-value="draftValue" :value="row.value" :aria-label="row.label" @click.stop @change="selectOption(row)"><span /></el-radio>
                  </template>
                </el-table-column>
                <el-table-column :label="`${entityLabel}名称`" prop="label" min-width="150" show-overflow-tooltip />
                <el-table-column label="编码" prop="code" min-width="140" show-overflow-tooltip />
                <el-table-column label="描述" prop="description" min-width="150" show-overflow-tooltip />
              </el-table>
              <el-pagination v-model:current-page="pageNum" :page-size="10" :total="filteredOptions.length" layout="total, prev, pager, next" class="picker-pagination" />
            </template>
          </template>
        </section>
        <aside class="selected-section">
          <div class="selected-heading"><strong>已选{{ entityLabel }}</strong><el-tag size="small" effect="plain">{{ draftOption ? 1 : 0 }} 个</el-tag></div>
          <div v-if="draftOption" class="selected-item">
            <div><strong>{{ draftOption.label }}</strong><span>{{ draftOption.path || draftOption.code }}</span></div>
            <el-button text circle :icon="Close" aria-label="移除已选项" @click="draftValue = ''" />
          </div>
          <el-empty v-else description="尚未选择" :image-size="64" />
        </aside>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="loading || !!error" @click="commit(draftValue)">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { CircleClose, Close, Search } from '@element-plus/icons-vue'
import PageState from '@/components/PageState.vue'

interface FilterOption {
  value: string
  label: string
  code?: string
  description?: string
  parentValue?: string
  kind?: string
  path?: string
  children?: FilterOption[]
}

const props = withDefaults(defineProps<{
  modelValue: string
  options: FilterOption[]
  title: string
  entityLabel: string
  placeholder: string
  tree?: boolean
  loading?: boolean
  error?: string
}>(), { tree: false, loading: false, error: '' })
const emit = defineEmits(['update:modelValue', 'retry'])
const visible = ref(false)
const keyword = ref('')
const draftValue = ref('')
const pageNum = ref(1)
const treeRef = ref()
const optionMap = computed(() => new Map(props.options.map(option => [option.value, option])))
const selectedOption = computed(() => optionMap.value.get(props.modelValue))
const draftOption = computed(() => optionMap.value.get(draftValue.value))
const filteredOptions = computed(() => props.options.filter(option => filterNode(keyword.value, option)))
const pageOptions = computed(() => filteredOptions.value.slice((pageNum.value - 1) * 10, pageNum.value * 10))

// 启用列表可能缺少不可见或停用的父节点，此时保留该节点为根，避免遗漏可选部门。
const treeOptions = computed(() => {
  const nodes = new Map(props.options.map(option => [option.value, { ...option, children: [] as FilterOption[] }]))
  const roots: FilterOption[] = []
  nodes.forEach(node => {
    const parent = nodes.get(node.parentValue || '')
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})
const expandedKeys = computed(() => {
  const keys = treeOptions.value.map(option => option.value)
  let current = optionMap.value.get(draftValue.value)
  const visited = new Set<string>()
  while (current?.parentValue && !visited.has(current.parentValue)) {
    visited.add(current.parentValue)
    keys.push(current.parentValue)
    current = optionMap.value.get(current.parentValue)
  }
  return keys
})

/** 搜索只改变候选项；已选草稿跨搜索、分页保留，确认后才写入外部查询条件。 */
function filterNode(query: string, option: FilterOption) {
  return `${option.label} ${option.code || ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())
}

/** 每次打开从已提交条件创建草稿，避免上次取消的选择或搜索词影响本次操作。 */
async function openPicker() {
  draftValue.value = props.modelValue
  keyword.value = ''
  pageNum.value = 1
  visible.value = true
  await nextTick()
  treeRef.value?.setCurrentKey(draftValue.value)
}

function selectOption(option: FilterOption) {
  draftValue.value = option.value
  treeRef.value?.setCurrentKey(option.value)
}

/** 清空与确认均提交单个值；关闭或取消弹窗不影响此前已提交的筛选。 */
function commit(value: string) {
  emit('update:modelValue', value)
  visible.value = false
}

watch(keyword, async () => {
  pageNum.value = 1
  await nextTick()
  treeRef.value?.filter(keyword.value)
})
watch(() => props.options, async () => {
  await nextTick()
  treeRef.value?.filter(keyword.value)
})
</script>

<style scoped>
.system-filter-picker { width: 100%; min-width: 0; }
.picker-control { position: relative; width: 100%; }
.picker-trigger { display: flex; align-items: center; justify-content: space-between; gap: 30px; width: 100%; height: 32px; padding: 0 11px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); color: var(--el-text-color-regular); cursor: pointer; font: inherit; text-align: left; }
.picker-trigger:hover, .picker-trigger:focus-visible { border-color: var(--el-color-primary); }
.picker-trigger > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-trigger > .el-icon { flex-shrink: 0; color: var(--el-text-color-placeholder); }
.is-placeholder { color: var(--el-text-color-placeholder); }
.picker-clear { position: absolute; right: 30px; top: 1px; display: grid; place-items: center; height: 30px; padding: 0 4px; border: 0; background: transparent; color: var(--el-text-color-placeholder); cursor: pointer; }
.picker-clear:hover { color: var(--el-color-primary); }
.picker-layout { display: grid; grid-template-columns: minmax(0, 1fr) 240px; border-top: 1px solid var(--el-border-color-lighter); }
.option-section { min-width: 0; padding: 16px 16px 0 0; }
.picker-search { margin-bottom: 12px; }
.selected-section { padding: 16px 0 0 16px; border-left: 1px solid var(--el-border-color-lighter); min-width: 0; }
.selected-heading, .selected-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.selected-heading { margin-bottom: 16px; }
.selected-item { padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-light); }
.selected-item > div { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.selected-item strong, .selected-item span { overflow-wrap: anywhere; }
.selected-item span { color: var(--el-text-color-secondary); font-size: 12px; }
.picker-pagination { margin-top: 14px; justify-content: flex-end; }
.tree-option { display: flex; min-width: 0; align-items: center; gap: 8px; padding-right: 8px; width: 100%; }
.tree-option .el-radio { margin-right: 0; }
.tree-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
:deep(.el-radio__label) { padding-left: 0; }
:deep(.el-tree-node__content) { height: 36px; }
@media (max-width: 760px) {
  .picker-layout { grid-template-columns: 1fr; }
  .option-section { padding-right: 0; }
  .selected-section { padding: 16px 0 0; margin-top: 16px; border-left: 0; border-top: 1px solid var(--el-border-color-lighter); }
  .selected-section :deep(.el-empty) { padding: 8px; }
}
</style>
