<template>
  <div class="node-data-settings">
    <SettingsSection
      v-if="isFieldNode"
      v-show="activeNodeSettingsTab === 'data'"
      title="字段数据"
      description="静态初始值与受控动态数据使用同一字段配置"
      :collapsible="false"
      primary
    >
      <el-form-item label="静态默认值">
        <el-input
          v-model="selectedField.defaultValue"
          placeholder="留空表示不设置静态默认值"
        />
        <div class="form-tip">
          需要从接口、实体或 Provider 动态取得默认值时，请在下方配置“字段默认值”数据源。
        </div>
      </el-form-item>
    </SettingsSection>

    <SettingsSection
      v-if="canConfigureSelectedNodeDataSource"
      v-show="activeNodeSettingsTab === 'data'"
      title="数据源绑定"
      description="按用途分别配置选项、默认值、计算、加载和提交处理"
    >
      <template #summary>
        <el-tag
          size="small"
          :type="selectedNodeDataSourceBindingCount ? 'success' : 'info'"
        >
          {{ selectedNodeDataSourceBindingCount
            ? `${selectedNodeDataSourceBindingCount} 项`
            : '未配置' }}
        </el-tag>
      </template>

      <div class="data-source-usage-list">
        <button
          v-for="usage in availableNodeDataSourceUsages"
          :key="usage.value"
          type="button"
          class="data-source-usage-row"
          :class="{ active: selectedField.dataSourceUsage === usage.value }"
          @click="selectNodeDataSourceUsage(usage.value)"
        >
          <span>{{ usage.label }}</span>
          <el-tag
            size="small"
            :type="isNodeDataSourceUsageConfigured(usage.value) ? 'success' : 'info'"
            effect="plain"
          >
            {{ isNodeDataSourceUsageConfigured(usage.value) ? '已配置' : '未配置' }}
          </el-tag>
        </button>
      </div>

      <div class="data-source-editor-heading">
        <strong>{{ selectedNodeDataSourceUsageLabel }}</strong>
        <el-button
          v-if="selectedField.dataSourceId"
          link
          type="danger"
          @click="clearSelectedNodeDataSourceBinding"
        >
          清除当前绑定
        </el-button>
      </div>
      <el-form-item label="数据源">
        <el-select
          v-model="selectedField.dataSourceId"
          clearable
          filterable
          placeholder="不绑定"
          style="width: 100%"
        >
          <el-option
            v-for="source in dataSources"
            :key="source.id"
            :label="`${source.sourceName} (${source.sourceType})`"
            :value="source.id"
          />
        </el-select>
        <div class="form-tip">仅可选择受控实体、字典、Provider 或 Connector。</div>
      </el-form-item>
      <details class="property-advanced">
        <summary>输入与输出映射</summary>
        <div class="property-advanced-body">
          <el-form-item label="输入映射">
            <template #label>
              <JsonConfigLabel
                label="输入映射"
                help-key="entityForm.dataSourceInputMapping"
              />
            </template>
            <el-input
              v-model="selectedField.dataSourceInputMappingText"
              type="textarea"
              :rows="3"
              placeholder='{"filters.ownerId":"data.ownerId"}'
            />
            <div class="form-tip">
              目标路径映射到 data/context/input 路径；也可使用 {"literal": 值}。
            </div>
          </el-form-item>
          <el-form-item label="输出映射">
            <template #label>
              <JsonConfigLabel
                label="输出映射"
                help-key="entityForm.dataSourceOutputMapping"
              />
            </template>
            <el-input
              v-model="selectedField.dataSourceOutputMappingText"
              type="textarea"
              :rows="3"
              placeholder='{"assigneeName":"data.user.name"}'
            />
            <div class="form-tip">
              目标字段映射到数据源返回路径；留空时使用原始返回值。
            </div>
          </el-form-item>
        </div>
      </details>
    </SettingsSection>

    <SettingsSection
      v-if="canConfigureSelectedNodeRelations"
      v-show="activeNodeSettingsTab === 'data'"
      title="实体关系与子表"
      description="业务关系只读展示，允许配置子表展示或引用选择方式"
    >
      <template #summary>
        <el-tag size="small" type="info">
          {{ isSubFormField(selectedField)
            ? '子表关系'
            : isSubListField(selectedField)
              ? '子列表'
              : '实体引用' }}
        </el-tag>
      </template>

      <template v-if="isSubFormField(selectedField)">
        <div class="relation-summary">
          <div>
            <span>子实体</span>
            <strong>
              {{ getEntityNameById(selectedField.childEntityId || selectedField.refEntityId) || '-' }}
            </strong>
          </div>
          <div>
            <span>关系</span>
            <strong>
              {{ selectedField.relationType === 'ONE_TO_ONE' ? '一对一' : '一对多' }}
            </strong>
          </div>
          <div>
            <span>外键</span>
            <strong>
              {{ selectedField.childRefFieldCode || selectedField.refFieldCode || '-' }}
            </strong>
          </div>
        </div>

        <el-form-item label="布局">
          <template #label>
            <ConfigHelpLabel
              label="布局"
              help-key="formNode.subFormLayout"
            />
          </template>
          <el-radio-group v-model="selectedField.layout">
            <el-radio-button value="form">分行</el-radio-button>
            <el-radio-button value="table">表格</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="子表表单">
          <el-select
            v-model="selectedField.refFormId"
            placeholder="默认表单"
            clearable
            style="width: 100%"
            @change="handleChildFormChange"
          >
            <el-option
              v-for="fm in formListByEntity"
              :key="fm.id"
              :label="fm.formName"
              :value="fm.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发布版本">
          <el-select
            v-model="selectedField.childFormReleaseId"
            placeholder="选择已发布版本"
            clearable
            filterable
            :disabled="!selectedField.refFormId"
            :loading="childFormReleaseLoading"
            style="width: 100%"
            @change="handleChildFormReleaseChange"
          >
            <el-option
              v-for="release in childFormReleases"
              :key="release.id"
              :label="formatChildFormReleaseLabel(release)"
              :value="release.id"
            />
          </el-select>
          <div class="form-tip">
            运行时固定读取所选 release 快照；子表单草稿不会影响已发布父表单。
          </div>
        </el-form-item>
        <div class="property-subheading">参数传递</div>
        <SubFormParameterMappingEditor
          v-model="selectedParameterContract"
          :parameter-options="selectedChildInputParameters"
          :child-field-options="selectedChildFieldOptions"
          :parent-fields="entityFields"
          :child-ref-field-code="selectedField.childRefFieldCode || selectedField.refFieldCode || ''"
        />
      </template>

      <template v-if="isSubListField(selectedField)">
        <div class="relation-summary">
          <div>
            <span>目标实体</span>
            <strong>
              {{ getEntityNameById(selectedField.refEntityId) || '-' }}
            </strong>
          </div>
          <div>
            <span>运行方式</span>
            <strong>已发布列表</strong>
          </div>
        </div>

        <el-form-item label="目标列表">
          <el-select
            v-model="selectedField.refListKey"
            placeholder="选择已发布列表"
            filterable
            style="width: 100%"
            :disabled="!selectedField.refEntityId"
            @change="handleSubListChange"
          >
            <el-option
              v-for="list in subListOptions"
              :key="list.listKey"
              :label="`${list.listName || list.listKey} (${list.listKey})`"
              :value="list.listKey"
            />
          </el-select>
          <div class="form-tip">
            仅可选择允许“嵌入”场景的已发布列表。运行时复用其字段、排序、数据范围和访问权限，不向父表单写入列表数据。
          </div>
        </el-form-item>
        <el-form-item label="参数传递" class="sub-list-parameter-item">
          <SubListParameterMappingEditor
            v-model="selectedSubListParameterContract"
            :target-fields="subListTargetFields"
            :parent-fields="entityFields"
            :parent-entity-id="entityInfo.id || ''"
            v-loading="subListTargetFieldsLoading"
          />
        </el-form-item>
        <el-form-item label="显示查询">
          <el-switch v-model="selectedField.subListShowSearch" />
        </el-form-item>
        <el-form-item label="显示分页">
          <el-switch v-model="selectedField.subListShowPagination" />
        </el-form-item>
        <el-form-item label="显示工具栏">
          <el-switch v-model="selectedField.subListShowToolbar" />
          <div class="form-tip">
            复用目标列表已发布的工具栏按钮及其权限配置；新增时会自动带入上方参数。
          </div>
        </el-form-item>
        <el-form-item label="显示操作列">
          <el-switch v-model="selectedField.subListShowRowActions" />
          <div class="form-tip">
            复用目标列表已发布的查看、编辑、审批、删除和自定义操作，仍受动作权限控制。
          </div>
        </el-form-item>
        <el-form-item label="每页条数">
          <el-input-number
            v-model="selectedField.subListPageSize"
            :min="1"
            :max="200"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="最大高度">
          <el-input-number
            v-model="selectedField.subListMaxHeight"
            :min="120"
            :max="2000"
            :step="20"
            controls-position="right"
          />
          <span class="number-unit">px</span>
        </el-form-item>
      </template>

      <template v-if="isReferenceFieldNode">
        <el-form-item label="引用类型">
          <el-select
            v-model="selectedField.refEntityType"
            :disabled="!!selectedField.fieldId"
            placeholder="选择引用类型"
            style="width: 100%"
          >
            <el-option label="用户自定义实体" value="CUSTOM" />
            <el-option label="系统用户" value="USER" />
            <el-option label="系统部门" value="DEPT" />
            <el-option label="系统角色" value="ROLE" />
            <el-option label="系统用户组" value="GROUP" />
            <el-option label="系统菜单" value="MENU" />
            <el-option label="系统字典" value="DICT" />
            <el-option label="系统字典项" value="DICT_ITEM" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="(selectedField.refEntityType || '').toUpperCase() === 'CUSTOM'"
          label="目标实体"
        >
          <EntityDefinitionPicker
            v-model="selectedField.refEntityId"
            :disabled="!!selectedField.fieldId"
            placeholder="选择目标实体"
            value-key="id"
            title="选择目标实体"
            :query="{ status: 'PUBLISHED' }"
            @selected="handleReferenceEntitySelected"
            @resolved="rememberEntityOption"
          />
          <div class="form-tip">
            {{ getEntityReferenceSelectionHint(selectedField.fieldType || selectedField.componentType) }}
          </div>
          <div v-if="selectedField.refEntityId" class="form-tip">
            当前目标：{{ getEntityNameById(selectedField.refEntityId) }}
          </div>
        </el-form-item>
        <el-form-item
          v-if="(selectedField.refEntityType || '').toUpperCase() === 'CUSTOM'"
          label="选择列表"
        >
          <el-select
            v-model="selectedField.refListKey"
            clearable
            placeholder="留空使用旧选择器"
            style="width: 100%"
          >
            <el-option
              v-for="list in referenceListOptions"
              :key="list.listKey"
              :label="`${list.listName || list.listKey} (${list.listKey})`"
              :value="list.listKey"
            />
          </el-select>
          <div class="form-tip">
            配置后使用统一列表运行时，字段、范围、排序和选择模式均继承该 listKey。
          </div>
        </el-form-item>
        <el-form-item label="兼容接口">
          <el-button plain @click="openLinkageConfig('value-calculation')">
            配置历史接口
          </el-button>
          <div class="form-tip">
            历史 apiUrl、apiParams 和 apiResultField 继续保留；新配置推荐使用上方受控数据源。
          </div>
        </el-form-item>
      </template>
    </SettingsSection>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import SubFormParameterMappingEditor from './SubFormParameterMappingEditor.vue'
import SubListParameterMappingEditor from './SubListParameterMappingEditor.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from './context'
import { safeParseConfig, stringifyConfig } from '@/shared/config-runtime'
import { normalizeSubListParameterContract } from '@/shared/sub-list'
import {
  getInputParameterDefinitions,
  getPublishedFormFields,
  getPublishedFormParameterSchema,
  normalizeSubFormParameterContract
} from '@/shared/subform-parameter-contract'

const context = inject(FORM_DESIGNER_CONTEXT_KEY)

if (!context) {
  throw new Error('FormNodeDataSettings requires form designer context')
}

const {
  selectedField,
  entityInfo,
  entityFields,
  activeNodeSettingsTab,
  isFieldNode,
  canConfigureSelectedNodeDataSource,
  selectedNodeDataSourceBindingCount,
  availableNodeDataSourceUsages,
  isNodeDataSourceUsageConfigured,
  selectNodeDataSourceUsage,
  selectedNodeDataSourceUsageLabel,
  dataSources,
  clearSelectedNodeDataSourceBinding,
  canConfigureSelectedNodeRelations,
  isSubFormField,
  isSubListField,
  getEntityNameById,
  formListByEntity,
  handleChildFormChange,
  childFormReleases,
  childFormReleaseLoading,
  handleChildFormReleaseChange,
  formatChildFormReleaseLabel,
  subListOptions,
  subListTargetFields,
  subListTargetFieldsLoading,
  handleSubListChange,
  isReferenceFieldNode,
  handleReferenceEntitySelected,
  rememberEntityOption,
  getEntityReferenceSelectionHint,
  referenceListOptions,
  openLinkageConfig
} = context

const selectedChildRelease = computed(() =>
  childFormReleases.value.find(item =>
    String(item?.id) === String(selectedField.value?.childFormReleaseId)
  ) || null
)

const selectedChildInputParameters = computed(() =>
  getInputParameterDefinitions(
    getPublishedFormParameterSchema(
      selectedChildRelease.value?.snapshotDocument
    )
  )
)

const selectedChildFieldOptions = computed(() =>
  getPublishedFormFields(selectedChildRelease.value?.snapshotDocument)
    .filter(field =>
      !['SUB_FORM', 'SUB_LIST'].includes(
        String(field.fieldType || '').toUpperCase()
      )
    )
)

const selectedParameterContract = computed({
  get() {
    const componentProps = safeParseConfig(
      selectedField.value?.componentProps
    )
    return normalizeSubFormParameterContract(
      componentProps.subFormConfig?.parameterContract
    )
  },
  set(value) {
    if (!selectedField.value) return
    const componentProps = safeParseConfig(
      selectedField.value.componentProps
    )
    selectedField.value.componentProps = stringifyConfig({
      ...componentProps,
      subFormConfig: {
        ...(componentProps.subFormConfig || {}),
        parameterContract: normalizeSubFormParameterContract(value)
      }
    })
  }
})

const selectedSubListParameterContract = computed({
  get() {
    const componentProps = safeParseConfig(
      selectedField.value?.componentProps
    )
    return normalizeSubListParameterContract(
      componentProps.subListConfig?.parameterContract
    )
  },
  set(value) {
    if (!selectedField.value) return
    const componentProps = safeParseConfig(
      selectedField.value.componentProps
    )
    selectedField.value.componentProps = stringifyConfig({
      ...componentProps,
      subListConfig: {
        ...(componentProps.subListConfig || {}),
        parameterContract: normalizeSubListParameterContract(value)
      }
    })
  }
})
</script>

<style scoped>
.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.number-unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.sub-list-parameter-item :deep(.el-form-item__content) {
  min-width: 0;
}

.property-advanced {
  margin: 4px 0 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.property-advanced > summary {
  padding: 10px 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
}

.property-advanced-body {
  padding: 12px 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  background: #fff;
}

.data-source-usage-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.data-source-usage-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: #fff;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.data-source-usage-row:hover,
.data-source-usage-row.active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.data-source-usage-row.active {
  background: var(--el-color-primary-light-9);
}

.data-source-editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 8px;
}

.relation-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: 8px 0 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.relation-summary div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  line-height: 20px;
}

.relation-summary span {
  color: #909399;
}

.relation-summary strong {
  max-width: 150px;
  overflow: hidden;
  color: #303133;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.property-subheading {
  padding-top: 14px;
  margin: 14px 0 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}
</style>
