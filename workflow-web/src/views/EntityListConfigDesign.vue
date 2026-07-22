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
        <el-tag :type="diffInfo.changed ? 'warning' : 'success'" effect="plain">
          {{ diffInfo.changed ? '草稿有未发布修改' : '已发布' }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button @click="showReleaseHistory">版本</el-button>
        <el-button type="success" plain @click="handlePublish">发布</el-button>
        <el-tag type="info" effect="plain">当前项独立保存</el-tag>
      </div>
    </div>

    <div class="design-container">
      <!-- 左侧：配置区 -->
      <div class="config-panel">
        <el-card shadow="never">
          <template #header>
            <span>字段配置</span>
            <el-text type="info" size="small">拖拽排序，勾选控制显示和查询</el-text>
          </template>

          <el-tabs v-model="activeConfigTab" type="border-card" class="config-tabs">
            <el-tab-pane label="列表设置" name="view">
              <div class="field-toolbar">
                <el-alert
                  title="列表设置可独立保存；列、按钮和场景的修改不会被一并覆盖。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <el-button type="success" plain @click="saveListMetadata">
                  保存列表设置
                </el-button>
              </div>
              <el-form label-width="120px" size="small" class="view-config-form">
                <el-divider content-position="left">渲染模式</el-divider>
                <el-form-item label="自定义列表组件">
                  <el-select
                    v-model="configInfo.customComponent"
                    placeholder="留空使用默认动态列表"
                    filterable
                    allow-create
                    clearable
                    style="width: 420px"
                  >
                    <el-option
                      v-for="option in customListOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    >
                      <div>{{ option.label }}</div>
                      <small class="option-description">{{ option.description }}</small>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item v-if="selectedCustomListSchema.length" label="组件参数">
                  <ConfigSchemaEditor
                    v-model="viewConfig.customComponentProps"
                    :schema="selectedCustomListSchema"
                  />
                </el-form-item>

                <el-divider content-position="left">访问与数据范围</el-divider>
                <el-form-item label="数据范围模式">
                  <el-select v-model="configInfo.dataScopeMode" style="width: 420px">
                    <el-option label="继承实体默认范围" value="INHERIT" />
                    <el-option label="在实体范围内缩小" value="NARROW" />
                    <el-option label="使用列表独立范围（高风险）" value="OVERRIDE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="访问权限码">
                  <el-input
                    v-model="configInfo.accessPermissionCode"
                    placeholder="留空继承 entity:{code}:list"
                    style="width: 420px"
                  />
                </el-form-item>
                <el-form-item label="允许场景">
                  <div class="scene-options">
                    <el-checkbox
                      v-for="scene in sceneOptions"
                      :key="scene.value"
                      :model-value="isSceneEnabled(scene.value)"
                      :disabled="sceneSavingCodes.has(scene.value)"
                      @change="toggleScene(scene.value, $event)"
                    >
                      {{ scene.label }}
                    </el-checkbox>
                  </div>
                  <el-text type="info" size="small">勾选后仅保存当前场景，发布后运行时生效。</el-text>
                </el-form-item>
                <el-form-item label="选择模式">
                  <el-radio-group v-model="configInfo.selectionMode">
                    <el-radio-button value="NONE">不选择</el-radio-button>
                    <el-radio-button value="SINGLE">单选</el-radio-button>
                    <el-radio-button value="MULTIPLE">多选</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="configInfo.selectionMode !== 'NONE'" label="返回值字段">
                  <el-select v-model="configInfo.selectionValueField" filterable style="width: 420px">
                    <el-option label="主键 ID" value="id" />
                    <el-option
                      v-for="field in entityFields"
                      :key="field.fieldCode"
                      :label="`${field.fieldName} (${field.fieldCode})`"
                      :value="field.fieldCode"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="configInfo.selectionMode !== 'NONE'" label="返回映射 JSON">
                  <el-input
                    v-model="configInfo.selectionReturnMappingsText"
                    type="textarea"
                    :rows="3"
                    placeholder='例如 [{"sourceField":"name","targetField":"customerName"}]'
                  />
                </el-form-item>
                <el-form-item label="固定条件 JSON">
                  <el-input
                    v-model="configInfo.fixedFilterConfig"
                    type="textarea"
                    :rows="3"
                    placeholder='例如 {"status":"RUNNING","status_op":"EQ"}'
                  />
                </el-form-item>
                <el-form-item label="上下文绑定 JSON">
                  <el-input
                    v-model="configInfo.contextBindingConfig"
                    type="textarea"
                    :rows="3"
                    placeholder='只保存 relationKey 等服务端可信上下文配置'
                  />
                </el-form-item>
                <el-form-item label="安全查询提供者">
                  <el-input
                    v-model="configInfo.queryProviderCode"
                    placeholder="留空使用平台动态表查询"
                    style="width: 420px"
                  />
                </el-form-item>
                <el-form-item label="统一查询数据源">
                  <el-select
                    v-model="configInfo.queryDataSourceId"
                    clearable
                    filterable
                    placeholder="可选：LIST_QUERY 数据源"
                    style="width: 420px"
                  >
                    <el-option
                      v-for="source in listQuerySources"
                      :key="source.id"
                      :label="`${source.sourceName} (${source.sourceType})`"
                      :value="source.id"
                    />
                  </el-select>
                </el-form-item>

                <el-divider content-position="left">查询区域</el-divider>
                <el-form-item label="默认显示条件">
                  <el-input-number v-model="viewConfig.search.defaultVisibleCount" :min="1" :max="20" />
                </el-form-item>
                <el-form-item label="允许展开收起">
                  <el-switch v-model="viewConfig.search.collapsible" />
                </el-form-item>
                <el-form-item label="标签宽度">
                  <el-input-number v-model="viewConfig.search.labelWidth" :min="60" :max="240" />
                  <span class="unit-text">px</span>
                </el-form-item>

                <el-divider content-position="left">表格与分页</el-divider>
                <el-form-item label="表格样式">
                  <el-checkbox v-model="viewConfig.table.stripe">斑马纹</el-checkbox>
                  <el-checkbox v-model="viewConfig.table.border">边框</el-checkbox>
                  <el-checkbox v-model="viewConfig.table.showIndex">序号列</el-checkbox>
                </el-form-item>
                <el-form-item label="表格尺寸">
                  <el-radio-group v-model="viewConfig.table.size">
                    <el-radio-button value="small">紧凑</el-radio-button>
                    <el-radio-button value="default">默认</el-radio-button>
                    <el-radio-button value="large">宽松</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="默认每页">
                  <el-select v-model="viewConfig.pagination.pageSize" style="width: 160px">
                    <el-option
                      v-for="size in viewConfig.pagination.pageSizes"
                      :key="size"
                      :label="`${size} 条`"
                      :value="size"
                    />
                  </el-select>
                </el-form-item>
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="字段配置" name="fields">
              <div class="field-toolbar">
                <el-alert
                  title="查询字段可以不显示在列表；虚拟列必须选择支持虚拟字段的数据源。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <el-button type="primary" plain @click="addVirtualField">
                  <el-icon><Plus /></el-icon>添加虚拟列
                </el-button>
              </div>
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
                <el-table-column label="字段编码" min-width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.fieldCode" size="small" :disabled="!isVirtualField(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="加入列表" width="80" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.showInList" />
                  </template>
                </el-table-column>
                <el-table-column label="查询条件" width="80" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.isQuery" :disabled="!supportsQuery(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="查询方式" width="100">
                  <template #default="{ row }">
                    <el-select v-model="row.queryType" size="small" :disabled="!row.isQuery">
                      <el-option label="等于" value="EQ" />
                      <el-option label="不等于" value="NE" />
                      <el-option label="包含" value="LIKE" />
                      <el-option label="不包含" value="NOT_LIKE" />
                      <el-option label="大于" value="GT" />
                      <el-option label="大于等于" value="GE" />
                      <el-option label="小于" value="LT" />
                      <el-option label="小于等于" value="LE" />
                      <el-option label="范围" value="BETWEEN" />
                      <el-option label="包含于" value="IN" />
                      <el-option label="不包含于" value="NOT_IN" />
                      <el-option label="为空" value="EMPTY" />
                      <el-option label="非空" value="NOT_EMPTY" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="数据源" min-width="140">
                  <template #default="{ row }">
                    <el-select
                      v-model="row.dataSourceType"
                      size="small"
                      :disabled="!isVirtualField(row)"
                      @change="handleDataSourceChange(row)"
                    >
                      <el-option
                        v-for="option in dataSourceOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                        :disabled="isVirtualField(row) && option.supportsVirtualField === false"
                      />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="渲染组件" min-width="140">
                  <template #default="{ row }">
                    <el-select v-model="row.renderComponent" size="small" clearable placeholder="自动">
                      <el-option
                        v-for="option in cellComponentOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
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
                <el-table-column label="配置" width="110" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openFieldConfig(row)">高级</el-button>
                    <el-button
                      link
                      type="success"
                      :loading="row._saving"
                      @click="saveCurrentField(row)"
                    >保存</el-button>
                    <el-button
                      v-if="isVirtualField(row)"
                      link
                      type="danger"
                      @click="removeVirtualField(row)"
                    >删除</el-button>
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
                :templates="buttonTemplates"
                @save="saveListAction($event, 'TOOLBAR')"
                @upgrade-template="upgradeButtonTemplate($event, 'TOOLBAR')"
                @remove="removeListAction($event, 'TOOLBAR')"
              />
            </el-tab-pane>
            <el-tab-pane label="操作列按钮" name="rowActions">
              <ListButtonConfigPanel
                type="row"
                v-model="rowActionButtons"
                :entityCode="entityCode"
                :entityFields="entityFields"
                :templates="buttonTemplates"
                @save="saveListAction($event, 'ROW')"
                @upgrade-template="upgradeButtonTemplate($event, 'ROW')"
                @remove="removeListAction($event, 'ROW')"
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
          <el-table
            :data="previewDataList"
            v-loading="previewLoading"
            :stripe="viewConfig.table.stripe !== false"
            :border="viewConfig.table.border === true"
            :size="viewConfig.table.size || 'small'"
          >
            <el-table-column v-if="viewConfig.table.showIndex !== false" type="index" width="50" />
            <el-table-column
              v-for="field in previewListFields"
              :key="field.fieldCode"
              :label="field.fieldName"
              :width="field.width > 0 ? field.width : undefined"
              :align="field.align"
              :fixed="safeParseConfig(field.columnConfig).fixed || undefined"
              :min-width="field.width > 0 ? undefined : (safeParseConfig(field.columnConfig).minWidth || 100)"
              :show-overflow-tooltip="safeParseConfig(field.columnConfig).showOverflowTooltip !== false"
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
              :page-sizes="viewConfig.pagination.pageSizes"
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

    <el-dialog
      v-model="fieldConfigDialogVisible"
      :title="`字段高级配置：${editingField?.fieldName || ''}`"
      width="720px"
      destroy-on-close
    >
      <el-tabs v-if="editingField" v-model="activeFieldConfigTab">
        <el-tab-pane label="数据源" name="source">
          <el-alert
            :title="selectedDataSourceOption?.description || '实体字段无需额外配置'"
            type="info"
            :closable="false"
            style="margin-bottom: 12px"
          />
          <ConfigSchemaEditor
            v-model="editingDataSourceConfig"
            :schema="selectedDataSourceOption?.configSchema || []"
          />
          <el-divider>统一数据源目录</el-divider>
          <el-select
            v-model="editingField.dataSourceId"
            clearable
            filterable
            placeholder="可选：LIST_COLUMN 数据源"
            style="width: 100%"
          >
            <el-option
              v-for="source in listColumnSources"
              :key="source.id"
              :label="`${source.sourceName} (${source.sourceType})`"
              :value="source.id"
            />
          </el-select>
          <el-divider>列模板</el-divider>
          <el-select
            v-model="editingField.templateId"
            clearable
            filterable
            placeholder="复制后独立"
            style="width: 100%"
            @change="handleListTemplateChange"
          >
            <el-option
              v-for="template in listTemplates"
              :key="template.id"
              :label="`${template.templateName} (v${template.currentVersion})`"
              :value="template.id"
            />
          </el-select>
          <el-button
            v-if="editingField.templateId"
            link
            type="primary"
            style="margin-top: 8px"
            @click="upgradeListTemplate"
          >
            检查模板升级（当前 v{{ editingField.templateVersion || 1 }}）
          </el-button>
        </el-tab-pane>
        <el-tab-pane label="单元格" name="render">
          <ConfigSchemaEditor
            v-model="editingRenderConfig"
            :schema="selectedCellDescriptor?.configSchema || []"
          />
        </el-tab-pane>
        <el-tab-pane label="查询项" name="query">
          <el-form label-width="110px" size="small">
            <el-form-item label="查询组件">
              <el-select v-model="editingQueryConfig.componentType" clearable placeholder="自动匹配字段类型" style="width: 100%">
                <el-option
                  v-for="option in queryComponentOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="占位提示">
              <el-input v-model="editingQueryConfig.placeholder" />
            </el-form-item>
            <el-form-item label="默认值">
              <el-input v-model="editingQueryConfig.defaultValue" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="列展示" name="column">
          <el-form label-width="110px" size="small">
            <el-form-item label="固定位置">
              <el-select v-model="editingColumnConfig.fixed" clearable placeholder="不固定" style="width: 100%">
                <el-option label="左侧" value="left" />
                <el-option label="右侧" value="right" />
              </el-select>
            </el-form-item>
            <el-form-item label="最小宽度">
              <el-input-number v-model="editingColumnConfig.minWidth" :min="60" :max="1000" />
            </el-form-item>
            <el-form-item label="溢出提示">
              <el-switch v-model="editingColumnConfig.showOverflowTooltip" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="fieldConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFieldAdvancedConfig">保存当前列</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseDialogVisible" title="列表发布版本" width="760px">
      <el-table :data="releases" size="small">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="contentHash" label="内容哈希" min-width="220" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="120" />
        <el-table-column prop="publishedAt" label="发布时间" width="180" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'ACTIVE'"
              @click="activateRelease(row)"
            >激活</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Rank, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityApi } from '@/api/entity'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import ListButtonConfigPanel from '@/components/ListButtonConfigPanel.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import { getCellComponentOptions, getCellDescriptor } from '@/utils/listCellRegistry'
import { getCustomListComponentOptions, getCustomListDescriptor } from '@/utils/customComponentRegistry'
import { getFormFieldComponentOptions } from '@/components/form-fields'
import {
  applySchemaDefaults,
  safeParseConfig,
  stringifyConfig
} from '@/shared/config-runtime'
import { filterEntityFieldsByLifecycle } from '@/shared/entity-design'
import { uiDataSourceApi, uiComponentTemplateApi } from '@/api/uiConfig'

const route = useRoute()
const router = useRouter()
const configId = route.params.id

// 配置信息
const configInfo = ref({})
const entityName = ref('')
const entityCode = ref('')
const entityId = ref('')
const entityFields = ref([])
const entityDefinition = ref({})
const releaseDialogVisible = ref(false)
const releases = ref([])
const diffInfo = ref({ changed: true, changedSections: [] })
const unifiedDataSources = ref([])
const listTemplates = ref([])
const buttonTemplates = ref([])
const dataSourceOptions = ref([
  {
    value: 'ENTITY_FIELD',
    label: '实体字段',
    description: '直接读取实体系统字段或自定义字段。',
    supportsVirtualField: false,
    supportsQuery: true,
    configSchema: []
  }
])
const cellComponentOptions = getCellComponentOptions()
const customListOptions = getCustomListComponentOptions()
const queryComponentOptions = getFormFieldComponentOptions()

const createDefaultViewConfig = () => ({
  search: {
    defaultVisibleCount: 4,
    collapsible: true,
    labelWidth: 100
  },
  table: {
    stripe: true,
    border: false,
    showIndex: true,
    size: 'default'
  },
  pagination: {
    pageSize: 10,
    pageSizes: [10, 20, 50, 100]
  },
  customComponentProps: {}
})
const viewConfig = ref(createDefaultViewConfig())

// 字段配置列表
const fieldConfigList = ref([])
let sortableInstance = null

// 配置 Tab
const activeConfigTab = ref('view')

const selectedCustomListSchema = computed(() =>
  getCustomListDescriptor(configInfo.value.customComponent)?.configSchema || []
)

const fieldConfigDialogVisible = ref(false)
const activeFieldConfigTab = ref('source')
const editingField = ref(null)
const editingDataSourceConfig = ref({})
const editingRenderConfig = ref({})
const editingQueryConfig = ref({})
const editingColumnConfig = ref({})

const selectedDataSourceOption = computed(() =>
  dataSourceOptions.value.find(option => option.value === editingField.value?.dataSourceType)
)

const selectedCellDescriptor = computed(() =>
  getCellDescriptor(editingField.value?.renderComponent || 'DefaultText')
)

const listColumnSources = computed(() =>
  unifiedDataSources.value.filter(source => source.enabled !== false)
)

const listQuerySources = computed(() =>
  unifiedDataSources.value.filter(source => source.enabled !== false)
)

// 工具栏按钮配置
const toolbarButtons = ref([])

// 操作列按钮配置
const rowActionButtons = ref([])
const sceneItems = ref([])
const sceneSavingCodes = ref(new Set())
const sceneSortCache = new Map()
const sceneOptions = [
  { value: 'MENU', label: '菜单' },
  { value: 'PAGE', label: '页面' },
  { value: 'DIALOG', label: '弹窗' },
  { value: 'DRAWER', label: '抽屉' },
  { value: 'EMBEDDED', label: '页面嵌入' },
  { value: 'FORM_PICKER', label: '表单选择器' },
  { value: 'SUB_TABLE', label: '子表选择' }
]

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
    .filter(f => f.isQuery)
    .map(f => {
      const originField = entityFields.value.find(ef => ef.id === f.fieldId)
      const queryConfig = safeParseConfig(f.queryConfig)
      return {
        ...f,
        componentType: queryConfig.componentType || f.componentType,
        placeholder: queryConfig.placeholder || f.placeholder,
        fieldType: originField?.fieldType || f.fieldType || 'STRING',
        optionsJson: originField?.optionsJson || f.optionsJson
      }
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
    const [extensionOptions, sourceCatalog, templates, buttons] = await Promise.all([
      entityListConfigApi.getExtensionOptions().catch(() => []),
      uiDataSourceApi.list().catch(() => []),
      uiComponentTemplateApi.list({ templateType: 'LIST_COLUMN_GROUP' }).catch(() => []),
      uiComponentTemplateApi.list({ templateType: 'BUTTON_GROUP' }).catch(() => [])
    ])
    unifiedDataSources.value = Array.isArray(sourceCatalog) ? sourceCatalog : []
    listTemplates.value = Array.isArray(templates) ? templates : []
    buttonTemplates.value = Array.isArray(buttons) ? buttons : []
    if (Array.isArray(extensionOptions) && extensionOptions.length > 0) {
      dataSourceOptions.value = extensionOptions
    }

    // 加载列表配置
    const [configRes, scenes] = await Promise.all([
      entityListConfigApi.getById(configId),
      entityListConfigApi.getScenes(configId).catch(() => [])
    ])
    sceneItems.value = Array.isArray(scenes) ? scenes : []
    sceneItems.value.forEach(scene => {
      sceneSortCache.set(scene.sceneCode, scene.sortOrder)
    })
    if (configRes) {
      configInfo.value = configRes
      configInfo.value.dataScopeMode = configRes.dataScopeMode || 'INHERIT'
      configInfo.value.fixedFilterConfig = JSON.stringify(
        configRes.fixedFilterConfig || {},
        null,
        2
      )
      configInfo.value.contextBindingConfig = JSON.stringify(
        configRes.contextBindingConfig || {},
        null,
        2
      )
      configInfo.value.allowedSceneValues = sceneItems.value.length > 0
        ? sceneItems.value.map(scene => scene.sceneCode)
        : safeJsonParse(configRes.allowedScenes)
          || ['MENU', 'PAGE', 'DIALOG', 'DRAWER', 'EMBEDDED', 'FORM_PICKER', 'SUB_TABLE']
      const selectionConfig = safeJsonParse(configRes.selectionConfig) || {}
      configInfo.value.selectionMode = selectionConfig.selectionMode || 'NONE'
      configInfo.value.selectionValueField = selectionConfig.valueField || 'id'
      configInfo.value.selectionReturnMappingsText = JSON.stringify(
        selectionConfig.returnMappings || [],
        null,
        2
      )
      entityId.value = configRes.entityId
      entityCode.value = configRes.entityCode
      viewConfig.value = mergeViewConfig(safeParseConfig(configRes.viewConfig))
      previewPageSize.value = viewConfig.value.pagination.pageSize
      await loadDiff()
    }

    // 加载实体信息
    const entityRes = await entityApi.getById(entityId.value)
    if (entityRes) {
      if (entityRes.storageMode === 'SYSTEM') {
        ElMessage.warning('平台系统实体不支持动态列表设计')
        router.replace('/entity')
        return
      }
      entityDefinition.value = entityRes
      entityName.value = entityRes.entityName
      entityCode.value = entityRes.entityCode
      entityFields.value = filterEntityFieldsByLifecycle(entityRes, entityRes.fields || [])
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
      id: saved?.id,
      revision: saved?.revision || 0,
      orderKey: saved?.orderKey || (index + 1) * 1000000,
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
      dataSourceId: saved?.dataSourceId || '',
      templateId: saved?.templateId,
      templateVersion: saved?.templateVersion,
      localOverridesDocument: saved?.localOverridesDocument || '',
      renderComponent: saved?.renderComponent || '',
      formatter: saved?.formatter || '',
      columnConfig: saved?.columnConfig || '',
      queryConfig: saved?.queryConfig || '',
      renderConfig: saved?.renderConfig || '',
      sortOrder: saved?.sortOrder ?? index
    }
  })

  savedFields
    .filter(saved => !entityFields.value.some(entityField => String(entityField.id) === String(saved.fieldId)))
    .forEach((saved, index) => {
      merged.push({
        ...saved,
        id: saved.id,
        revision: saved.revision || 0,
        orderKey: saved.orderKey || (entityFields.value.length + index + 1) * 1000000,
        fieldId: saved.fieldId || `virtual_${Date.now()}_${index}`,
        fieldCode: saved.fieldCode || `virtual_${index + 1}`,
        fieldName: saved.fieldName || '虚拟列',
        fieldType: saved.fieldType || 'STRING',
        showInList: saved.showInList !== false,
        isQuery: saved.isQuery === true,
        queryType: saved.queryType || 'EQ',
        width: saved.width || 0,
        align: saved.align || 'left',
        dataSourceType: saved.dataSourceType || 'FIELD_TEMPLATE',
        dataSourceConfig: saved.dataSourceConfig || '',
        renderComponent: saved.renderComponent || '',
        formatter: saved.formatter || '',
        columnConfig: saved.columnConfig || '',
        queryConfig: saved.queryConfig || '',
        renderConfig: saved.renderConfig || '',
        sortOrder: saved.sortOrder ?? entityFields.value.length + index
      })
    })

  // 按 sortOrder 排序
  merged.sort((a, b) => a.sortOrder - b.sortOrder)
  fieldConfigList.value = merged
}

function mergeViewConfig(saved) {
  const defaults = createDefaultViewConfig()
  return {
    ...defaults,
    ...saved,
    search: { ...defaults.search, ...(saved.search || {}) },
    table: { ...defaults.table, ...(saved.table || {}) },
    pagination: { ...defaults.pagination, ...(saved.pagination || {}) },
    customComponentProps: saved.customComponentProps || {}
  }
}

function isVirtualField(field) {
  return String(field?.fieldId || '').startsWith('virtual_')
}

function supportsQuery(field) {
  const option = dataSourceOptions.value.find(item => item.value === field.dataSourceType)
  return option?.supportsQuery !== false
}

function addVirtualField() {
  const timestamp = Date.now()
  const defaultSource = dataSourceOptions.value.find(option => option.supportsVirtualField !== false)
  fieldConfigList.value.push({
    fieldId: `virtual_${timestamp}`,
    fieldCode: `virtual_${timestamp}`,
    fieldName: '虚拟列',
    fieldType: 'STRING',
    showInList: true,
    isQuery: false,
    queryType: 'EQ',
    width: 0,
    align: 'left',
    dataSourceType: defaultSource?.value || 'FIELD_TEMPLATE',
    dataSourceConfig: '',
    renderComponent: 'DefaultText',
    formatter: '',
    columnConfig: '',
    queryConfig: '',
    renderConfig: '',
    sortOrder: fieldConfigList.value.length,
    orderKey: (fieldConfigList.value.length + 1) * 1000000,
    revision: 0,
    dataSourceId: ''
  })
}

async function removeVirtualField(field) {
  if (field.id && field.revision > 0) {
    try {
      await entityListConfigApi.deleteField(
        configId,
        field.id,
        field.revision
      )
    } catch (error) {
      handleRevisionConflict(error, field)
      return
    }
  }
  fieldConfigList.value = fieldConfigList.value.filter(item => item !== field)
  await refreshConfigRevision()
  await loadDiff()
}

function handleDataSourceChange(field) {
  const option = dataSourceOptions.value.find(item => item.value === field.dataSourceType)
  if (option?.supportsQuery === false) {
    field.isQuery = false
  }
  const schema = option?.configSchema || []
  field.dataSourceConfig = stringifyConfig(applySchemaDefaults(
    schema,
    safeParseConfig(field.dataSourceConfig)
  ))
}

function openFieldConfig(field) {
  editingField.value = field
  editingDataSourceConfig.value = applySchemaDefaults(
    dataSourceOptions.value.find(item => item.value === field.dataSourceType)?.configSchema || [],
    safeParseConfig(field.dataSourceConfig)
  )
  editingRenderConfig.value = applySchemaDefaults(
    getCellDescriptor(field.renderComponent || 'DefaultText')?.configSchema || [],
    safeParseConfig(field.renderConfig)
  )
  editingQueryConfig.value = {
    componentType: '',
    placeholder: '',
    defaultValue: '',
    ...safeParseConfig(field.queryConfig)
  }
  editingColumnConfig.value = {
    fixed: '',
    minWidth: 100,
    showOverflowTooltip: true,
    ...safeParseConfig(field.columnConfig)
  }
  activeFieldConfigTab.value = 'source'
  fieldConfigDialogVisible.value = true
}

async function handleListTemplateChange(templateId) {
  if (!editingField.value || !templateId) {
    if (editingField.value) {
      editingField.value.templateVersion = null
      editingField.value.localOverridesDocument = ''
    }
    return
  }
  const template = listTemplates.value.find(item => item.id === templateId)
  const versions = await uiComponentTemplateApi.versions(templateId)
  const latest = versions.find(item => item.version === template?.currentVersion)
    || versions[0]
  if (!latest) return
  const snapshot = safeParseConfig(latest.snapshotDocument)
  Object.assign(editingField.value, snapshot.field || snapshot)
  editingField.value.templateVersion = latest.version
  editingField.value.localOverridesDocument = stringifyConfig({})
  ElMessage.success(`已锁定列模板 v${latest.version}`)
}

async function upgradeListTemplate() {
  const field = editingField.value
  if (!field?.templateId) return
  const template = listTemplates.value.find(item => item.id === field.templateId)
  if (!template || template.currentVersion === field.templateVersion) {
    ElMessage.info('当前已是最新模板版本')
    return
  }
  const result = await uiComponentTemplateApi.upgrade(field.templateId, {
    fromVersion: field.templateVersion,
    toVersion: template.currentVersion,
    currentSnapshot: normalizeFieldForSave(field),
    localOverrides: safeParseConfig(field.localOverridesDocument)
  })
  if (result.requiresConfirmation) {
    try {
      await ElMessageBox.confirm(
        `以下列配置同时被模板和本地修改：${result.conflicts.join('、')}。继续后保留本地列配置。`,
        '确认列模板升级',
        {
          type: 'warning',
          confirmButtonText: '保留本地配置并升级',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  Object.assign(field, result.mergedSnapshot?.field || result.mergedSnapshot || {})
  field.templateId = template.id
  field.templateVersion = template.currentVersion
  await saveCurrentField(field)
  ElMessage.success(`已保存列模板升级 v${template.currentVersion}`)
}

async function saveFieldAdvancedConfig() {
  if (!editingField.value) return
  editingField.value.dataSourceConfig = stringifyConfig(editingDataSourceConfig.value)
  editingField.value.renderConfig = stringifyConfig(editingRenderConfig.value)
  editingField.value.queryConfig = stringifyConfig(editingQueryConfig.value)
  editingField.value.columnConfig = stringifyConfig(editingColumnConfig.value)
  await saveCurrentField(editingField.value)
  fieldConfigDialogVisible.value = false
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
  if (typeof text === 'object') return text
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

function isSceneEnabled(sceneCode) {
  return sceneItems.value.some(scene => scene.sceneCode === sceneCode)
}

async function toggleScene(sceneCode, enabled) {
  sceneSavingCodes.value = new Set([...sceneSavingCodes.value, sceneCode])
  try {
    const current = sceneItems.value.find(scene => scene.sceneCode === sceneCode)
    if (enabled && !current) {
      await entityListConfigApi.createScene(configId, {
        sceneCode,
        sortOrder: sceneSortCache.get(sceneCode)
          ?? sceneOptions.findIndex(scene => scene.value === sceneCode)
      })
    } else if (!enabled && current) {
      sceneSortCache.set(sceneCode, current.sortOrder)
      await entityListConfigApi.deleteScene(configId, current.id, current.revision)
    }
    sceneItems.value = await entityListConfigApi.getScenes(configId)
    sceneItems.value.forEach(item => {
      sceneSortCache.set(item.sceneCode, item.sortOrder)
    })
    configInfo.value.allowedSceneValues = sceneItems.value.map(scene => scene.sceneCode)
    await refreshConfigRevision()
    await loadDiff()
    ElMessage.success(`场景“${sceneOptions.find(scene => scene.value === sceneCode)?.label || sceneCode}”已保存，尚未发布`)
  } catch (error) {
    handleRevisionConflict(error)
    sceneItems.value = await entityListConfigApi.getScenes(configId).catch(() => sceneItems.value)
  } finally {
    const next = new Set(sceneSavingCodes.value)
    next.delete(sceneCode)
    sceneSavingCodes.value = next
  }
}

function normalizeActionForSave(button, position) {
  const actionParams = {}
  for (const key of [
    'targetEntityCode',
    'targetListKey',
    'presentation',
    'selectionMode',
    'openListTitle',
    'relationKey',
    'selectionHandler'
  ]) {
    if (button[key] !== undefined && button[key] !== '') {
      actionParams[key] = button[key]
    }
  }
  return {
    expectedRevision: button.id ? button.revision : null,
    position,
    buttonKey: button.key,
    buttonType: button.type,
    buttonLabel: button.label,
    icon: button.icon || '',
    styleType: button.buttonType || 'default',
    linkMode: button.link === true,
    customMode: button.customMode || '',
    handlerCode: button.customHandler || '',
    permissionCode: button.perm || '',
    enabled: button.enabled !== false,
    unavailableBehavior: button.availabilityRule?.unavailableBehavior || '',
    sortOrder: Number(button.sort || 0),
    orderKey: (Number(button.sort || 0) + 1) * 1000000,
    actionParams,
    availabilityRule: button.availabilityRule || {},
    templateId: button.templateId || null,
    templateVersion: button.templateVersion || null,
    localOverridesDocument: button.localOverridesDocument
      || button.localOverrides
      || null,
    clearFields: button.templateId
      ? []
      : ['templateId', 'templateVersion', 'localOverridesDocument']
  }
}

async function upgradeButtonTemplate(button, position) {
  if (!button?.templateId) return
  const template = buttonTemplates.value.find(item => item.id === button.templateId)
  if (!template || template.currentVersion === button.templateVersion) {
    ElMessage.info('当前已是最新按钮模板版本')
    return
  }
  const currentSnapshot = {
    ...button
  }
  delete currentSnapshot.id
  delete currentSnapshot.revision
  delete currentSnapshot.orderKey
  delete currentSnapshot._saving
  const result = await uiComponentTemplateApi.upgrade(button.templateId, {
    fromVersion: button.templateVersion,
    toVersion: template.currentVersion,
    currentSnapshot,
    localOverrides: safeJsonParse(
      button.localOverridesDocument || button.localOverrides
    ) || {}
  })
  if (result.requiresConfirmation) {
    try {
      await ElMessageBox.confirm(
        `以下按钮配置同时被模板和本地修改：${result.conflicts.join('、')}。继续后保留本地按钮配置。`,
        '确认按钮模板升级',
        {
          type: 'warning',
          confirmButtonText: '保留本地配置并升级',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  Object.assign(button, result.mergedSnapshot?.button || result.mergedSnapshot || {})
  button.templateId = template.id
  button.templateVersion = template.currentVersion
  await saveListAction(button, position)
  ElMessage.success(`已保存按钮模板升级 v${template.currentVersion}`)
}

async function refreshListActions() {
  const latest = await entityListConfigApi.getById(configId)
  if (!latest) return
  configInfo.value.revision = latest.revision
  configInfo.value.activeReleaseId = latest.activeReleaseId
  configInfo.value.publishedVersion = latest.publishedVersion
  parseButtonConfig(latest)
}

async function saveListAction(button, position) {
  button._saving = true
  try {
    const payload = normalizeActionForSave(button, position)
    if (button.id && button.revision > 0) {
      await entityListConfigApi.patchAction(configId, button.id, payload)
    } else {
      await entityListConfigApi.createAction(configId, payload)
    }
    await refreshListActions()
    await loadDiff()
    ElMessage.success('当前按钮已保存，尚未发布')
  } catch (error) {
    handleRevisionConflict(error)
    await refreshListActions().catch(() => {})
  } finally {
    button._saving = false
  }
}

async function removeListAction(button, position) {
  try {
    if (button.id && button.revision > 0) {
      await entityListConfigApi.deleteAction(configId, button.id, button.revision)
      await refreshListActions()
    } else {
      const target = position === 'TOOLBAR' ? toolbarButtons : rowActionButtons
      target.value = target.value.filter(item => item !== button)
    }
    await loadDiff()
    ElMessage.success('当前按钮已删除，尚未发布')
  } catch (error) {
    handleRevisionConflict(error)
    await refreshListActions().catch(() => {})
  }
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
    onEnd: async (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex === newIndex) return

      const item = fieldConfigList.value.splice(oldIndex, 1)[0]
      fieldConfigList.value.splice(newIndex, 0, item)
      if (item.id && item.revision > 0) {
        try {
          const saved = await entityListConfigApi.reorderField(
            configId,
            item.id,
            {
              expectedRevision: item.revision,
              previousId: fieldConfigList.value[newIndex - 1]?.id || null,
              nextId: fieldConfigList.value[newIndex + 1]?.id || null
            }
          )
          Object.assign(item, saved)
          await refreshConfigRevision()
          await loadDiff()
        } catch (error) {
          handleRevisionConflict(error, item)
          await loadData()
        }
      }
    }
  })
}

function normalizeFieldForSave(field, index = fieldConfigList.value.indexOf(field)) {
  return {
    id: field.id,
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    showInList: field.showInList,
    isQuery: field.isQuery,
    queryType: field.queryType,
    width: field.width,
    align: field.align,
    dataSourceType: field.dataSourceType || 'ENTITY_FIELD',
    dataSourceConfig: field.dataSourceConfig || '',
    dataSourceId: field.dataSourceId || null,
    renderComponent: field.renderComponent || '',
    formatter: field.formatter || '',
    columnConfig: field.columnConfig || '',
    queryConfig: field.queryConfig || '',
    renderConfig: field.renderConfig || '',
    sortOrder: Math.max(0, index),
    orderKey: field.orderKey || (Math.max(0, index) + 1) * 1000000,
    templateId: field.templateId || null,
    templateVersion: field.templateVersion || null,
    localOverridesDocument: field.localOverridesDocument || null
  }
}

function handleRevisionConflict(error, target) {
  if (error?.status === 409 || error?.errorCode === 'CONFIG_REVISION_CONFLICT') {
    ElMessage.warning('配置已被其他人修改，已切换为服务器当前版本')
    if (target && error.currentData) {
      Object.assign(target, error.currentData)
    }
    return true
  }
  ElMessage.error(error?.message || '保存失败')
  return false
}

async function saveCurrentField(field) {
  if (!field) return
  field._saving = true
  try {
    const payload = normalizeFieldForSave(field)
    const saved = field.id && field.revision > 0
      ? await entityListConfigApi.patchField(
          configId,
          field.id,
          field.revision,
          payload
        )
      : await entityListConfigApi.createField(configId, payload)
    Object.assign(field, saved)
    await refreshConfigRevision()
    await loadDiff()
    ElMessage.success('当前列已保存，尚未发布')
  } catch (error) {
    handleRevisionConflict(error, field)
  } finally {
    field._saving = false
  }
}

async function refreshConfigRevision() {
  const latest = await entityListConfigApi.getById(configId)
  if (latest) {
    configInfo.value.revision = latest.revision
    configInfo.value.activeReleaseId = latest.activeReleaseId
    configInfo.value.publishedVersion = latest.publishedVersion
  }
}

async function saveListMetadata() {
  try {
    const saved = await entityListConfigApi.patchMetadata(configId, {
      expectedRevision: configInfo.value.revision,
      listName: configInfo.value.listName,
      description: configInfo.value.description,
      isDefault: configInfo.value.isDefault,
      customComponent: configInfo.value.customComponent,
      dataScopeMode: configInfo.value.dataScopeMode || 'INHERIT',
      accessPermissionCode: configInfo.value.accessPermissionCode || '',
      selectionConfig: {
        selectionMode: configInfo.value.selectionMode || 'NONE',
        valueField: configInfo.value.selectionValueField || 'id',
        returnMappings: safeJsonParse(
          configInfo.value.selectionReturnMappingsText
        ) || []
      },
      fixedFilterConfig: safeJsonParse(configInfo.value.fixedFilterConfig) || {},
      contextBindingConfig: safeJsonParse(configInfo.value.contextBindingConfig) || {},
      viewConfig: viewConfig.value,
      queryProviderCode: configInfo.value.queryProviderCode || '',
      queryDataSourceId: configInfo.value.queryDataSourceId || null
    })
    configInfo.value.revision = saved.revision
    await loadDiff()
    ElMessage.success('列表设置已保存，尚未发布')
  } catch (error) {
    handleRevisionConflict(error, configInfo.value)
  }
}

async function loadDiff() {
  try {
    diffInfo.value = await entityListConfigApi.getDiff(configId)
  } catch {
    diffInfo.value = { changed: true, changedSections: [] }
  }
}

async function handlePublish() {
  try {
    const diff = await entityListConfigApi.getDiff(configId)
    if (!diff.changed) {
      ElMessage.info('当前草稿与已发布版本一致')
      return
    }
    await ElMessageBox.confirm(
      `将发布 ${describePublishChanges(diff)}，运行时会原子切换。`,
      '发布列表',
      { type: 'warning' }
    )
    await entityListConfigApi.publish(configId, '列表设计器发布')
    await loadDiff()
    await loadPreviewData()
    ElMessage.success('列表发布成功')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '发布失败')
    }
  }
}

function describePublishChanges(diff) {
  const labels = (diff.changedItems || [])
    .slice(0, 8)
    .map(item => `${changeTypeLabel(item.changeType)}${item.label || item.id}`)
  if (labels.length) {
    const remaining = Math.max(0, (diff.changedItems?.length || 0) - labels.length)
    return `${labels.join('、')}${remaining ? `等 ${remaining + labels.length} 项` : ''}`
  }
  return diff.changedSections?.join('、') || '当前列表草稿'
}

function changeTypeLabel(changeType) {
  return {
    ADDED: '新增：',
    UPDATED: '修改：',
    MOVED: '移动：',
    REMOVED: '删除：'
  }[changeType] || '修改：'
}

async function showReleaseHistory() {
  releases.value = await entityListConfigApi.getReleases(configId)
  releaseDialogVisible.value = true
}

async function activateRelease(release) {
  await ElMessageBox.confirm(
    `确认激活历史版本 v${release.version}？`,
    '回滚发布版本',
    { type: 'warning' }
  )
  await entityListConfigApi.activateRelease(configId, release.id)
  await showReleaseHistory()
  await loadDiff()
  await loadPreviewData()
  ElMessage.success('历史版本已激活')
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
    const res = await entityListRuntimeApi.query(
      entityCode.value,
      configInfo.value?.listKey,
      {
        pageNum: previewPageNum.value,
        pageSize: previewPageSize.value,
        scene: 'PAGE',
        filters: params
      }
    )
    previewDataList.value = res?.records || res?.list || []
    previewAllData.value = previewDataList.value
    previewTotal.value = Number(res?.total || previewDataList.value.length)
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

.view-config-form {
  max-width: 760px;
}

.field-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.field-toolbar :deep(.el-alert) {
  flex: 1;
}

.config-tabs :deep(.el-tabs__nav-scroll) {
  overflow-x: auto;
  scrollbar-width: thin;
}

.config-tabs :deep(.el-tabs__nav) {
  min-width: max-content;
}

.config-tabs :deep(.el-tabs__item) {
  white-space: nowrap;
}

.option-description,
.unit-text {
  color: #909399;
  font-size: 12px;
}
.scene-options {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  width: 100%;
}

.unit-text {
  margin-left: 6px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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

@media (max-width: 960px) {
  .page-header,
  .header-left,
  .header-actions,
  .field-toolbar {
    flex-wrap: wrap;
  }

  .design-container {
    display: block;
    overflow-y: auto;
    padding: 8px;
  }

  .config-panel,
  .preview-panel {
    width: 100%;
    min-width: 0;
    overflow: visible;
  }

  .preview-panel {
    margin-top: 12px;
  }

  .field-toolbar {
    align-items: stretch;
  }

  .view-config-form :deep(.el-form-item__content) {
    min-width: 0;
  }

  .view-config-form :deep(.el-select),
  .view-config-form :deep(.el-input) {
    max-width: 100%;
  }
}
</style>
