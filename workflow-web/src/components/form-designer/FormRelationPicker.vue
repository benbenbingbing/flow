<template>
  <section class="form-relation-picker">
    <div class="relation-heading">
      <strong>实体关系</strong>
      <el-button link size="small" :loading="loading" @click="load">刷新</el-button>
    </div>
    <p>一对一选表单，一对多选列表，直接嵌入当前表单。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-for="relation in relations" :key="relation.id || relation.relationCode" class="relation-item">
      <div>
        <strong>{{ relation.relationName }}</strong>
        <small>{{ relation.childEntityName || relation.childEntityCode }} · {{ typeLabel(relation) }}</small>
      </div>
      <el-button
        size="small" link type="primary"
        :disabled="!ownerId || relation.enabled === false"
        @click="openRelation(relation)"
      >{{ relation.enabled === false ? '已停用' : existingFor(relation) ? '已添加 · 配置' : `选择${typeLabel(relation)}` }}</el-button>
    </div>
    <p v-if="!loading && !error && !relations.length">尚未定义关系，请先配置关联实体及关联字段。</p>
    <el-button v-if="sourceEntity.id" link type="primary" @click="openRelationManagement">
      管理实体关系
    </el-button>

    <el-dialog v-model="visible" :title="`关联${typeLabel(selectedRelation)}`" width="620px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="关系">{{ selectedRelation?.relationName }}</el-descriptions-item>
        <el-descriptions-item label="关联实体">{{ selectedRelation?.childEntityName || selectedRelation?.childEntityCode }}</el-descriptions-item>
        <el-descriptions-item label="关联规则">{{ selectedRelation?.childRefFieldCode }} = 当前记录.id</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" class="target-form">
        <el-form-item :label="`选择要显示的${typeLabel(selectedRelation)}`" required>
          <el-select v-model="contentId" :loading="catalogLoading" filterable :placeholder="`请选择已发布的${typeLabel(selectedRelation)}`" style="width: 100%">
            <el-option v-for="option in options" :key="option.id" :value="option.id" :label="option.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="catalogError" :title="catalogError" type="error" :closable="false" />
      <el-alert v-else-if="!catalogLoading && !options.length" :title="`关联实体暂无可用的已发布${typeLabel(selectedRelation)}，请先配置并发布。`" type="info" :closable="false" />
      <p class="help">保存后直接显示在当前表单下方，发布当前表单后生效。系统会按当前记录自动查找关联数据。</p>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!contentId || catalogLoading || !!catalogError" @click="save">{{ editing ? '保存显示配置' : '添加到表单' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityRelationApi } from '@/api/entityRelation'
import { getFormsByEntity } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiCompositionApi } from '@/api/uiComposition'
import { relationContentType, sortEntityRelations } from '@/shared/entity-relation'
import { buildRelatedContentPayload } from '@/shared/related-content'
import { buildRelationContent, isSimpleRelationContent, relationContentOptions } from '@/shared/relation-content'

const props = defineProps({
  sourceEntity: { type: Object, required: true },
  ownerId: { type: [String, Number], default: '' },
  compositions: { type: Array, default: () => [] }
})
const emit = defineEmits(['saved', 'edit'])
const router = useRouter()
const relations = ref([]), loading = ref(false), error = ref('')
const visible = ref(false), selectedRelation = ref(null), editing = ref(null)
const options = ref([]), contentId = ref(''), catalogLoading = ref(false), catalogError = ref(''), saving = ref(false)
let loadSequence = 0, catalogSequence = 0
const typeLabel = relation => relationContentType(relation) === 'FORM' ? '表单' : '列表'
const existingFor = relation => props.compositions.find(item =>
  item.config?.relation?.type === 'ENTITY_RELATION'
    && item.config.relation.relationCode === relation.relationCode)

/** 在新标签页管理关系，保留当前表单设计状态；解析路由以兼容应用部署路径。 */
function openRelationManagement() {
  if (!props.sourceEntity.id) return
  const route = router.resolve({
    path: `/entity/design/${props.sourceEntity.id}`,
    query: { tab: 'relations' }
  })
  window.open(route.href, '_blank', 'noopener,noreferrer')
}

/** 关系目录独立于实体字段；失败时保留错误，不能把失败伪装成没有关系。 */
async function load() {
  const sequence = ++loadSequence
  relations.value = []
  error.value = ''
  if (!props.sourceEntity.id) return
  loading.value = true
  try {
    const rows = await entityRelationApi.list(props.sourceEntity.id)
    if (sequence === loadSequence) relations.value = sortEntityRelations(Array.isArray(rows) ? rows : [])
  } catch (e) {
    if (sequence === loadSequence) error.value = e?.message || '实体关系加载失败，请重试'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

/** 简单关系只需要选择目标页面；已有高级配置不在此入口被覆盖。 */
async function openRelation(relation) {
  const existing = existingFor(relation)
  if (existing && !isSimpleRelationContent(existing)) {
    emit('edit', existing)
    return
  }
  selectedRelation.value = relation
  editing.value = existing || null
  contentId.value = existing?.config?.target?.contentId ? String(existing.config.target.contentId) : ''
  options.value = []
  catalogError.value = ''
  visible.value = true
  const sequence = ++catalogSequence
  catalogLoading.value = true
  try {
    // 其他节点的保存也会推进宿主修订号；编辑既有关系时必须重新读取，
    // 不能使用侧栏首次加载时的 ownerRevision，否则正常编辑也会发生并发冲突。
    const [rows, currentItems] = await Promise.all([
      relationContentType(relation) === 'FORM'
        ? getFormsByEntity(relation.childEntityId)
        : entityListConfigApi.getByEntityId(relation.childEntityId),
      existing ? uiCompositionApi.list('FORM', props.ownerId) : Promise.resolve(null)
    ])
    if (sequence !== catalogSequence) return
    if (existing) {
      const current = (currentItems || []).find(item => item.id === existing.id)
      if (!current) throw new Error('此关联展示已被删除，请刷新表单后重新添加')
      if (!isSimpleRelationContent(current)) {
        visible.value = false
        emit('edit', current)
        return
      }
      editing.value = current
      contentId.value = String(current.config?.target?.contentId || '')
    }
    options.value = relationContentOptions(rows, relation, props.ownerId)
    if (!options.value.some(option => option.id === contentId.value)) contentId.value = ''
    if (!contentId.value && options.value.length === 1) contentId.value = options.value[0].id
  } catch (e) {
    if (sequence === catalogSequence) catalogError.value = e?.message || '目标页面加载失败，请关闭后重试'
  } finally {
    if (sequence === catalogSequence) catalogLoading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const item = buildRelationContent({
      relation: selectedRelation.value,
      content: options.value.find(option => option.id === contentId.value),
      sourceEntity: props.sourceEntity,
      existing: editing.value,
      orderKey: Math.max(0, ...props.compositions.map(row => Number(row.orderKey) || 0)) + 1000000
    })
    const payload = buildRelatedContentPayload(item, 'FORM', props.ownerId)
    const saved = editing.value
      ? await uiCompositionApi.update('FORM', props.ownerId, editing.value.id, payload)
      : await uiCompositionApi.create('FORM', props.ownerId, payload)
    visible.value = false
    emit('saved', { item: saved, ownerRevision: saved?.ownerRevision })
    ElMessage.success('已添加关联展示，发布当前表单后生效')
  } catch (e) {
    ElMessage.error(e?.message || '关联展示保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.sourceEntity.id, load, { immediate: true })
watch(visible, value => { if (!value) ++catalogSequence })
defineExpose({ load, openRelation })
</script>

<style scoped>
.form-relation-picker { padding: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.relation-heading { display: flex; align-items: center; justify-content: space-between; }
.form-relation-picker p, .help { font-size: 12px; line-height: 1.7; color: var(--el-text-color-secondary); }
.relation-item { padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.relation-item strong, .relation-item small { display: block; overflow-wrap: anywhere; }
.relation-item small { margin-top: 4px; color: var(--el-text-color-secondary); }
.target-form { margin-top: 20px; }
</style>
