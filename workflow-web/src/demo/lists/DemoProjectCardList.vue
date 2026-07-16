<template>
  <div class="demo-project-list" :class="{ compact: config.compact === true }">
    <el-card shadow="never" class="demo-toolbar">
      <div class="toolbar-row">
        <el-input
          v-model="queryForm[primaryQueryCode]"
          :placeholder="config.searchPlaceholder || '搜索项目名称'"
          clearable
          @keyup.enter="runtime.search()"
        />
        <el-button type="primary" @click="runtime.search()">查询</el-button>
        <el-button @click="runtime.reset()">重置</el-button>
        <div class="toolbar-spacer" />
        <el-button @click="runtime.reload()">刷新</el-button>
        <el-button
          v-if="toolbarCapability('create').visible !== false"
          type="primary"
          :disabled="toolbarCapability('create').enabled === false"
          :title="toolbarCapability('create').reason || ''"
          @click="runtime.create()"
        >新增项目</el-button>
      </div>
    </el-card>

    <div v-loading="tableLoading" class="demo-card-grid" :style="gridStyle">
      <el-card
        v-for="row in dataList"
        :key="row.id"
        shadow="hover"
        class="project-card"
      >
        <template #header>
          <div class="card-header">
            <div>
              <strong>{{ getValue(row, 'projectName') || row.name || row.title || '-' }}</strong>
              <div class="project-code">{{ row.code || getValue(row, 'projectCode') || row.dataNo }}</div>
            </div>
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) || row.status }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="1" size="small">
          <el-descriptions-item label="负责人">
            {{ getValue(row, 'ownerName') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预算">
            {{ formatBudget(getValue(row, 'budget')) }}
          </el-descriptions-item>
          <el-descriptions-item label="风险">
            <DemoRiskProgressCell
              :value="getValue(row, 'riskScore')"
              :row="row"
              :config="{ showText: true, showLevel: true }"
            />
          </el-descriptions-item>
        </el-descriptions>

        <p v-if="config.showDescription !== false" class="project-description">
          {{ getValue(row, 'description') || '暂无项目说明' }}
        </p>

        <div class="card-actions">
          <el-button
            v-if="isVisible(row, 'view')"
            link
            type="primary"
            @click="runtime.view(row)"
          >查看</el-button>
          <el-button
            v-if="isVisible(row, 'edit')"
            link
            type="primary"
            :disabled="!runtime.canAction(row, 'edit')"
            :title="runtime.getActionReason(row, 'edit')"
            @click="runtime.edit(row)"
          >编辑</el-button>
          <el-button
            v-if="isVisible(row, 'approve')"
            link
            type="warning"
            :disabled="!runtime.canAction(row, 'approve')"
            :title="runtime.getActionReason(row, 'approve')"
            @click="runtime.approve(row)"
          >审批</el-button>
          <el-button
            v-if="isVisible(row, 'delete')"
            link
            type="danger"
            :disabled="!runtime.canAction(row, 'delete')"
            :title="runtime.getActionReason(row, 'delete')"
            @click="runtime.delete(row)"
          >删除</el-button>
        </div>
      </el-card>
    </div>

    <el-empty v-if="!tableLoading && dataList.length === 0" description="暂无项目数据" />

    <div class="demo-pagination">
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
import DemoRiskProgressCell from '../list-fields/DemoRiskProgressCell.vue'

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

const primaryQueryCode = computed(() =>
  props.queryFields.find(field => ['projectName', 'name'].includes(field.fieldCode))?.fieldCode
  || props.queryFields[0]?.fieldCode
  || 'projectName'
)

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${Math.min(4, Math.max(1, Number(props.config.columns) || 3))}, minmax(260px, 1fr))`
}))

function getValue(row, fieldCode) {
  return row?.extData?.[fieldCode] ?? row?.data?.[fieldCode] ?? row?.[fieldCode]
}

function formatBudget(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isNaN(number)
    ? value
    : number.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function isVisible(row, action) {
  return row?.actionCapabilities?.[action]?.visible !== false
}

function toolbarCapability(action) {
  return props.listConfig?.toolbarCapabilities?.[action] || {
    visible: true,
    enabled: true,
    reason: ''
  }
}
</script>

<style scoped>
.demo-project-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar-row,
.card-header,
.card-actions,
.demo-pagination {
  display: flex;
  align-items: center;
}

.toolbar-row {
  gap: 8px;
}

.toolbar-row .el-input {
  width: 280px;
}

.toolbar-spacer {
  flex: 1;
}

.demo-card-grid {
  display: grid;
  gap: 14px;
}

.card-header {
  justify-content: space-between;
  gap: 12px;
}

.card-header > div {
  flex: 1;
  min-width: 0;
}

.card-header strong,
.project-code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-header strong {
  line-height: 1.45;
}

.project-code {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.card-header .el-tag {
  flex: 0 0 auto;
}

.project-description {
  min-height: 42px;
  margin: 12px 0;
  color: #606266;
  line-height: 1.6;
}

.card-actions {
  justify-content: flex-end;
  border-top: 1px solid #ebeef5;
  padding-top: 10px;
}

.demo-pagination {
  justify-content: flex-end;
}

.compact .project-description {
  display: none;
}

@media (max-width: 1100px) {
  .demo-card-grid {
    grid-template-columns: repeat(2, minmax(260px, 1fr)) !important;
  }
}

@media (max-width: 720px) {
  .toolbar-row {
    flex-wrap: wrap;
  }

  .toolbar-spacer {
    display: none;
  }

  .demo-card-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
