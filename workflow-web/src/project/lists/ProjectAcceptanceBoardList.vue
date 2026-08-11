<template>
  <div class="project-acceptance-board">
    <div class="board-toolbar">
      <el-input
        v-model="queryForm[searchField]"
        :placeholder="config.searchPlaceholder || '搜索验收单名称'"
        clearable
        @keyup.enter="runtime.search()"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="runtime.search()">查询</el-button>
      <el-button @click="runtime.reset()">重置</el-button>
      <span class="toolbar-spacer" />
      <el-button @click="runtime.reload()">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
      <el-button type="primary" @click="runtime.create()">
        <el-icon><Plus /></el-icon>
        新增验收单
      </el-button>
    </div>

    <div v-loading="tableLoading" class="board-table">
      <div class="board-header">
        <span>验收单</span>
        <span>场景</span>
        <span>评分</span>
        <span>状态</span>
        <span>扩展轨迹</span>
        <span>操作</span>
      </div>
      <div
        v-for="row in dataList"
        :key="row.id"
        class="board-row"
      >
        <div class="identity-cell">
          <strong>{{ valueOf(row, 'name') || row.name || '-' }}</strong>
          <small>{{ row.dataNo || row.code || row.id }}</small>
        </div>
        <span>{{ sceneLabel(valueOf(row, 'acceptance_scene')) }}</span>
        <div class="score-cell">
          <el-progress
            :percentage="scoreOf(row)"
            :stroke-width="8"
            :show-text="false"
            :status="scoreOf(row) >= 60 ? 'success' : 'exception'"
          />
          <strong>{{ scoreOf(row) }}</strong>
        </div>
        <el-tag :type="getStatusType(row.status)">
          {{ getStatusText(row.status) || row.status || '-' }}
        </el-tag>
        <span class="trace-cell">
          {{ config.showProviderTrace === false
            ? '-'
            : valueOf(row, 'provider_trace') || valueOf(row, 'provider_column') || '等待执行' }}
        </span>
        <div class="row-actions">
          <el-button link type="primary" @click="runtime.view(row)">查看</el-button>
          <el-button
            link
            type="primary"
            :disabled="!runtime.canAction(row, 'edit')"
            :title="runtime.getActionReason(row, 'edit')"
            @click="runtime.edit(row)"
          >编辑</el-button>
          <el-button
            link
            type="warning"
            :disabled="!runtime.canAction(row, 'approve')"
            :title="runtime.getActionReason(row, 'approve')"
            @click="runtime.approve(row)"
          >审批</el-button>
        </div>
      </div>
      <el-empty
        v-if="!tableLoading && dataList.length === 0"
        description="暂无验收数据"
      />
    </div>

    <div class="board-pagination">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="runtime.viewConfig?.pagination?.pageSizes || [10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="value => emit('sizeChange', value)"
        @current-change="value => emit('pageChange', value)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

const props = defineProps({
  entityCode: String,
  entityDefinition: { type: Object, default: () => ({}) },
  entityName: String,
  listConfig: { type: Object, default: () => ({}) },
  listConfigFields: { type: Array, default: () => [] },
  listFields: { type: Array, default: () => [] },
  queryFields: { type: Array, default: () => [] },
  queryForm: { type: Object, default: () => ({}) },
  dataList: { type: Array, default: () => [] },
  loading: Boolean,
  tableLoading: Boolean,
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  config: { type: Object, default: () => ({}) },
  runtime: { type: Object, required: true },
  getStatusType: { type: Function, default: () => '' },
  getStatusText: { type: Function, default: value => value }
})

const emit = defineEmits(['sizeChange', 'pageChange'])
const searchField = computed(() =>
  props.queryFields.find(field => field.fieldCode === 'name')?.fieldCode
  || props.queryFields[0]?.fieldCode
  || 'name'
)

function valueOf(row, fieldCode) {
  return row?.extData?.[fieldCode]
    ?? row?.data?.[fieldCode]
    ?? row?.[fieldCode]
}

function scoreOf(row) {
  const value = Number(valueOf(row, 'acceptance_score') || 0)
  return Math.max(0, Math.min(100, Number.isFinite(value) ? value : 0))
}

function sceneLabel(value) {
  return {
    FULL_EXTENSION: '全扩展链路',
    FORM_EXTENSION: '表单扩展',
    LIST_EXTENSION: '列表扩展',
    PROCESS_EXTENSION: '流程扩展'
  }[value] || value || '-'
}
</script>

<style scoped>
.project-acceptance-board {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.board-toolbar,
.board-pagination,
.score-cell,
.row-actions {
  display: flex;
  align-items: center;
}

.board-toolbar {
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.board-toolbar .el-input {
  width: 280px;
}

.toolbar-spacer {
  flex: 1;
}

.board-table {
  min-height: 180px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
}

.board-header,
.board-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) minmax(120px, 1fr) minmax(120px, 1fr) 110px minmax(180px, 1.4fr) 150px;
  gap: 14px;
  align-items: center;
  padding: 12px 14px;
}

.board-header {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 600;
}

.board-row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.board-row:hover {
  background: var(--el-fill-color-lighter);
}

.identity-cell,
.trace-cell {
  min-width: 0;
}

.identity-cell strong,
.identity-cell small,
.trace-cell {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-cell small {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.score-cell {
  gap: 8px;
}

.score-cell .el-progress {
  flex: 1;
}

.row-actions {
  justify-content: flex-end;
}

.board-pagination {
  justify-content: flex-end;
}

@media (max-width: 1000px) {
  .board-table {
    overflow-x: auto;
  }

  .board-header,
  .board-row {
    min-width: 980px;
  }
}

@media (max-width: 720px) {
  .board-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .board-toolbar .el-input {
    width: 100%;
  }

  .toolbar-spacer {
    display: none;
  }
}
</style>
