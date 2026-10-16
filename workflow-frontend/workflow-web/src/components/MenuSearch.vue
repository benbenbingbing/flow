<template>
  <el-select
    ref="searchSelect"
    v-model="selectedPath"
    class="menu-search"
    filterable
    default-first-option
    :filter-method="filterMenus"
    :loading="loading"
    loading-text="加载菜单中…"
    placeholder="搜索菜单"
    aria-label="搜索菜单"
    no-match-text="未找到匹配菜单"
    :no-data-text="keyword.trim() ? '未找到匹配菜单' : '暂无可访问菜单'"
    popper-class="menu-search-popper"
    :fit-input-width="false"
    @change="navigateToMenu"
    @visible-change="handleVisibleChange"
  >
    <template #prefix>
      <el-icon><Search /></el-icon>
    </template>
    <el-option
      v-for="option in filteredOptions"
      :key="option.path"
      :value="option.path"
      :label="option.label"
      class="menu-search-option"
    >
      <span class="menu-search-name">{{ option.menuName }}</span>
      <span v-if="option.parentLabel" class="menu-search-parent">{{ option.parentLabel }}</span>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { buildMenuSearchOptions, filterMenuSearchOptions } from '@/utils/menuSearch'

const props = defineProps({
  menus: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const router = useRouter()
const searchSelect = ref(null)
const selectedPath = ref()
const keyword = ref('')
// 始终从当前侧栏计算，角色授权或菜单刷新后不保留旧搜索索引。
const options = computed(() => buildMenuSearchOptions(props.menus))
const filteredOptions = computed(() => filterMenuSearchOptions(options.value, keyword.value))

const filterMenus = value => {
  keyword.value = value
}

const resetSearch = () => {
  keyword.value = ''
  selectedPath.value = undefined
}

const handleVisibleChange = visible => {
  // 首次输入也会展开下拉框，此时保留已经输入的关键字，仅在关闭时重置。
  if (!visible) resetSearch()
}

/**
 * 选择后沿用站内路由守卫；菜单刷新期间及已从当前授权菜单移除的旧选项不可跳转。
 * 清空选择并移走输入焦点，确保下次搜索（包括当前页）仍能正常触发。
 */
const navigateToMenu = path => {
  if (props.loading || !options.value.some(option => option.path === path)) return
  resetSearch()
  searchSelect.value?.blur()
  router.push(path)
}
</script>

<style scoped>
.menu-search {
  width: 220px;
}

.menu-search-option {
  display: flex;
  height: auto;
  min-height: 48px;
  flex-direction: column;
  justify-content: center;
  padding-top: 7px;
  padding-bottom: 7px;
  line-height: 20px;
}

.menu-search-name,
.menu-search-parent {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-search-parent {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:global(.menu-search-popper) {
  max-width: calc(100vw - 24px);
}

:global(.el-select-dropdown.menu-search-popper) {
  width: min(360px, calc(100vw - 24px));
}

@media (max-width: 760px) {
  .menu-search {
    width: clamp(132px, 36vw, 220px);
  }
}
</style>
