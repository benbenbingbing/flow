<template>
  <div class="selection-mapping-editor">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="引用字段仍保存实体 ID；以下规则只回填当前表单的其他字段，并随表单发布版本生效。"
    />

    <div class="mapping-toolbar">
      <div>
        <strong>{{ fieldLabel }}</strong>
        <span>选择数据后批量回填</span>
      </div>
      <el-button type="primary" plain @click="addMapping">
        <el-icon><Plus /></el-icon>
        添加映射
      </el-button>
    </div>

    <el-empty
      v-if="!loading && mappings.length === 0"
      description="尚未配置选择后回填"
      :image-size="72"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="mappings"
      border
      row-key="rowKey"
    >
      <el-table-column label="来源字段" min-width="210">
        <template #default="{ row }">
          <el-select
            v-model="row.sourcePath"
            filterable
            placeholder="选择关联实体字段"
            style="width: 100%"
            @change="syncSourceType(row)"
          >
            <el-option
              v-for="option in sourceFields"
              :key="option.value"
              :label="`${option.label} (${option.fieldCode})`"
              :value="option.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="目标字段" min-width="210">
        <template #default="{ row }">
          <el-select
            v-model="row.targetPath"
            filterable
            placeholder="选择当前表单字段"
            style="width: 100%"
            @change="syncTargetType(row)"
          >
            <el-option
              v-for="option in targetFields"
              :key="option.value"
              :label="`${option.label} (${option.fieldCode})`"
              :value="option.value"
              :disabled="!compatible(row.sourceType, option.fieldType)"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="覆盖策略" width="150">
        <template #header>
          <ConfigHelpLabel
            label="覆盖策略"
            help-key="entitySelection.overwrite"
          />
        </template>
        <template #default="{ row }">
          <el-select v-model="row.overwrite">
            <el-option label="始终覆盖" value="ALWAYS" />
            <el-option label="仅空值覆盖" value="IF_EMPTY" />
            <el-option label="覆盖前确认" value="CONFIRM" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="来源为空" width="150">
        <template #header>
          <ConfigHelpLabel
            label="来源为空"
            help-key="entitySelection.clearOnEmpty"
          />
        </template>
        <template #default="{ row }">
          <el-select v-model="row.clearOnEmpty">
            <el-option label="清空目标字段" :value="true" />
            <el-option label="保留原值" :value="false" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="72" align="center">
        <template #default="{ $index }">
          <el-button
            circle
            type="danger"
            title="删除回填映射"
            @click="mappings.splice($index, 1)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mapping-footer">
      <span>保存的是当前表单草稿，发布或热发布后运行时才会生效。</span>
      <el-button
        type="primary"
        :loading="saving"
        @click="save"
      >
        保存回填配置
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getEntityFields } from '@/api/entityForm'
import { uiEventBindingApi } from '@/api/uiConfig'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  areEntitySelectionTypesCompatible,
  buildEntitySelectionSourceFields,
  buildEntitySelectionTargetFields,
  entitySelectionMappings,
  mergeEntitySelectionMappings,
  parseBindingSteps,
  resolveEntitySelectionRefConfig
} from '@/shared/entity-selection-mapping'

const props = defineProps({
  formId: { type: [String, Number], required: true },
  field: { type: Object, required: true },
  formFields: { type: Array, default: () => [] }
})

const emit = defineEmits(['changed'])

const loading = ref(false)
const saving = ref(false)
const binding = ref(null)
const referencedEntityFields = ref([])
const mappings = ref([])
let rowSequence = 0

const refConfig = computed(() =>
  resolveEntitySelectionRefConfig(props.field))

const fieldLabel = computed(() =>
  props.field?.fieldLabel
  || props.field?.fieldName
  || props.field?.fieldCode)

const sourceFields = computed(() =>
  buildEntitySelectionSourceFields(
    refConfig.value.refEntityType,
    referencedEntityFields.value
  ))

const targetFields = computed(() =>
  buildEntitySelectionTargetFields(
    props.formFields,
    props.field?.fieldCode
  ))

function compatible(sourceType, targetType) {
  return !sourceType
    || areEntitySelectionTypesCompatible(sourceType, targetType)
}

function createRow(value = {}) {
  return {
    rowKey: `selection_mapping_${++rowSequence}`,
    sourcePath: '',
    targetPath: '',
    sourceType: '',
    targetType: '',
    overwrite: 'ALWAYS',
    clearOnEmpty: true,
    transform: 'IDENTITY',
    separator: ',',
    ...value
  }
}

function addMapping() {
  mappings.value.push(createRow())
}

function syncSourceType(row) {
  const source = sourceFields.value.find(
    item => item.value === row.sourcePath)
  row.sourceType = source?.fieldType || 'STRING'
  if (row.targetPath
      && !compatible(row.sourceType, row.targetType)) {
    row.targetPath = ''
    row.targetType = ''
  }
}

function syncTargetType(row) {
  const target = targetFields.value.find(
    item => item.value === row.targetPath)
  row.targetType = target?.fieldType || 'STRING'
}

async function load() {
  loading.value = true
  try {
    const [bindings, fields] = await Promise.all([
      uiEventBindingApi.list('FORM', String(props.formId)),
      String(refConfig.value.refEntityType).toUpperCase() === 'CUSTOM'
        && refConfig.value.refEntityId
        ? getEntityFields(refConfig.value.refEntityId)
        : Promise.resolve([])
    ])
    binding.value = (Array.isArray(bindings) ? bindings : [])
      .find(item =>
        String(item.targetType || '').toUpperCase() === 'FIELD'
        && String(item.targetKey || '') === String(props.field?.fieldCode || '')
        && String(item.eventCode || '').toUpperCase() === 'ENTITY_SELECTED')
      || null
    referencedEntityFields.value = Array.isArray(fields) ? fields : []
    mappings.value = entitySelectionMappings(binding.value)
      .map(createRow)
    mappings.value.forEach(row => {
      const source = sourceFields.value.find(
        item => item.value === row.sourcePath)
      const target = targetFields.value.find(
        item => item.value === row.targetPath)
      row.sourceType = source?.fieldType || row.sourceType || ''
      row.targetType = target?.fieldType || row.targetType || ''
    })
  } catch (error) {
    ElMessage.error(error.message || '加载选择后回填配置失败')
  } finally {
    loading.value = false
  }
}

function validate() {
  const targets = new Set()
  for (const row of mappings.value) {
    if (!row.sourcePath || !row.targetPath) {
      ElMessage.warning('每条回填规则都必须选择来源字段和目标字段')
      return false
    }
    if (targets.has(row.targetPath)) {
      ElMessage.warning('同一个目标字段只能配置一条回填规则')
      return false
    }
    targets.add(row.targetPath)
    if (!compatible(row.sourceType, row.targetType)) {
      ElMessage.warning('存在类型不兼容的回填规则，请重新选择目标字段')
      return false
    }
  }
  return true
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    const steps = mergeEntitySelectionMappings(
      parseBindingSteps(binding.value),
      mappings.value
    )
    if (!steps.length && binding.value) {
      await uiEventBindingApi.remove(
        binding.value.id,
        binding.value.revision
      )
    } else if (steps.length) {
      const payload = {
        expectedRevision: binding.value?.revision ?? null,
        ownerType: 'FORM',
        ownerId: String(props.formId),
        targetType: 'FIELD',
        targetKey: String(props.field?.fieldCode || ''),
        eventCode: 'ENTITY_SELECTED',
        inheritanceMode:
          binding.value?.inheritanceMode || 'INHERIT',
        enabled: binding.value?.enabled !== false,
        steps
      }
      binding.value = binding.value
        ? await uiEventBindingApi.update(binding.value.id, payload)
        : await uiEventBindingApi.create(payload)
    }
    ElMessage.success('选择后回填已保存，发布表单配置后生效')
    await load()
    emit('changed', mappings.value.length)
  } catch (error) {
    ElMessage.error(error.message || '保存选择后回填配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.selection-mapping-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mapping-toolbar,
.mapping-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.mapping-toolbar > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mapping-toolbar span,
.mapping-footer span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mapping-footer {
  padding-top: 4px;
}
</style>
