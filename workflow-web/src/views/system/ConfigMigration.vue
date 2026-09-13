<template>
  <div class="config-migration-page">
    <el-card class="overview-card" shadow="never">
      <div class="overview">
        <div>
          <p>迁移实体、流程、系统实体 UI、工作日历和 SLA 策略的已发布快照，不包含业务数据、系统表结构和运行台账。</p>
        </div>
        <div class="overview-stats">
          <div><strong>{{ assetStats.pending }}</strong><span>待导出</span></div>
          <div><strong>{{ assetStats.exported }}</strong><span>已导出</span></div>
          <div><strong>{{ importStats.blocked }}</strong><span>待处理冲突</span></div>
        </div>
      </div>
      <el-steps :active="migrationStep" finish-status="success" simple class="migration-steps">
        <el-step title="1. 选择配置" description="确认发布快照" @click="goToStage('assets')" />
        <el-step title="2. 校验依赖" description="补齐硬依赖" @click="goToStage('assets')" />
        <el-step title="3. 生成发布包" description="下载并交付" @click="goToStage('exports')" />
        <el-step title="4. 上传并对比" description="处理环境差异" @click="goToStage('imports')" />
        <el-step title="5. 发布结果" description="发布或回滚" @click="goToStage('imports')" />
      </el-steps>
    </el-card>
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="选择与校验" name="assets">
          <div class="toolbar">
            <el-form :model="assetFilters" inline>
              <el-form-item label="类型">
                <el-select v-model="assetFilters.assetType" clearable placeholder="全部" style="width: 160px">
                  <el-option label="实体" value="ENTITY" />
                  <el-option label="系统实体 UI" value="SYSTEM_ENTITY_UI" />
                  <el-option label="流程" value="PROCESS" />
                  <el-option label="数据字典" value="DICTIONARY" />
                  <el-option label="工作日历" value="WORK_CALENDAR" />
                  <el-option label="SLA 策略" value="TASK_SLA_POLICY" />
                </el-select>
              </el-form-item>
              <el-form-item label="编码">
                <el-input v-model="assetFilters.businessKey" clearable placeholder="实体/流程编码" />
              </el-form-item>
              <el-form-item label="迁移标记">
                <el-input v-model="assetFilters.migrationTag" clearable placeholder="REL-..." />
              </el-form-item>
              <el-form-item label="待导出">
                <el-select v-model="assetFilters.markForExport" clearable placeholder="全部" style="width: 120px">
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="searchAssets">查询</el-button>
                <el-button @click="resetAssetFilters">重置</el-button>
              </el-form-item>
            </el-form>
            <el-button
              type="primary"
              :disabled="selectedAssets.length === 0"
              :loading="exportPreparing || exporting"
              @click="openBatchExport"
            >
              批量下载（{{ selectedAssets.length }}）
            </el-button>
          </div>
          <PageState
            v-if="assetError"
            type="error"
            title="配置清单加载失败"
            :description="assetError"
            retryable
            compact
            @retry="loadAssets"
          />
          <el-table
            v-else
            ref="assetTableRef"
            v-loading="assetLoading"
            :data="assets"
            border
            stripe
            row-key="id"
            @selection-change="selectedAssets = $event"
          >
            <el-table-column
              type="selection"
              width="48"
              reserve-selection
              :selectable="row => row.snapshotCompleteness === 'COMPLETE'"
            />
            <el-table-column prop="assetType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="assetTypeTagType(row.assetType)">
                  {{ assetTypeLabel(row.assetType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assetName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="businessKey" label="业务编码" min-width="150" show-overflow-tooltip />
            <el-table-column prop="sourceVersion" label="版本" width="80">
              <template #default="{ row }">v{{ row.sourceVersion }}</template>
            </el-table-column>
            <el-table-column prop="migrationTag" label="迁移标记" min-width="160" />
            <el-table-column prop="snapshotCompleteness" label="快照" width="100">
              <template #default="{ row }">
                <el-tag :type="row.snapshotCompleteness === 'COMPLETE' ? 'success' : 'warning'">
                  {{ row.snapshotCompleteness === 'COMPLETE' ? '完整' : '历史缺失' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="markForExport" label="待导出" width="80">
              <template #default="{ row }">
                <el-tag :type="row.markForExport ? 'success' : 'info'">{{ row.markForExport ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="exportStatus" label="导出状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.exportStatus === 'EXPORTED' ? 'success' : 'warning'">
                  {{ row.exportStatus === 'EXPORTED' ? '已导出' : '待导出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dependencyCount" label="依赖" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="showDependencies(row)">
                  {{ row.dependencyCount || 0 }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="publishedAt" label="发布时间" min-width="160">
              <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="showSnapshot(row)">快照</el-button>
                <el-button link type="primary" @click="openMarkDialog(row)">标记</el-button>
                <el-button
                  link
                  type="success"
                  :disabled="row.snapshotCompleteness !== 'COMPLETE'"
                  @click="openSingleExport(row)"
                >
                  下载
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <ConfigMigrationPagination
            v-if="!assetError"
            v-model="assetPage"
            @change="loadAssets"
          />
        </el-tab-pane>
        <el-tab-pane label="发布包" name="exports">
          <div class="table-actions">
            <el-button @click="loadExports">刷新</el-button>
          </div>
          <PageState
            v-if="exportError"
            type="error"
            title="发布包记录加载失败"
            :description="exportError"
            retryable
            compact
            @retry="loadExports"
          />
          <el-table v-else v-loading="exportLoading" :data="exportPackages" border stripe>
            <el-table-column label="发布包信息" min-width="350">
              <template #default="{ row }">
                <div class="primary-line">{{ row.packageNo }}</div>
                <div class="meta-line">{{ row.fileName }}</div>
                <el-popover placement="bottom-start" :width="420" trigger="click">
                  <template #reference>
                    <el-button link type="info" class="technical-detail-button">查看技术详情</el-button>
                  </template>
                  <div class="technical-detail">
                    <div><strong>文件名</strong>{{ row.fileName || '-' }}</div>
                    <div><strong>SHA-256</strong><code>{{ row.checksum || '-' }}</code></div>
                  </div>
                </el-popover>
              </template>
            </el-table-column>
            <el-table-column prop="migrationTag" label="迁移标记" min-width="190" />
            <el-table-column prop="assetCount" label="资产数" width="80" />
            <el-table-column label="创建信息" min-width="170">
              <template #default="{ row }">
                <div>{{ row.createdBy || '-' }}</div>
                <div class="meta-line">{{ formatDate(row.createdAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="downloadCount" label="下载次数" width="90" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="downloadPackage(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <ConfigMigrationPagination
            v-if="!exportError"
            v-model="exportPage"
            @change="loadExports"
          />
        </el-tab-pane>
        <el-tab-pane label="导入与发布" name="imports">
          <div class="import-panel">
            <el-input v-model="sourceEnvironment" placeholder="来源环境，如 TEST" style="width: 220px" />
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".wfpack"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
            >
              <el-button>选择 .wfpack</el-button>
            </el-upload>
            <el-button type="primary" :loading="uploading" :disabled="!pendingFile" @click="uploadPackage">
              上传并校验
            </el-button>
            <el-button @click="loadImports">刷新</el-button>
          </div>
          <PageState
            v-if="importError"
            type="error"
            title="导入记录加载失败"
            :description="importError"
            retryable
            compact
            @retry="loadImports"
          />
          <el-table v-else v-loading="importLoading" :data="imports" border stripe>
            <el-table-column label="发布包" min-width="310">
              <template #default="{ row }">
                <div class="primary-line">{{ row.packageNo }}</div>
                <div class="meta-line">{{ row.fileName }}</div>
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.sourceEnvironment || '-' }}</el-tag>
                <div class="meta-line migration-tag-line">{{ row.migrationTag }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="导入结果" min-width="190">
              <template #default="{ row }">
                <div>{{ row.importedBy || '-' }} · {{ formatDate(row.importedAt) }}</div>
                <div v-if="row.errorMessage" class="error-line" :title="row.errorMessage">{{ row.errorMessage }}</div>
                <div v-else class="meta-line">校验和与签名已验证</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="270" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="showImportItems(row)">项目</el-button>
                <el-button link type="primary" @click="analyzeImport(row)">分析</el-button>
                <el-button link type="primary" @click="openCompare(row)">对比</el-button>
                <el-button v-if="row.status === 'BLOCKED'" link type="warning" @click="openMapping(row)">映射</el-button>
                <el-button v-if="row.status === 'ANALYZED'" link type="success" @click="publishImport(row)">发布</el-button>
                <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="rollbackImport(row)">回滚</el-button>
              </template>
            </el-table-column>
          </el-table>
          <ConfigMigrationPagination
            v-if="!importError"
            v-model="importPage"
            @change="loadImports"
          />
        </el-tab-pane>
        <el-tab-pane label="影响对比" name="compare">
          <div class="compare-toolbar">
            <el-select
              v-model="compareImportId"
              filterable
              clearable
              placeholder="选择导入批次"
              style="width: 420px"
              @change="handleCompareImportChange"
            >
              <el-option
                v-for="item in importOptions"
                :key="item.id"
                :label="`${item.migrationTag} · ${item.packageNo}`"
                :value="item.id"
              />
            </el-select>
            <el-button type="primary" :disabled="!compareImportId" @click="loadCompare">刷新对比</el-button>
          </div>
          <PageState
            v-if="compareError"
            type="error"
            title="影响对比加载失败"
            :description="compareError"
            retryable
            compact
            @retry="loadCompare"
          />
          <el-alert
            v-else-if="compareData?.validationReport"
            :title="compareData.validationReport.blocked ? '存在阻断项，不能发布' : '分析通过，可以发布'"
            :type="compareData.validationReport.blocked ? 'error' : 'success'"
            :closable="false"
            show-icon
            class="compare-alert"
          />
          <el-table
            v-if="!compareError"
            :data="pagedCompareItems"
            border
            stripe
            v-loading="compareLoading"
          >
            <el-table-column label="资产" min-width="330">
              <template #default="{ row }">
                <div class="asset-title-line">
                  <el-tag size="small" :type="assetTypeTagType(row.assetType)">
                    {{ assetTypeLabel(row.assetType) }}
                  </el-tag>
                  <span>{{ row.assetName }}</span>
                </div>
                <div class="meta-line">{{ row.businessKey }}</div>
              </template>
            </el-table-column>
            <el-table-column label="版本" width="130">
              <template #default="{ row }">
                v{{ row.sourceVersion }} → v{{ row.targetBeforeVersion || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="comparisonStatus" label="对比状态" width="130">
              <template #default="{ row }">
                <el-tag :type="compareStatusType(row.comparisonStatus)">
                  {{ compareStatusText(row.comparisonStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="mappingStatus" label="映射" width="100">
              <template #default="{ row }">
                <el-tag :type="row.mappingStatus === 'RESOLVED' ? 'success' : 'warning'">
                  {{ row.mappingStatus === 'RESOLVED' ? '已解决' : '待映射' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publishStatus" label="发布状态" width="110">
              <template #default="{ row }">
                <el-tag type="info">{{ publishStatusText(row.publishStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="说明" min-width="240" show-overflow-tooltip />
          </el-table>
          <ConfigMigrationPagination
            v-if="!compareError"
            v-model="comparePage"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <el-dialog v-model="markDialogVisible" title="调整待导出标记" width="500px">
      <el-form :model="markForm" label-width="110px">
        <el-form-item label="加入待导出">
          <el-switch v-model="markForm.markForExport" />
        </el-form-item>
        <el-form-item label="迁移批次标记">
          <el-input v-model="markForm.migrationTag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="markDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMark">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="exportDialogVisible" title="生成配置发布包" width="620px">
      <el-form :model="exportForm" label-width="110px">
        <el-form-item label="资产">
          <span>{{ exportTargets.map(item => item.assetName).join('、') }}</span>
        </el-form-item>
        <el-form-item label="迁移标记">
          <el-input v-model="exportForm.migrationTag" />
        </el-form-item>
        <el-form-item v-if="exportTargets.length === 1" label="导出范围">
          <el-radio-group v-model="exportForm.full">
            <el-radio :value="true">完整配置</el-radio>
            <el-radio :value="false">细粒度选择</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="exportTargets.length === 1 && !exportForm.full" label="配置部分">
          <el-checkbox-group v-model="exportForm.sections">
            <el-checkbox v-for="option in sectionOptions" :key="option.value" :label="option.value">
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item
          v-if="exportTargets.length === 1 && !exportForm.full && exportForm.sections.includes('forms') && formOptions.length"
          label="具体表单"
        >
          <el-select
            v-model="exportForm.formKeys"
            multiple
            clearable
            collapse-tags
            placeholder="不选择表示全部表单"
            style="width: 100%"
          >
            <el-option
              v-for="option in formOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="exportTargets.length === 1 && !exportForm.full && exportForm.sections.includes('lists') && listOptions.length"
          label="具体列表"
        >
          <el-select
            v-model="exportForm.listKeys"
            multiple
            clearable
            collapse-tags
            placeholder="不选择表示全部列表"
            style="width: 100%"
          >
            <el-option
              v-for="option in listOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-alert
          title="发布时以当前迁移包为原子单位；细粒度导出只更新选中范围，并自动补齐该范围的硬依赖。"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exporting" @click="confirmExport">生成并下载</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="snapshotVisible" title="发布快照" width="900px">
      <pre class="json-view">{{ prettyJson(currentSnapshot) }}</pre>
    </el-dialog>
    <el-dialog v-model="dependencyVisible" title="依赖关系" width="760px">
      <el-table :data="currentDependencies" border>
        <el-table-column prop="type" label="类型" width="170" />
        <el-table-column prop="key" label="依赖编码" min-width="240" />
        <el-table-column prop="required" label="硬依赖" width="90">
          <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="160" />
      </el-table>
    </el-dialog>
    <el-dialog v-model="itemsVisible" title="导入项目" width="1180px">
      <el-table :data="currentImportItems" border stripe>
        <el-table-column prop="assetType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="assetTypeTagType(row.assetType)">
              {{ assetTypeLabel(row.assetType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assetName" label="名称" min-width="150" />
        <el-table-column prop="businessKey" label="业务编码" min-width="160" />
        <el-table-column prop="sourceVersion" label="来源版本" width="90" />
        <el-table-column prop="comparisonStatus" label="对比状态" width="120" />
        <el-table-column prop="mappingStatus" label="映射状态" width="100" />
        <el-table-column prop="publishStatus" label="发布状态" width="110" />
        <el-table-column prop="errorMessage" label="原因" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-dialog>
    <el-dialog v-model="mappingVisible" title="环境依赖映射" width="900px">
      <el-alert
        title="组件、数据提供者、用户、角色或部门在生产环境编码不一致时，在这里建立映射。"
        type="warning"
        :closable="false"
        class="mapping-alert"
      />
      <el-table :data="mappingRows" border>
        <el-table-column prop="sourceType" label="类型" width="170" />
        <el-table-column prop="sourceKey" label="来源编码" min-width="230" />
        <el-table-column label="生产编码" min-width="260">
          <template #default="{ row }">
            <el-input v-model="row.targetKey" placeholder="输入生产环境注册名或业务编码" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }"><el-input v-model="row.description" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="mappingVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMappings">保存并重新分析</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { configMigrationApi } from '@/api/configMigration'
import { generateMigrationTag } from '@/utils/migrationTag'
import PageState from '@/components/PageState.vue'
import ConfigMigrationPagination from '@/components/ConfigMigrationPagination.vue'
import {
  assetTypeLabel,
  assetTypeTagType,
  compareStatusText,
  compareStatusType,
  publishStatusText,
  statusText,
  statusType
} from '@/shared/config-migration-display'
import {
  applyConfigMigrationPageResult,
  createConfigMigrationPage,
  paginateConfigMigrationRows,
  shouldReloadConfigMigrationPage,
  updateConfigMigrationClientPage
} from '@/shared/config-migration-pagination'
const activeTab = ref('assets')
const assets = ref([])
const assetPage = ref(createConfigMigrationPage())
const assetLoading = ref(false)
const assetError = ref('')
const selectedAssets = ref([])
const assetTableRef = ref()
const assetFilters = reactive({
  assetType: '',
  businessKey: '',
  migrationTag: '',
  markForExport: true
})
const exportPackages = ref([])
const exportPage = ref(createConfigMigrationPage())
const exportLoading = ref(false)
const exportError = ref('')
const exporting = ref(false)
const exportPreparing = ref(false)
const imports = ref([])
const importOptions = ref([])
const importPage = ref(createConfigMigrationPage())
const importLoading = ref(false)
const importError = ref('')
const uploading = ref(false)
const pendingFile = ref(null)
const sourceEnvironment = ref('TEST')
const uploadRef = ref()
const currentImportItems = ref([])
const itemsVisible = ref(false)
const markDialogVisible = ref(false)
const currentMarkAsset = ref(null)
const markForm = reactive({ markForExport: true, migrationTag: '' })
const exportDialogVisible = ref(false)
const exportTargets = ref([])
const exportForm = reactive({
  migrationTag: generateMigrationTag(),
  full: true,
  sections: [],
  formKeys: [],
  listKeys: []
})
const snapshotVisible = ref(false)
const currentSnapshot = ref({})
const dependencyVisible = ref(false)
const currentDependencies = ref([])
const mappingVisible = ref(false)
const currentMappingImport = ref(null)
const mappingRows = ref([])
const compareImportId = ref('')
const compareData = ref(null)
const comparePage = ref(createConfigMigrationPage())
const compareLoading = ref(false)
const compareError = ref('')
// 快速翻页或切换批次时，只有同一数据源的最后一次请求可以提交状态。
const requestVersions = {
  assets: 0,
  exports: 0,
  imports: 0,
  importOptions: 0,
  stats: 0,
  compare: 0,
  snapshotDetail: 0,
  dependencyDetail: 0,
  exportDetail: 0
}
const assetStats = reactive({ pending: 0, exported: 0 })
const importStats = reactive({ blocked: 0 })
const compareItems = computed(() => Array.isArray(compareData.value?.items) ? compareData.value.items : [])
const pagedCompareItems = computed(() => paginateConfigMigrationRows(compareItems.value, comparePage.value))
const migrationStep = computed(() => {
  if (activeTab.value === 'assets') {
    return selectedAssets.value.length > 0 ? 1 : 0
  }
  if (activeTab.value === 'exports') return 2
  if (activeTab.value === 'compare') return 3
  const selectedImport = importOptions.value.find(item => String(item.id) === String(compareImportId.value))
  return selectedImport?.status === 'PUBLISHED' || selectedImport?.status === 'ROLLED_BACK' ? 5 : 4
})
const sectionOptions = computed(() => {
  const asset = exportTargets.value[0]
  if (!asset) return []
  if (asset.assetType === 'PROCESS') {
    return [
      { label: '流程基本信息', value: 'definition' },
      { label: 'BPMN、节点与表单引用', value: 'bpmnXml' },
      { label: '节点表单', value: 'nodeForms' },
      { label: '节点审批', value: 'nodeApprovals' },
      { label: '流程动作', value: 'flowActions' },
      { label: '状态映射', value: 'statusMappings' }
    ]
  }
  if (asset.assetType === 'SYSTEM_ENTITY_UI') {
    return [
      { label: '表单', value: 'forms' },
      { label: '列表', value: 'lists' },
      { label: '只读数据源', value: 'dataSources' },
      { label: 'UI 扩展', value: 'extensions' }
    ]
  }
  if (asset.assetType === 'WORK_CALENDAR') {
    return [{ label: '日历规则与组织绑定', value: 'configuration' }]
  }
  if (asset.assetType === 'TASK_SLA_POLICY') {
    return [{ label: '时限与升级步骤', value: 'configuration' }]
  }
  if (asset.assetType === 'DICTIONARY') {
    return [
      { label: '字典定义', value: 'definition' },
      { label: '字典项', value: 'items' }
    ]
  }
  return [
    { label: '实体基本信息与流程绑定', value: 'definition' },
    { label: '实体字段与关系', value: 'fields' },
    { label: '状态与编码规则', value: 'statuses' },
    { label: '表单', value: 'forms' },
    { label: '列表', value: 'lists' },
    { label: '数据权限', value: 'dataPermissions' },
    { label: '菜单权限', value: 'menus' }
  ]
})
const exportSnapshot = computed(() => parseJson(
  exportTargets.value[0]?.snapshotJson,
  {}
))
const formOptions = computed(() => (exportSnapshot.value.forms || []).map(item => ({
  label: item.formName ? `${item.formName} (${item.formKey})` : item.formKey,
  value: item.formKey
})).filter(item => item.value))
const listOptions = computed(() => (exportSnapshot.value.lists || []).map(item => ({
  label: item.listName ? `${item.listName} (${item.listKey})` : item.listKey,
  value: item.listKey
})).filter(item => item.value))
const loadAssets = async (pageOverride) => {
  const requestVersion = ++requestVersions.assets
  const requestedPage = Number.isFinite(Number(pageOverride?.pageNum)) ? pageOverride : assetPage.value
  assetLoading.value = true
  assetError.value = ''
  try {
    const filters = Object.fromEntries(
      Object.entries(assetFilters).filter(([, value]) => value !== '' && value !== null && value !== undefined)
    )
    const result = await configMigrationApi.getAssetPage({
      ...filters,
      pageNum: requestedPage.pageNum,
      pageSize: requestedPage.pageSize
    })
    if (requestVersion !== requestVersions.assets) return
    const normalized = applyConfigMigrationPageResult(requestedPage, result)
    assets.value = normalized.records
    assetPage.value = normalized.page
    if (shouldReloadConfigMigrationPage(requestedPage, normalized)) {
      await loadAssets(normalized.page)
    }
  } catch (error) {
    if (requestVersion === requestVersions.assets) {
      assetError.value = error?.message || '无法读取可迁移配置，请检查权限或稍后重试。'
    }
  } finally {
    if (requestVersion === requestVersions.assets) assetLoading.value = false
  }
}
const loadExports = async (pageOverride) => {
  const requestVersion = ++requestVersions.exports
  const requestedPage = Number.isFinite(Number(pageOverride?.pageNum)) ? pageOverride : exportPage.value
  exportLoading.value = true
  exportError.value = ''
  try {
    const result = await configMigrationApi.getExportPackagePage({
      pageNum: requestedPage.pageNum,
      pageSize: requestedPage.pageSize
    })
    if (requestVersion !== requestVersions.exports) return
    const normalized = applyConfigMigrationPageResult(requestedPage, result)
    exportPackages.value = normalized.records
    exportPage.value = normalized.page
    if (shouldReloadConfigMigrationPage(requestedPage, normalized)) {
      await loadExports(normalized.page)
    }
  } catch (error) {
    if (requestVersion === requestVersions.exports) {
      exportError.value = error?.message || '无法读取发布包记录，请稍后重试。'
    }
  } finally {
    if (requestVersion === requestVersions.exports) exportLoading.value = false
  }
}
const loadImports = async (pageOverride) => {
  const requestVersion = ++requestVersions.imports
  const requestedPage = Number.isFinite(Number(pageOverride?.pageNum)) ? pageOverride : importPage.value
  importLoading.value = true
  importError.value = ''
  try {
    const result = await configMigrationApi.getImportPage({
      pageNum: requestedPage.pageNum,
      pageSize: requestedPage.pageSize
    })
    if (requestVersion !== requestVersions.imports) return
    const normalized = applyConfigMigrationPageResult(requestedPage, result)
    imports.value = normalized.records
    importPage.value = normalized.page
    if (shouldReloadConfigMigrationPage(requestedPage, normalized)) {
      await loadImports(normalized.page)
    }
  } catch (error) {
    if (requestVersion === requestVersions.imports) {
      importError.value = error?.message || '无法读取导入记录，请检查权限或稍后重试。'
    }
  } finally {
    if (requestVersion === requestVersions.imports) importLoading.value = false
  }
}
const loadImportOptions = async () => {
  const requestVersion = ++requestVersions.importOptions
  try {
    const options = await configMigrationApi.getImportOptions() || []
    if (requestVersion === requestVersions.importOptions) importOptions.value = options
  } catch {
    // 分页主表仍可独立使用；下拉读取失败时保留上一次成功的候选项。
  }
}
const loadStats = async () => {
  const requestVersion = ++requestVersions.stats
  try {
    const stats = await configMigrationApi.getStats()
    if (requestVersion !== requestVersions.stats) return
    assetStats.pending = Number(stats?.pending) || 0
    assetStats.exported = Number(stats?.exported) || 0
    importStats.blocked = Number(stats?.blocked) || 0
  } catch {
    // 统计卡失败不应阻断各 Tab 的核心查询和迁移操作。
  }
}
const clearAssetSelection = () => {
  assetTableRef.value?.clearSelection()
  selectedAssets.value = []
}
const searchAssets = () => {
  assetPage.value = { ...assetPage.value, pageNum: 1 }
  clearAssetSelection()
  loadAssets()
}
const resetAssetFilters = () => {
  Object.assign(assetFilters, {
    assetType: '',
    businessKey: '',
    migrationTag: '',
    markForExport: true
  })
  searchAssets()
}
const handleTabChange = (name) => {
  if (name === 'assets') loadAssets()
  if (name === 'exports') loadExports()
  if (name === 'imports') loadImports()
  if (name === 'compare') loadImportOptions()
}
const goToStage = (tab) => {
  activeTab.value = tab
  handleTabChange(tab)
}
const openMarkDialog = (row) => {
  currentMarkAsset.value = row
  markForm.markForExport = Boolean(row.markForExport)
  markForm.migrationTag = row.migrationTag || generateMigrationTag()
  markDialogVisible.value = true
}
const saveMark = async () => {
  if (markForm.markForExport && !markForm.migrationTag.trim()) {
    ElMessage.warning('加入待导出清单时必须填写迁移批次标记')
    return
  }
  await configMigrationApi.updateAssetMark(currentMarkAsset.value.id, { ...markForm })
  ElMessage.success('标记已更新')
  markDialogVisible.value = false
  clearAssetSelection()
  await Promise.all([loadAssets(), loadStats()])
}
const showSnapshot = async (row) => {
  const requestVersion = ++requestVersions.snapshotDetail
  const detail = await configMigrationApi.getAsset(row.id)
  if (requestVersion !== requestVersions.snapshotDetail) return
  currentSnapshot.value = parseJson(detail?.snapshotJson, {})
  snapshotVisible.value = true
}
const showDependencies = async (row) => {
  const requestVersion = ++requestVersions.dependencyDetail
  const detail = await configMigrationApi.getAsset(row.id)
  if (requestVersion !== requestVersions.dependencyDetail) return
  currentDependencies.value = parseJson(detail?.dependenciesJson, [])
  dependencyVisible.value = true
}
const openExportDialog = (targets) => {
  exportTargets.value = targets
  const tags = new Set(targets.map(item => item.migrationTag).filter(Boolean))
  exportForm.migrationTag = tags.size === 1 ? [...tags][0] : generateMigrationTag()
  exportForm.full = true
  exportForm.sections = []
  exportForm.formKeys = []
  exportForm.listKeys = []
  exportDialogVisible.value = true
}
const openSingleExport = async (row) => {
  // 分页列表只传输摘要；细粒度选项需要在打开弹窗时按需读取完整快照。
  const requestVersion = ++requestVersions.exportDetail
  exportPreparing.value = true
  try {
    const detail = await configMigrationApi.getAsset(row.id)
    if (requestVersion !== requestVersions.exportDetail) return
    openExportDialog([detail])
  } finally {
    if (requestVersion === requestVersions.exportDetail) exportPreparing.value = false
  }
}
const openBatchExport = async () => {
  // 单选批量入口同样开放细粒度导出，因此必须补取分页摘要中未携带的完整快照。
  const targets = [...selectedAssets.value]
  if (targets.length === 0) return
  const requestVersion = ++requestVersions.exportDetail
  exportPreparing.value = true
  try {
    if (targets.length === 1) {
      const detail = await configMigrationApi.getAsset(targets[0].id)
      if (requestVersion !== requestVersions.exportDetail) return
      openExportDialog([detail])
      return
    }
    openExportDialog(targets)
  } finally {
    if (requestVersion === requestVersions.exportDetail) exportPreparing.value = false
  }
}
const confirmExport = async () => {
  if (!exportForm.migrationTag.trim()) {
    ElMessage.warning('请输入迁移标记')
    return
  }
  if (exportTargets.value.length === 1 && !exportForm.full && exportForm.sections.length === 0) {
    ElMessage.warning('至少选择一个细粒度配置部分')
    return
  }
  exporting.value = true
  try {
    const selections = {}
    if (exportTargets.value.length === 1) {
      const selection = exportForm.full
        ? { full: true }
        : { full: false, sections: [...exportForm.sections] }
      if (!exportForm.full && exportForm.sections.includes('forms') && exportForm.formKeys.length) {
        selection.formKeys = [...exportForm.formKeys]
      }
      if (!exportForm.full && exportForm.sections.includes('lists') && exportForm.listKeys.length) {
        selection.listKeys = [...exportForm.listKeys]
      }
      selections[exportTargets.value[0].id] = selection
    }
    const result = await configMigrationApi.exportPackage({
      assetIds: exportTargets.value.map(item => item.id),
      migrationTag: exportForm.migrationTag,
      selections
    })
    await downloadPackage(result)
    ElMessage.success('发布包已生成')
    exportDialogVisible.value = false
    exportPage.value = { ...exportPage.value, pageNum: 1 }
    clearAssetSelection()
    await Promise.all([loadAssets(), loadExports(), loadStats()])
  } finally {
    exporting.value = false
  }
}
const downloadPackage = async (row) => {
  const blob = await configMigrationApi.downloadPackage(row.id)
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = row.fileName || `${row.packageNo || 'config-migration'}.wfpack`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(link.href)
}
const handleFileChange = (file) => {
  pendingFile.value = file.raw
}
const handleFileRemove = () => {
  pendingFile.value = null
}
const uploadPackage = async () => {
  if (!sourceEnvironment.value.trim()) {
    ElMessage.warning('请填写来源环境，便于后续识别配置来源')
    return
  }
  uploading.value = true
  try {
    await configMigrationApi.uploadPackage(pendingFile.value, sourceEnvironment.value)
    ElMessage.success('发布包已上传并完成签名校验')
    pendingFile.value = null
    uploadRef.value?.clearFiles()
    importPage.value = { ...importPage.value, pageNum: 1 }
    await Promise.all([loadImports(), loadImportOptions(), loadStats()])
  } finally {
    uploading.value = false
  }
}
const showImportItems = async (row) => {
  currentImportItems.value = await configMigrationApi.getImportItems(row.id) || []
  itemsVisible.value = true
}
const analyzeImport = async (row) => {
  const result = await configMigrationApi.analyzeImport(row.id)
  ElMessage[result.blocked ? 'warning' : 'success'](
    result.blocked ? '分析完成，存在阻断项' : '分析通过，可以发布'
  )
  await Promise.all([loadImports(), loadImportOptions(), loadStats()])
  if (activeTab.value === 'compare') {
    compareImportId.value = row.id
    loadCompare()
  }
}
const openCompare = async (row) => {
  activeTab.value = 'compare'
  compareImportId.value = row.id
  comparePage.value = createConfigMigrationPage(comparePage.value.pageSize)
  await loadCompare()
}
const handleCompareImportChange = () => {
  comparePage.value = createConfigMigrationPage(comparePage.value.pageSize)
  loadCompare()
}
const loadCompare = async () => {
  const requestVersion = ++requestVersions.compare
  if (!compareImportId.value) {
    compareData.value = null
    comparePage.value = updateConfigMigrationClientPage(comparePage.value, 0)
    compareError.value = ''
    compareLoading.value = false
    return
  }
  compareLoading.value = true
  compareError.value = ''
  try {
    const result = await configMigrationApi.compareImport(compareImportId.value)
    if (requestVersion !== requestVersions.compare) return
    compareData.value = result
    comparePage.value = updateConfigMigrationClientPage(comparePage.value, compareItems.value.length)
  } catch (error) {
    if (requestVersion === requestVersions.compare) {
      compareError.value = error?.message || '无法生成影响对比，请重新分析后再试。'
    }
  } finally {
    if (requestVersion === requestVersions.compare) compareLoading.value = false
  }
}
const openMapping = async (row) => {
  currentMappingImport.value = row
  const compare = await configMigrationApi.compareImport(row.id)
  const missing = (compare.validationReport?.items || [])
    .flatMap(item => item.missingDependencies || [])
  const unique = new Map()
  missing.forEach(item => unique.set(`${item.type}:${item.key}`, {
    sourceType: item.type,
    sourceKey: item.key,
    targetKey: item.targetKey || item.key,
    description: item.source || '',
    enabled: true
  }))
  mappingRows.value = [...unique.values()]
  mappingVisible.value = true
}
const saveMappings = async () => {
  if (mappingRows.value.some(item => !item.targetKey?.trim())) {
    ElMessage.warning('生产编码不能为空')
    return
  }
  await configMigrationApi.saveMappings(currentMappingImport.value.id, mappingRows.value)
  ElMessage.success('映射已保存并重新分析')
  mappingVisible.value = false
  await Promise.all([loadImports(), loadImportOptions(), loadStats()])
  if (String(compareImportId.value) === String(currentMappingImport.value.id)) loadCompare()
}
const publishImport = async (row) => {
  const confirmation = await ElMessageBox.prompt(
    `发布会更新目标环境的实体、系统实体 UI、表单、列表、流程、工作日历和 SLA 策略配置。请输入迁移标记「${row.migrationTag}」确认。`,
    '确认发布配置',
    {
      type: 'warning',
      inputPlaceholder: row.migrationTag,
      confirmButtonText: '确认发布',
      inputValidator: value => value === row.migrationTag || '迁移标记不匹配'
    }
  )
  if (confirmation.value !== row.migrationTag) return
  await configMigrationApi.publishImport(row.id)
  ElMessage.success('配置发布成功')
  await Promise.all([loadImports(), loadImportOptions(), loadStats()])
}
const rollbackImport = async (row) => {
  const confirmation = await ElMessageBox.prompt(
    `回滚会恢复上一版本配置，但不会修改业务数据或系统表结构。请输入发布包编号「${row.packageNo}」确认。`,
    '确认回滚配置',
    {
      type: 'warning',
      inputPlaceholder: row.packageNo,
      confirmButtonText: '确认回滚',
      inputValidator: value => value === row.packageNo || '发布包编号不匹配'
    }
  )
  if (confirmation.value !== row.packageNo) return
  await configMigrationApi.rollbackImport(row.id)
  ElMessage.success('配置已回滚')
  await Promise.all([loadImports(), loadImportOptions(), loadStats()])
}
const parseJson = (value, fallback) => {
  if (!value) return fallback
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}
const prettyJson = (value) => JSON.stringify(value || {}, null, 2)
const formatDate = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
onMounted(async () => {
  await Promise.all([loadAssets(), loadImports(), loadImportOptions(), loadStats()])
})
</script>
<style scoped lang="scss" src="./ConfigMigration.scss"></style>
