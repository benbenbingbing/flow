<template>
  <div class="management-navigation-tree">
    <el-input
      v-if="searchable"
      v-model="searchInput"
      :placeholder="searchPlaceholder"
      clearable
      @keyup.enter="search"
      @clear="search"
    >
      <template #append><el-button @click="search">搜索</el-button></template>
    </el-input>
    <div v-if="error" class="tree-error" role="alert">
      {{ error }}
      <el-button link type="primary" @click="reload">重新加载</el-button>
    </div>
    <div v-loading="searchLoading" class="tree-scroll">
      <el-tree
        v-if="!keyword"
        :key="treeVersion"
        ref="treeRef"
        node-key="id"
        :data="roots"
        :props="{ label: labelKey, isLeaf, disabled: 'selectionDisabled' }"
        lazy
        :load="loadChildren"
        :default-expanded-keys="['0']"
        :expand-on-click-node="false"
        highlight-current
        @node-click="selectNode"
      >
        <template #default="{ data }">
          <el-tooltip :content="data.displayPath || data[labelKey]" :show-after="400">
            <span :class="{ 'load-more': data.more }">{{ data[labelKey] }}</span>
          </el-tooltip>
        </template>
      </el-tree>
      <template v-else>
        <el-button link @click="clearSearch">返回树形导航</el-button>
        <el-empty v-if="!searchLoading && !searchRows.length" :description="emptyDescription" :image-size="60" />
        <button v-for="row in searchRows" :key="row.id" class="search-result" :disabled="row.selectionDisabled" @click="emit('select', row)">
          <span>{{ row[labelKey] }}</span>
          <small>{{ row.displayPath }}</small>
        </button>
        <el-pagination
          v-if="searchTotal > searchPageSize"
          small
          layout="prev, pager, next"
          :total="searchTotal"
          :page-size="searchPageSize"
          :current-page="searchPage"
          @current-change="loadSearch"
        />
      </template>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '0' },
  pageLoader: { type: Function, required: true },
  pathLoader: { type: Function, required: true },
  labelKey: { type: String, required: true },
  rootLabel: { type: String, required: true },
  searchPlaceholder: { type: String, default: '搜索名称' },
  emptyDescription: { type: String, default: '暂无匹配节点' },
  parentCandidates: Boolean,
  searchable: Boolean,
  selectableRoot: { type: Boolean, default: true },
  type: { type: String, default: '' },
  excludeId: { type: String, default: '' }
})
const emit = defineEmits(['select'])
const roots = [{ id: '0', [props.labelKey]: props.rootLabel, hasChildren: true }]
const treeRef = ref(null)
const treeVersion = ref(0)
const error = ref('')
const searchInput = ref('')
const keyword = ref('')
const searchRows = ref([])
const searchPage = ref(1)
const searchTotal = ref(0)
const searchLoading = ref(false)
const searchPageSize = 20
const branchPageSize = 50
const pendingLoads = new Map()
const loadingMore = new Set()
let searchSequence = 0
let revealSequence = 0

function isLeaf(data) {
  return !!data.more || data.selectionDisabled || !data.hasChildren
}

function pageParams(parentId, pageNum, pageSize) {
  return { parentId, pageNum, pageSize, type: props.type, excludeId: props.excludeId || undefined, parentCandidates: props.parentCandidates || undefined }
}

function moreNode(parentId, pageNum) {
  return { id: `__more__${parentId}`, [props.labelKey]: '加载更多…', more: true, parentId, pageNum }
}

/** 每次展开仅取直属下级的一页，加载更多作为同级操作项，保持已展开分支。 */
function loadChildren(node, resolve, reject) {
  if (node.level === 0) return resolve(roots)
  const parentId = node.data.id
  const version = treeVersion.value
  const pending = props.pageLoader(pageParams(parentId, 1, branchPageSize))
    .then(page => {
      if (version !== treeVersion.value) return
      const rows = [...page.records]
      if (rows.length < page.total) rows.push(moreNode(parentId, 2))
      resolve(rows)
    })
    .catch(e => {
      if (version === treeVersion.value) error.value = e?.message || '导航树加载失败'
      // 失败节点保持未加载状态，收起后可重新展开重试。
      reject()
    })
    .finally(() => {
      if (pendingLoads.get(parentId) === pending) pendingLoads.delete(parentId)
    })
  pendingLoads.set(parentId, pending)
}

async function loadMore(data) {
  if (loadingMore.has(data.parentId)) return
  loadingMore.add(data.parentId)
  const version = treeVersion.value
  try {
    const page = await props.pageLoader(pageParams(data.parentId, data.pageNum, branchPageSize))
    if (version !== treeVersion.value) return
    treeRef.value.remove(data)
    for (const row of page.records) {
      // 定位搜索结果时可能已插入不在首批中的祖先，后续翻页按 ID 去重。
      if (!treeRef.value.getNode(row.id)) treeRef.value.append(row, data.parentId)
    }
    if (data.pageNum * branchPageSize < page.total) {
      treeRef.value.append(moreNode(data.parentId, data.pageNum + 1), data.parentId)
    }
    error.value = ''
  } catch (e) {
    error.value = e?.message || '加载更多失败，请重试'
  } finally {
    loadingMore.delete(data.parentId)
    if (version === treeVersion.value) treeRef.value?.setCurrentKey(props.modelValue)
  }
}

function selectNode(data) {
  if (data.more) return loadMore(data)
  if (data.selectionDisabled) return treeRef.value.setCurrentKey(props.modelValue)
  if (data.id !== '0' || props.selectableRoot) emit('select', data)
  else treeRef.value.setCurrentKey(props.modelValue)
}

/** 补齐目标父链，即使目标位于尚未翻到的同级页也能直接定位。 */
async function reveal(id = props.modelValue) {
  const sequence = ++revealSequence
  try {
    const path = id && id !== '0' ? await props.pathLoader(id) : []
    await nextTick()
    if (sequence !== revealSequence || keyword.value || !treeRef.value) return
    let parentId = '0'
    for (const row of path) {
      const parent = treeRef.value.getNode(parentId)
      if (!parent) return
      if (pendingLoads.has(parentId)) await pendingLoads.get(parentId)
      else if (!parent.loaded) {
        parent.expand()
        await pendingLoads.get(parentId)
      }
      if (sequence !== revealSequence) return
      if (!parent.loaded) return
      parent.expanded = true
      if (!treeRef.value.getNode(row.id)) treeRef.value.append(row, parentId)
      parentId = row.id
    }
    treeRef.value.setCurrentKey(id || '0')
    await nextTick()
    treeRef.value.$el.querySelector('.is-current')?.scrollIntoView({ block: 'nearest' })
  } catch (e) {
    if (sequence === revealSequence) error.value = e?.message || '节点定位失败'
  }
}

async function loadSearch(pageNum = 1) {
  const sequence = ++searchSequence
  searchPage.value = pageNum
  searchLoading.value = true
  searchRows.value = []
  searchTotal.value = 0
  error.value = ''
  try {
    const page = await props.pageLoader({ ...pageParams('0', pageNum, searchPageSize), keyword: keyword.value })
    if (sequence !== searchSequence) return
    searchRows.value = page.records
    searchTotal.value = page.total
  } catch (e) {
    if (sequence === searchSequence) error.value = e?.message || '搜索失败'
  } finally {
    if (sequence === searchSequence) searchLoading.value = false
  }
}

function search() {
  keyword.value = searchInput.value.trim()
  if (keyword.value) loadSearch(1)
  else reload()
}

function clearSearch() {
  searchInput.value = ''
  search()
}

async function reload() {
  ++searchSequence
  ++revealSequence
  searchLoading.value = false
  error.value = ''
  if (keyword.value) return loadSearch(searchPage.value)
  treeVersion.value++
  pendingLoads.clear()
  await nextTick()
  await reveal()
}

watch(() => props.modelValue, () => reveal(), { immediate: true })
watch(() => [props.type, props.excludeId], reload)
defineExpose({ reload, reveal })
</script>

<style scoped>
.management-navigation-tree {
  min-width: 0;
}

.tree-scroll {
  max-height: 62vh;
  overflow: auto;
  margin-top: 10px;
}

.tree-error {
  color: var(--el-color-danger);
  font-size: 13px;
  margin: 8px 0;
}

.load-more {
  color: var(--el-color-primary);
}

.search-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px;
  background: transparent;
  border: 0;
  text-align: left;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.search-result:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.search-result:hover {
  background: var(--el-fill-color-light);
}

.search-result small {
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

</style>
