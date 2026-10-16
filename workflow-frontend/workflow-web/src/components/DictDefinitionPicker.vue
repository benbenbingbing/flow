<template>
  <div class="dict-definition-picker">
    <button type="button" class="picker-trigger" @click="openPicker">
      <span v-if="modelValue" class="trigger-content">
        <span class="trigger-primary">{{ selectedDict?.dictName || modelValue }}</span>
        <span v-if="selectedDict?.dictName" class="secondary-text">{{ modelValue }}</span>
      </span>
      <span v-else class="trigger-placeholder">选择系统代码表</span>
      <el-icon><Search /></el-icon>
    </button>

    <el-dialog
      v-model="visible"
      title="选择代码表"
      width="min(880px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="dict-definition-picker-dialog"
    >
      <template #header>
        <div class="dialog-heading">
          <strong>选择代码表</strong>
          <span>按名称或编码搜索，选择后点击确认</span>
        </div>
      </template>

      <el-input v-model="keyword" clearable placeholder="搜索代码表名称或编码" class="search-input">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <PageState
        v-if="error"
        type="error"
        title="代码表加载失败"
        :description="error"
        retryable
        compact
        @retry="emit('retry')"
      />
      <template v-else>
        <el-table
          v-loading="loading"
          :data="pageRows"
          row-key="dictCode"
          border
          stripe
          height="380"
          :empty-text="keyword.trim() ? '未找到匹配的代码表' : '暂无代码表，可返回字段配置新建'"
          @row-click="selectRow"
        >
          <el-table-column label="选择" width="64" align="center">
            <template #default="{ row }">
              <el-radio
                v-model="draftCode"
                :value="row.dictCode"
                :aria-label="`选择代码表 ${row.dictName || row.dictCode}`"
                @click.stop
              ><span /></el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="dictName" label="代码表名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="dictCode" label="代码表编码" min-width="260" show-overflow-tooltip />
        </el-table>
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredOptions.length"
            layout="total, sizes, prev, pager, next"
          />
        </div>
      </template>

      <div class="selection-summary" aria-live="polite">
        <span>已选代码表：</span>
        <span v-if="draftCode" class="selection-value">
          <strong>{{ draftDict?.dictName || draftCode }}</strong>
          <span v-if="draftDict?.dictName" class="secondary-text">{{ draftCode }}</span>
        </span>
        <span v-else class="secondary-text">尚未选择</span>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!draftDict || loading || Boolean(error)" @click="confirmSelection">
          确认选择
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageState from '@/components/PageState.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  options: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'retry'])
const visible = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
// 临时选择独立于字段草稿，跨页和搜索时保留，只有确认后才写回 dictType。
const draftCode = ref('')
const selectedDict = computed(() => props.options.find(item => item.dictCode === props.modelValue))
const draftDict = computed(() => props.options.find(item => item.dictCode === draftCode.value))
const filteredOptions = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return props.options.filter(item => !query
    || [item.dictName, item.dictCode].some(value => String(value || '').toLowerCase().includes(query)))
})
const pageRows = computed(() => filteredOptions.value.slice(
  (pageNum.value - 1) * pageSize.value,
  pageNum.value * pageSize.value
))

watch([keyword, pageSize], () => { pageNum.value = 1 }, { flush: 'sync' })
// 重试或新建后候选项数量可能变少，避免停留在超出范围的空白页。
watch(() => filteredOptions.value.length, total => {
  pageNum.value = Math.min(pageNum.value, Math.max(1, Math.ceil(total / pageSize.value)))
})
watch(() => props.modelValue, () => { visible.value = false })

/** 每次打开都从字段当前值恢复临时选择，并定位到所在页，便于核对已有配置。 */
function openPicker() {
  keyword.value = ''
  draftCode.value = props.modelValue || ''
  visible.value = true
  pageNum.value = Math.max(1, Math.floor(props.options.findIndex(item => item.dictCode === draftCode.value) / pageSize.value) + 1)
}

function selectRow(row) {
  draftCode.value = row.dictCode
}

/** 仅提交仍在候选列表中的代码表编码；字段的持久化沿用页面原有保存流程。 */
function confirmSelection() {
  if (!draftDict.value || props.loading || props.error) return
  emit('update:modelValue', draftCode.value)
  visible.value = false
}
</script>

<style scoped>
.dict-definition-picker {
  width: 100%;
  min-width: 0;
}

.picker-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 40px;
  padding: 6px 11px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.picker-trigger:hover,
.picker-trigger:focus-visible {
  border-color: var(--el-color-primary);
}

.picker-trigger > .el-icon {
  flex-shrink: 0;
}

.trigger-content,
.selection-value {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.5;
}

.trigger-content > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trigger-primary {
  color: var(--el-text-color-primary);
}

.trigger-placeholder {
  color: var(--el-text-color-placeholder);
}

.secondary-text,
.dialog-heading > span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dialog-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dialog-heading > strong {
  font-size: 18px;
}

.search-input {
  margin-bottom: 16px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
  margin-top: 14px;
}

.selection-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.selection-summary > span:first-child {
  flex-shrink: 0;
}

.selection-value {
  overflow-wrap: anywhere;
}
</style>
