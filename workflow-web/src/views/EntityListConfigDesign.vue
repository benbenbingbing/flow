<template>
  <div class="entity-list-config-design">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span>列表配置设计：{{ configInfo.listName }}</span>
        <el-tag size="small" type="info">{{ entityName }}</el-tag>
      </div>
      <el-button type="primary" @click="handleSave" :loading="saving">
        <el-icon><Check /></el-icon>保存配置
      </el-button>
    </div>

    <div class="design-container">
      <!-- 左侧：配置区 -->
      <div class="config-panel">
        <el-card shadow="never">
          <template #header>
            <span>字段配置</span>
            <el-text type="info" size="small">拖拽排序，勾选控制显示和查询</el-text>
          </template>

          <!-- 自定义列表组件配置 -->
          <el-form :model="configInfo" inline size="small" style="margin-bottom: 12px;">
            <el-form-item label="自定义列表组件">
              <el-input
                v-model="configInfo.customComponent"
                placeholder="输入已注册的自定义列表组件名，留空使用默认渲染"
                style="width: 360px"
                clearable
              />
            </el-form-item>
          </el-form>

          <el-tabs v-model="activeConfigTab" type="border-card" class="config-tabs">
            <el-tab-pane label="字段配置" name="fields">
              <el-table
                :data="fieldConfigList"
                row-key="fieldId"
                class="field-config-table"
                size="small"
                border
              >
                <el-table-column label="排序" width="40" align="center">
                  <template #default>
                    <el-icon class="drag-handle"><Rank /></el-icon>
                  </template>
                </el-table-column>
                <el-table-column label="字段名称" min-width="100">
                  <template #default="{ row }">
                    <el-input v-model="row.fieldName" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="加入列表" width="80" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.showInList" />
                  </template>
                </el-table-column>
                <el-table-column label="查询条件" width="80" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.isQuery" :disabled="!row.showInList" />
                  </template>
                </el-table-column>
                <el-table-column label="查询方式" width="100">
                  <template #default="{ row }">
                    <el-select v-model="row.queryType" size="small" :disabled="!row.isQuery || !row.showInList">
                      <el-option label="等于" value="EQ" />
                      <el-option label="不等于" value="NE" />
                      <el-option label="包含" value="LIKE" />
                      <el-option label="大于" value="GT" />
                      <el-option label="小于" value="LT" />
                      <el-option label="范围" value="BETWEEN" />
                      <el-option label="包含于" value="IN" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="数据源" width="110">
                  <template #default="{ row }">
                    <el-select v-model="row.dataSourceType" size="small" :disabled="!row.showInList">
                      <el-option label="实体字段" value="ENTITY_FIELD" />
                      <el-option label="关联查询" value="REFERENCE" />
                      <el-option label="聚合统计" value="AGGREGATE" />
                      <el-option label="自定义" value="CUSTOM_PROVIDER" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="渲染组件" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.renderComponent" size="small" :disabled="!row.showInList" clearable placeholder="默认">
                      <el-option label="默认文本" value="DefaultText" />
                      <el-option label="状态标签" value="StatusBadge" />
                      <el-option label="日期格式" value="DateFormatter" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="宽度" width="80">
                  <template #default="{ row }">
                    <el-input-number v-model="row.width" :min="0" :max="500" size="small" controls-position="right" :disabled="!row.showInList" />
                  </template>
                </el-table-column>
                <el-table-column label="对齐" width="90">
                  <template #default="{ row }">
                    <el-select v-model="row.align" size="small" :disabled="!row.showInList">
                      <el-option label="左对齐" value="left" />
                      <el-option label="居中" value="center" />
                      <el-option label="右对齐" value="right" />
                    </el-select>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="工具栏按钮" name="toolbar">
              <ListButtonConfigPanel
                type="toolbar"
                v-model="toolbarButtons"
                :entityCode="entityCode"
                :entityFields="entityFields"
              />
            </el-tab-pane>
            <el-tab-pane label="操作列按钮" name="rowActions">
              <ListButtonConfigPanel
                type="row"
                v-model="rowActionButtons"
                :entityCode="entityCode"
                :entityFields="entityFields"
              />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </div>

      <!-- 右侧：预览区 -->
      <div class="preview-panel">
        <el-card shadow="never">
          <template #header>
            <span>预览</span>
            <el-text type="info" size="small">实时预览列表效果</el-text>
          </template>

          <!-- 查询条件 -->
          <div class="preview-query" v-if="previewQueryFields.length > 0">
            <el-form :model="previewQueryForm" inline size="small">
              <el-form-item
                v-for="field in previewQueryFields"
                :key="field.fieldCode"
                :label="field.fieldName"
              >
                <!-- BETWEEN 范围查询 -->
                <template v-if="field.queryType === 'BETWEEN'">
                  <el-date-picker
                    v-if="field.fieldType === 'DATE'"
                    v-model="previewQueryForm[field.fieldCode + '_start']"
                    type="date"
                    :placeholder="`开始${field.fieldName}`"
                    style="width: 130px"
                  />
                  <el-input
                    v-else
                    v-model="previewQueryForm[field.fieldCode + '_start']"
                    :placeholder="`开始${field.fieldName}`"
                    clearable
                    style="width: 130px"
                  />
                  <span style="margin: 0 4px">~</span>
                  <el-date-picker
                    v-if="field.fieldType === 'DATE'"
                    v-model="previewQueryForm[field.fieldCode + '_end']"
                    type="date"
                    :placeholder="`结束${field.fieldName}`"
                    style="width: 130px"
                  />
                  <el-input
                    v-else
                    v-model="previewQueryForm[field.fieldCode + '_end']"
                    :placeholder="`结束${field.fieldName}`"
                    clearable
                    style="width: 130px"
                  />
                </template>
                <!-- 普通查询 -->
                <template v-else>
                  <el-input
                    v-if="field.fieldType === 'STRING' || field.fieldType === 'TEXT'"
                    v-model="previewQueryForm[field.fieldCode]"
                    :placeholder="`请输入${field.fieldName}`"
                    clearable
                  />
                  <el-select
                    v-else-if="field.fieldType === 'SELECT'"
                    v-model="previewQueryForm[field.fieldCode]"
                    :placeholder="`请选择${field.fieldName}`"
                    clearable
                  >
                    <el-option
                      v-for="opt in parseOptions(field.optionsJson)"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                  <el-date-picker
                    v-else-if="field.fieldType === 'DATE'"
                    v-model="previewQueryForm[field.fieldCode]"
                    type="date"
                    :placeholder="`选择${field.fieldName}`"
                  />
                  <el-input
                    v-else
                    v-model="previewQueryForm[field.fieldCode]"
                    :placeholder="`请输入${field.fieldName}`"
                    clearable
                  />
                </template>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handlePreviewSearch">查询</el-button>
                <el-button @click="handlePreviewReset">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 数据表格 -->
          <el-table :data="previewDataList" v-loading="previewLoading" stripe size="small" border>
            <el-table-column type="index" width="50" />
            <el-table-column
              v-for="field in previewListFields"
              :key="field.fieldCode"
              :label="field.fieldName"
              :width="field.width > 0 ? field.width : undefined"
              :align="field.align"
              min-width="100"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <ListCellRenderer
                  v-if="field.renderComponent || (field.dataSourceType && field.dataSourceType !== 'ENTITY_FIELD')"
                  :row="row"
                  :field="field"
                />
                <span v-else>{{ row.data?.[field.fieldCode] ?? row[field.fieldCode] ?? '-' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="preview-pagination" v-if="previewTotal > 0">
            <el-pagination
              v-model:current-page="previewPageNum"
              v-model:page-size="previewPageSize"
              :total="previewTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadPreviewData"
              @current-change="loadPreviewData"
              small
            />
          </div>

          <el-empty v-if="!previewLoading && previewDataList.length === 0" description="暂无数据" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityApi, entityDataApi } from '@/api/entity'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import ListButtonConfigPanel from '@/components/ListButtonConfigPanel.vue'

const route = useRoute()
const router = useRouter()
const configId = route.params.id

// 配置信息
const configInfo = ref({})
const entityName = ref('')
const entityCode = ref('')
const entityId = ref('')
const entityFields = ref([])
const saving = ref(false)

// 字段配置列表
const fieldConfigList = ref([])
let sortableInstance = null

// 配置 Tab
const activeConfigTab = ref('fields')

// 工具栏按钮配置
const toolbarButtons = ref([])

// 操作列按钮配置
const rowActionButtons = ref([])

// 预览相关
const previewQueryForm = ref({})
const previewDataList = ref([])
const previewAllData = ref([])
const previewLoading = ref(false)
const previewPageNum = ref(1)
const previewPageSize = ref(10)
const previewTotal = ref(0)

const previewQueryFields = computed(() => {
  return fieldConfigList.value
    .filter(f => f.showInList && f.isQuery)
    .map(f => {
      const originField = entityFields.value.find(ef => ef.id === f.fieldId)
      return { ...f, fieldType: originField?.fieldType || 'STRING', optionsJson: originField?.optionsJson }
    })
})

const previewListFields = computed(() => {
  return fieldConfigList.value.filter(f => f.showInList)
})

onMounted(() => {
  loadData()
})

async function loadData() {
  try {
    // 加载列表配置
    const configRes = await entityListConfigApi.getById(configId)
    if (configRes) {
      configInfo.value = configRes
      entityId.value = configRes.entityId
      entityCode.value = configRes.entityCode
    }

    // 加载实体信息
    const entityRes = await entityApi.getById(entityId.value)
    if (entityRes) {
      entityName.value = entityRes.entityName
      entityCode.value = entityRes.entityCode
      entityFields.value = entityRes.fields || []
    }

    // 合并字段配置
    mergeFieldConfig(configRes?.fields || [])

    // 解析按钮配置
    parseButtonConfig(configRes)

    // 初始化拖拽
    nextTick(() => {
      initSortable()
    })

    // 加载预览数据
    loadPreviewData()
  } catch (e) {
    console.error('加载数据失败:', e)
    ElMessage.error('加载数据失败')
  }
}

function mergeFieldConfig(savedFields) {
  // 以实体字段为基准
  const merged = entityFields.value.map((ef, index) => {
    const saved = savedFields.find(sf => sf.fieldId === ef.id)
    return {
      fieldId: ef.id,
      fieldCode: ef.fieldCode,
      fieldName: saved?.fieldName || ef.fieldName,
      fieldType: ef.fieldType,
      optionsJson: ef.optionsJson,
      showInList: saved ? saved.showInList : ef.showInList,
      isQuery: saved ? saved.isQuery : ef.isQuery,
      queryType: saved?.queryType || 'LIKE',
      width: saved?.width || 0,
      align: saved?.align || 'left',
      dataSourceType: saved?.dataSourceType || 'ENTITY_FIELD',
      dataSourceConfig: saved?.dataSourceConfig || '',
      renderComponent: saved?.renderComponent || '',
      formatter: saved?.formatter || '',
      sortOrder: saved?.sortOrder ?? index
    }
  })

  // 按 sortOrder 排序
  merged.sort((a, b) => a.sortOrder - b.sortOrder)
  fieldConfigList.value = merged
}

const DEFAULT_TOOLBAR_BUTTONS = [
  { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true, perm: '' },
  { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true, perm: '' },
  { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true, perm: '' },
  { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true, perm: '' }
]

const DEFAULT_ROW_ACTION_BUTTONS = [
  { key: 'view', type: 'built-in', label: '查看', buttonType: 'primary', link: true, sort: 1, enabled: true, perm: '' },
  { key: 'edit', type: 'built-in', label: '编辑', buttonType: 'primary', link: true, sort: 2, enabled: true, perm: '' },
  { key: 'approve', type: 'built-in', label: '审批', buttonType: 'warning', link: true, sort: 3, enabled: true, perm: '' },
  { key: 'delete', type: 'built-in', label: '删除', buttonType: 'danger', link: true, sort: 4, enabled: true, perm: '' }
]

function safeJsonParse(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

function parseButtonConfig(configRes) {
  const toolbar = safeJsonParse(configRes?.toolbarConfig)
  toolbarButtons.value = toolbar && toolbar.length > 0 ? toolbar : DEFAULT_TOOLBAR_BUTTONS.map(b => ({ ...b }))

  const rowActions = safeJsonParse(configRes?.rowActionConfig)
  rowActionButtons.value = rowActions && rowActions.length > 0 ? rowActions : DEFAULT_ROW_ACTION_BUTTONS.map(b => ({ ...b }))
}

function initSortable() {
  const tableEl = document.querySelector('.field-config-table .el-table__body-wrapper tbody')
  if (!tableEl) return

  if (sortableInstance) {
    sortableInstance.destroy()
  }

  sortableInstance = new Sortable(tableEl, {
    handle: '.drag-handle',
    animation: 150,
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex === newIndex) return

      const item = fieldConfigList.value.splice(oldIndex, 1)[0]
      fieldConfigList.value.splice(newIndex, 0, item)
    }
  })
}

async function handleSave() {
  const fields = fieldConfigList.value.map((f, index) => ({
    fieldId: f.fieldId,
    fieldCode: f.fieldCode,
    fieldName: f.fieldName,
    showInList: f.showInList,
    isQuery: f.isQuery,
    queryType: f.queryType,
    width: f.width,
    align: f.align,
    dataSourceType: f.dataSourceType || 'ENTITY_FIELD',
    dataSourceConfig: f.dataSourceConfig || '',
    renderComponent: f.renderComponent || '',
    formatter: f.formatter || '',
    sortOrder: index
  }))

  const dto = {
    id: configInfo.value.id,
    entityId: configInfo.value.entityId,
    entityCode: configInfo.value.entityCode,
    listKey: configInfo.value.listKey,
    listName: configInfo.value.listName,
    description: configInfo.value.description,
    isDefault: configInfo.value.isDefault,
    customComponent: configInfo.value.customComponent,
    toolbarConfig: JSON.stringify(toolbarButtons.value),
    rowActionConfig: JSON.stringify(rowActionButtons.value),
    fields
  }

  saving.value = true
  try {
    await entityListConfigApi.save(dto)
    ElMessage.success('保存成功')
  } catch (e) {
    console.error('保存失败:', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function loadPreviewData() {
  if (!entityCode.value) return
  previewLoading.value = true
  try {
    // 只传查询条件，不传分页参数（后端不分页，返回全部数据）
    const params = { ...previewQueryForm.value }
    // 添加查询方式参数（EQ/LIKE/GT/LT 等）
    previewQueryFields.value.forEach((field) => {
      const code = field.fieldCode
      if (code && params[code] !== undefined && field.queryType) {
        params[code + '_op'] = field.queryType
      }
    })
    // 调用带列表配置扩展的接口
    const res = await entityDataApi.getListWithConfig(entityCode.value, configInfo.value?.listKey, params)
    previewAllData.value = res || []
    previewTotal.value = previewAllData.value.length
    // 前端内存分页
    const start = (previewPageNum.value - 1) * previewPageSize.value
    const end = start + previewPageSize.value
    previewDataList.value = previewAllData.value.slice(start, end)
  } catch (e) {
    console.error('加载预览数据失败:', e)
    previewAllData.value = []
    previewDataList.value = []
    previewTotal.value = 0
  } finally {
    previewLoading.value = false
  }
}

function handlePreviewSearch() {
  previewPageNum.value = 1
  loadPreviewData()
}

function handlePreviewReset() {
  previewQueryForm.value = {}
  previewPageNum.value = 1
  loadPreviewData()
}

function parseOptions(optionsJson) {
  if (!optionsJson) return []
  try {
    return JSON.parse(optionsJson)
  } catch {
    return []
  }
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.entity-list-config-design {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
}
.design-container {
  display: flex;
  flex: 1;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}
.config-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
.preview-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
.drag-handle {
  cursor: move;
  color: #909399;
}
.drag-handle:hover {
  color: #409eff;
}
.preview-query {
  margin-bottom: 12px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}
.preview-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
