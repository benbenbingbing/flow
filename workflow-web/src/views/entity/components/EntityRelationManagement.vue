<template>
  <section class="relation-management">
    <header class="relation-header">
      <div>
        <h2>实体关系</h2>
        <p>选择关联实体和实际关联字段。一对一在表单中显示关联表单，一对多显示关联列表。</p>
      </div>
      <div class="relation-actions">
        <el-button :loading="loading" @click="loadRelations">刷新</el-button>
        <el-button
          v-if="canManage && !readonlyEntity"
          type="primary"
          @click="openCreate"
        >
          新增关系
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="readonlyEntity"
      title="平台系统实体不能配置聚合关系"
      description="系统实体结构由平台维护，仅支持查看字段目录。"
      type="warning"
      :closable="false"
      show-icon
      class="relation-alert"
    />
    <el-alert
      v-else-if="!canManage"
      title="当前账号仅有查看权限"
      description="需要 entity:definition:manage 权限才能新增、编辑或删除实体关系。"
      type="info"
      :closable="false"
      show-icon
      class="relation-alert"
    />

    <PageState
      v-if="loadError"
      type="error"
      title="实体关系加载失败"
      :description="loadError"
      retryable
      @retry="loadRelations"
    />

    <el-table
      v-else-if="relations.length"
      v-loading="loading"
      :data="relations"
      border
      stripe
      row-key="id"
      class="relation-table"
    >
      <el-table-column label="关系" min-width="220" fixed="left">
        <template #default="{ row }">
          <div class="primary-text">{{ row.relationName }}</div>
          <div class="secondary-text">{{ row.relationCode }}</div>
          <el-tag
            v-if="row.parentFieldCode"
            size="small"
            type="warning"
            effect="plain"
            class="legacy-tag"
          >
            兼容字段 {{ row.parentFieldCode }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内部数据键" min-width="150">
        <template #default="{ row }">
          <code>{{ row.dataKey }}</code>
        </template>
      </el-table-column>
      <el-table-column label="关联实体 / 关联字段" min-width="230">
        <template #default="{ row }">
          <div class="primary-text">
            {{ row.childEntityName || row.childEntityCode || row.childEntityId }}
          </div>
          <div class="secondary-text">
            {{ row.childEntityCode || row.childEntityId }} · {{ row.childRefFieldCode }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="基数" width="100" align="center">
        <template #default="{ row }">
          {{ relationTypeLabel(row.relationType) }}
        </template>
      </el-table-column>
      <el-table-column label="表单中展示" width="120" align="center">
        <template #default="{ row }">
          {{ row.relationType === 'ONE_TO_ONE' ? '关联表单' : '关联列表' }}
        </template>
      </el-table-column>
      <el-table-column label="所有权" width="110" align="center">
        <template #default="{ row }">
          <el-tag
            :type="row.ownershipType === 'COMPOSITION' ? 'primary' : 'info'"
            effect="plain"
          >
            {{ ownershipTypeLabel(row.ownershipType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="约束" min-width="180">
        <template #default="{ row }">
          <div class="flag-list">
            <el-tag v-if="row.required" type="danger" size="small" effect="plain">必填</el-tag>
            <el-tag v-if="row.cascadeDelete" type="warning" size="small" effect="plain">级联删除</el-tag>
            <span v-if="!row.required && !row.cascadeDelete">-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-else-if="!loading && !readonlyEntity"
      description="尚未定义实体关系"
      class="relation-empty"
    >
      <template #description>
        <p>先定义关系并发布实体，再到表单设计左侧“实体关系”选择要嵌入的表单或列表。</p>
      </template>
      <el-button v-if="canManage" type="primary" @click="openCreate">新增关系</el-button>
    </el-empty>

    <el-dialog
      v-model="editorVisible"
      :title="isEditing ? '编辑实体关系' : '新增实体关系'"
      width="min(760px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="resetEditor"
    >
      <el-alert
        v-if="isEditing"
        title="稳定标识创建后不可修改"
        description="关系编码和聚合数据键会被表单、版本配置及外部接口引用；如需改变语义，请新建关系并迁移引用。"
        type="info"
        :closable="false"
        show-icon
        class="editor-alert"
      />
      <el-form
        ref="editorFormRef"
        :model="editor"
        :rules="editorRules"
        label-width="120px"
        status-icon
      >
        <SettingsSection
          title="关系名称"
          description="给这项关联起一个便于表单设计时识别的名称"
          :collapsible="false"
          primary
        >
          <el-form-item label="关系名称" prop="relationName" required>
            <el-input
              v-model="editor.relationName"
              maxlength="200"
              show-word-limit
              placeholder="例如：订单明细"
            />
          </el-form-item>
        </SettingsSection>

        <SettingsSection
          title="内部标识（自动生成）"
          description="无需创建同名字段；仅接口集成需要自定义时展开"
          :default-expanded="false"
        >
          <el-form-item label="关系编码" prop="relationCode">
            <el-input
              v-model="editor.relationCode"
              :disabled="isEditing"
              maxlength="100"
              placeholder="留空由系统自动生成"
            />
            <div class="form-tip">这是关系自身的标识，不是实体字段。删除后编码不能复用。</div>
          </el-form-item>
          <el-form-item label="内部数据键" prop="dataKey">
            <el-input
              v-model="editor.dataKey"
              :disabled="isEditing"
              maxlength="100"
              placeholder="留空自动生成，不与当前实体字段重名"
            />
            <div class="form-tip">用于承载关联结果，无需在任一实体中添加同名字段。实际关联使用下方选择的关联字段。</div>
          </el-form-item>
        </SettingsSection>

        <SettingsSection
          title="数据怎么关联"
          description="选择目标实体，再选择其中保存当前记录 ID 的字段"
          :collapsible="false"
        >
          <el-form-item label="关联实体" prop="childEntityId" required>
            <EntityDefinitionPicker
              v-model="editor.childEntityId"
              value-key="id"
              title="选择关联实体"
              placeholder="请选择要展示数据的实体"
              :query="{ storageMode: 'DYNAMIC', status: 'PUBLISHED' }"
              :exclude-values="[String(entityId)]"
              @change="handleChildEntityChange"
            />
          </el-form-item>
          <el-form-item label="关联字段" prop="childRefFieldCode" required>
            <el-select
              v-model="editor.childRefFieldCode"
              :loading="childFieldsLoading"
              :disabled="!editor.childEntityId"
              filterable
              placeholder="选择保存当前记录 ID 的字段"
              style="width: 100%"
            >
              <el-option
                v-for="field in childFieldOptions"
                :key="field.fieldCode"
                :label="`${field.fieldName || field.fieldCode} / ${field.fieldCode} · ${field.fieldType || '未知类型'}`"
                :value="field.fieldCode"
              />
            </el-select>
            <div class="form-tip">字段位于“{{ editor.childEntityName || '关联实体' }}”，支持指向当前实体的单值引用，以及主键类型兼容的普通字符串字段（长度至少 64）。</div>
          </el-form-item>
          <el-alert
            v-if="editor.childEntityId && !childFieldsLoading && !childFieldOptions.length"
            title="关联实体中还没有兼容的 ID 字段"
            description="请选择或新增长度至少 64 的文本字段，或指向当前实体的单值实体引用字段，保存并发布后重新选择。不支持数字、多值字段或引用其他实体的字段。"
            type="warning"
            :closable="false"
            show-icon
          />
          <div v-if="editor.childRefFieldCode" class="form-tip">
            匹配规则：{{ editor.childEntityName || editor.childEntityCode || '关联实体' }}.{{ editor.childRefFieldCode }} = 当前记录.id
          </div>
          <el-button
            v-if="editor.childEntityId"
            link type="primary"
            @click="$router.push(`/entity/design/${editor.childEntityId}`)"
          >前往关联实体配置字段</el-button>
        </SettingsSection>

        <SettingsSection
          title="关系规则"
          description="定义数量、所有权、删除行为和运行状态"
          :collapsible="false"
        >
          <el-form-item label="关联数量" prop="relationType" required>
            <el-radio-group v-model="editor.relationType">
              <el-radio-button value="ONE_TO_ONE">一对一 · 显示表单</el-radio-button>
              <el-radio-button value="ONE_TO_MANY">一对多 · 显示列表</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="所有权类型" prop="ownershipType" required>
            <el-radio-group v-model="editor.ownershipType" @change="handleOwnershipChange">
              <el-radio-button value="COMPOSITION">组成关系</el-radio-button>
              <el-radio-button value="ASSOCIATION">普通关联</el-radio-button>
            </el-radio-group>
            <div class="form-tip">
              组成关系表示子记录属于父记录生命周期；普通关联不会随父记录删除。
            </div>
          </el-form-item>
          <el-form-item label="级联删除">
            <el-switch
              v-model="editor.cascadeDelete"
              :disabled="editor.ownershipType !== 'COMPOSITION'"
            />
            <span class="switch-tip">
              {{ editor.ownershipType === 'COMPOSITION'
                ? '删除父记录时同时删除子记录'
                : '普通关联不允许级联删除' }}
            </span>
          </el-form-item>
          <el-form-item label="是否必填">
            <el-switch v-model="editor.required" />
            <span class="switch-tip">保存聚合数据时要求至少存在对应子记录</span>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number
              v-model="editor.sortOrder"
              :min="0"
              :max="9999"
              controls-position="right"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="是否启用">
            <el-switch v-model="editor.enabled" />
            <span class="switch-tip">停用后运行时不再加载该关系</span>
          </el-form-item>
        </SettingsSection>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存修改' : '创建关系' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi } from '@/api/entity'
import { entityRelationApi } from '@/api/entityRelation'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import PageState from '@/components/PageState.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import {
  ENTITY_RELATION_CODE_PATTERN,
  createEntityRelationDraft,
  normalizeEntityRelation,
  relationReferenceFields,
  sortEntityRelations,
  toEntityRelationSavePayload
} from '@/shared/entity-relation'

const props = defineProps({
  entityId: {
    type: [String, Number],
    required: true
  },
  canManage: {
    type: Boolean,
    default: false
  },
  readonlyEntity: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['count-change'])

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const relations = ref([])
const editorVisible = ref(false)
const editorFormRef = ref(null)
const editor = ref(createEntityRelationDraft())
const childFields = ref([])
const childFieldsLoading = ref(false)
let childFieldRequestSequence = 0

const isEditing = computed(() => Boolean(editor.value.id))
const childFieldOptions = computed(() => relationReferenceFields(childFields.value, props.entityId))

const codeRule = {
  validator: (_rule, value, callback) => {
    if (!String(value || '').trim()) {
      callback()
    } else if (!isEditing.value
      && !ENTITY_RELATION_CODE_PATTERN.test(String(value).trim())) {
      callback(new Error('必须以字母开头，且只能包含字母、数字和下划线'))
    } else {
      callback()
    }
  },
  trigger: ['blur', 'change']
}

const editorRules = {
  relationName: [
    { required: true, message: '请输入关系名称', trigger: 'blur' },
    { max: 200, message: '关系名称不能超过 200 个字符', trigger: 'blur' }
  ],
  relationCode: [codeRule],
  dataKey: [codeRule],
  childEntityId: [{ required: true, message: '请选择子实体', trigger: 'change' }],
  childRefFieldCode: [{ required: true, message: '请选择关联实体中保存当前记录 ID 的字段', trigger: 'change' }],
  relationType: [{ required: true, message: '请选择关系基数', trigger: 'change' }],
  ownershipType: [{ required: true, message: '请选择所有权类型', trigger: 'change' }]
}

onMounted(loadRelations)
watch(() => props.entityId, loadRelations)
watch(() => props.readonlyEntity, loadRelations)

async function loadRelations() {
  loadError.value = ''
  if (!props.entityId || props.readonlyEntity) {
    relations.value = []
    emit('count-change', 0)
    return
  }
  loading.value = true
  try {
    const response = await entityRelationApi.list(props.entityId)
    const rows = Array.isArray(response)
      ? response
      : response?.records || response?.list || response?.data || []
    relations.value = sortEntityRelations(rows)
    emit('count-change', relations.value.length)
  } catch (error) {
    console.error('加载实体关系失败:', error)
    relations.value = []
    emit('count-change', 0)
    loadError.value = error?.message || '无法读取实体关系，请检查权限或稍后重试。'
  } finally {
    loading.value = false
  }
}

function nextSortOrder() {
  const maxOrder = relations.value.reduce(
    (current, item) => Math.max(current, Number(item.sortOrder || 0)),
    -10
  )
  return maxOrder + 10
}

function openCreate() {
  editor.value = createEntityRelationDraft(nextSortOrder())
  childFields.value = []
  editorVisible.value = true
}

async function openEdit(row) {
  editor.value = normalizeEntityRelation(row)
  editorVisible.value = true
  await loadChildFields(editor.value.childEntityId)
}

function resetEditor() {
  // 关闭后忽略尚未完成的字段请求，避免下一次新建被上一次目标实体覆盖。
  ++childFieldRequestSequence
  childFieldsLoading.value = false
  editor.value = createEntityRelationDraft()
  childFields.value = []
  editorFormRef.value?.clearValidate()
}

async function handleChildEntityChange(value) {
  editor.value.childRefFieldCode = ''
  editor.value.childEntityName = ''
  editor.value.childEntityCode = ''
  await loadChildFields(value)
  if (!isEditing.value && childFieldOptions.value.length === 1) {
    editor.value.childRefFieldCode = childFieldOptions.value[0].fieldCode
  }
}

async function loadChildFields(childEntityId) {
  const sequence = ++childFieldRequestSequence
  if (!childEntityId) {
    childFields.value = []
    return
  }
  childFieldsLoading.value = true
  try {
    const entity = await entityApi.getById(childEntityId)
    if (sequence !== childFieldRequestSequence) return
    childFields.value = entity?.fields || []
    editor.value.childEntityName = entity?.entityName || ''
    editor.value.childEntityCode = entity?.entityCode || ''
    if (!editor.value.relationName) editor.value.relationName = entity?.entityName || ''
  } catch (error) {
    if (sequence !== childFieldRequestSequence) return
    console.error('加载子实体字段失败:', error)
    childFields.value = []
    ElMessage.error(error?.message || '加载子实体字段失败')
  } finally {
    if (sequence === childFieldRequestSequence) {
      childFieldsLoading.value = false
    }
  }
}

function handleOwnershipChange(value) {
  if (value === 'ASSOCIATION') editor.value.cascadeDelete = false
}

async function handleSave() {
  if (!props.canManage) return
  try {
    await editorFormRef.value?.validate()
  } catch {
    ElMessage.warning('请完善实体关系配置')
    return
  }
  saving.value = true
  try {
    const payload = toEntityRelationSavePayload(editor.value)
    if (isEditing.value) {
      await entityRelationApi.update(props.entityId, editor.value.id, payload)
      ElMessage.success('实体关系已更新，发布当前实体后生效')
    } else {
      await entityRelationApi.create(props.entityId, payload)
      ElMessage.success('关系已创建，请发布当前实体，再到表单设计选择关联表单或列表')
    }
    editorVisible.value = false
    await loadRelations()
  } catch (error) {
    console.error('保存实体关系失败:', error)
    ElMessage.error(error?.message || '保存实体关系失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  if (!props.canManage) return
  try {
    await ElMessageBox.confirm(
      `确认删除关系「${row.relationName}」？关系编码 ${row.relationCode} 和数据键 ${row.dataKey} 将退役且不能复用；引用它的表单、版本配置或接口需要先完成迁移。`,
      '删除实体关系',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await entityRelationApi.delete(props.entityId, row.id)
    ElMessage.success('实体关系已删除，稳定编码已退役')
    await loadRelations()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error('删除实体关系失败:', error)
    ElMessage.error(error?.message || '删除实体关系失败')
  }
}

function relationTypeLabel(value) {
  return value === 'ONE_TO_ONE' ? '一对一' : '一对多'
}

function ownershipTypeLabel(value) {
  return value === 'ASSOCIATION' ? '普通关联' : '组成关系'
}
</script>

<style scoped>
.relation-management {
  flex: 1;
  min-height: 0;
  margin: var(--entity-design-panel-gap, 16px) 0;
  padding: 20px;
  overflow: auto;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgb(0 0 0 / 8%);
}

.relation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.relation-header h2 {
  margin: 0 0 6px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.relation-header p,
.relation-empty p {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.relation-actions,
.flag-list {
  display: flex;
  align-items: center;
  gap: 8px;
}

.relation-alert,
.editor-alert {
  margin-bottom: 16px;
}

.relation-table {
  width: 100%;
}

.primary-text {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.legacy-tag {
  margin-top: 6px;
}

code {
  overflow-wrap: anywhere;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.switch-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 768px) {
  .relation-management {
    padding: 14px;
  }

  .relation-header {
    flex-direction: column;
    gap: 12px;
  }

  .relation-actions {
    width: 100%;
  }

  .relation-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
