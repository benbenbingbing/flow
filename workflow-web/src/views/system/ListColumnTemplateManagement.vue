<template>
  <div class="template-page">
    <div class="page-heading">
      <div>
        <h2>列表列模板</h2>
        <p>把常用列配置保存为初始化模板。应用时复制到当前列，之后模板与列互不影响。</p>
      </div>
      <div class="heading-actions">
        <el-button
          :icon="Refresh"
          :loading="loading"
          title="刷新模板"
          @click="loadAll"
        />
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Plus"
          @click="editorRef?.openCreate()"
        >
          新建模板
        </el-button>
      </div>
    </div>

    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索模板名称或编码"
        class="keyword-input"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <el-table
      v-loading="loading"
      :data="filteredRows"
      row-key="id"
      border
      stripe
    >
      <el-table-column label="模板" min-width="250">
        <template #default="{ row }">
          <div class="primary-text">{{ row.templateName }}</div>
          <div class="secondary-text">{{ row.templateKey }}</div>
        </template>
      </el-table-column>
      <el-table-column label="配置摘要" min-width="300">
        <template #default="{ row }">
          <span>{{ row.summary }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status === 'ACTIVE' ? '启用' : row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="canManage"
            link
            type="primary"
            :icon="Edit"
            @click="editorRef?.openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canManage"
            link
            type="primary"
            :icon="CopyDocument"
            @click="editorRef?.openCopy(row)"
          >
            复制
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && filteredRows.length === 0"
      description="暂无符合条件的列表列模板"
      :image-size="72"
      class="page-empty"
    />

    <ListColumnTemplateEditorDialog
      ref="editorRef"
      :data-source-options="dataSourceOptions"
      :unified-data-sources="unifiedDataSources"
      @saved="loadAll"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import {
  CopyDocument,
  Edit,
  Plus,
  Refresh,
  Search
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ListColumnTemplateEditorDialog
  from '@/components/ui-config/ListColumnTemplateEditorDialog.vue'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiComponentTemplateApi, uiDataSourceApi } from '@/api/uiConfig'
import { useUserStore } from '@/stores/user'
import {
  createListColumnTemplateEditor,
  describeListColumnTemplate,
  LIST_COLUMN_TEMPLATE_TYPE,
  parseListColumnTemplateSnapshot
} from '@/shared/list-column-template'

const userStore = useUserStore()
const loading = ref(false)
const keyword = ref('')
const rows = ref([])
const dataSourceOptions = ref([{
  value: 'ENTITY_FIELD',
  label: '实体字段',
  description: '直接读取实体字段。',
  supportsQuery: true,
  configSchema: []
}])
const unifiedDataSources = ref([])
const editorRef = ref()

const canManage = computed(() =>
  userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:list-column-template:manage')
)
const filteredRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesKeyword = !search
      || String(row.templateName || '').toLowerCase().includes(search)
      || String(row.templateKey || '').toLowerCase().includes(search)
    return matchesKeyword
  })
})

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [templates, extensions, sources] = await Promise.all([
      uiComponentTemplateApi.list({ templateType: LIST_COLUMN_TEMPLATE_TYPE }),
      entityListConfigApi.getExtensionOptions().catch(() => []),
      uiDataSourceApi.list().catch(() => [])
    ])
    if (Array.isArray(extensions) && extensions.length) {
      dataSourceOptions.value = extensions
    }
    unifiedDataSources.value = Array.isArray(sources)
      ? sources.filter(source => source.enabled !== false)
      : []
    rows.value = await Promise.all((templates || []).map(loadTemplateRow))
  } catch (error) {
    ElMessage.error(error?.message || '加载列表列模板失败')
  } finally {
    loading.value = false
  }
}

async function loadTemplateRow(template) {
  try {
    const snapshot = await uiComponentTemplateApi.snapshot(template.id)
    const editor = createListColumnTemplateEditor({
      id: template.id,
      templateKey: template.templateKey,
      templateName: template.templateName,
      ...parseListColumnTemplateSnapshot(snapshot)
    })
    return {
      ...template,
      editor,
      summary: describeListColumnTemplate(editor)
    }
  } catch {
    const editor = createListColumnTemplateEditor({
      id: template.id,
      templateKey: template.templateKey,
      templateName: template.templateName
    })
    return {
      ...template,
      editor,
      summary: '模板配置暂不可用'
    }
  }
}

function formatDate(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped>
.template-page {
  min-height: 100%;
  padding: 22px;
  background: #f5f7fa;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
.page-heading h2 {
  margin: 0;
  color: #303133;
  font-size: 24px;
}
.page-heading p {
  margin: 7px 0 0;
  color: #606266;
  line-height: 1.6;
}
.heading-actions,
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.table-toolbar { margin-bottom: 14px; }
.keyword-input { width: 320px; }
.primary-text {
  color: #303133;
  font-weight: 600;
  line-height: 1.5;
}
.secondary-text {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  line-height: 1.45;
}
.page-empty {
  margin-top: -1px;
  background: #fff;
  border: 1px solid #ebeef5;
}
@media (max-width: 640px) {
  .template-page { padding: 14px; }
  .page-heading,
  .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .heading-actions { justify-content: flex-end; }
  .keyword-input { width: 100%; }
}
</style>
