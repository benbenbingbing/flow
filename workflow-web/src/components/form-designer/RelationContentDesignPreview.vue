<template>
  <section class="relation-design-preview">
    <header>
      <div>
        <strong>{{ config.name || target.contentName }}</strong>
        <el-tag size="small" effect="plain">{{ target.contentType === 'FORM' ? '关联表单 · 一对一' : '关联列表 · 一对多' }}</el-tag>
      </div>
      <div v-if="editable">
        <el-button link type="primary" @click="$emit('edit', composition)">配置</el-button>
        <el-button link type="danger" @click="$emit('remove', composition)">移除</el-button>
      </div>
    </header>
    <p>{{ target.entityName }} / {{ target.contentName }} · 发布后按当前记录自动显示关联数据</p>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <FormPreviewLinkage
      v-else-if="previewForm" :form="previewForm" :model-value="{}"
      :readonly="true" mode="view" :show-header="false" height="auto"
    />
    <el-table v-else-if="columns.length" :data="[]" border empty-text="运行时仅显示与当前记录关联的数据">
      <el-table-column v-for="column in columns" :key="column.fieldCode || column.id" :label="column.fieldLabel || column.fieldName || column.fieldCode" min-width="140" />
    </el-table>
    <el-empty v-else description="目标页面未配置可展示字段" :image-size="50" />
  </section>
</template>

<script setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import { safeParseConfig } from '@/shared/config-runtime'

const FormPreviewLinkage = defineAsyncComponent(() => import('@/components/FormPreviewLinkage.vue'))
const props = defineProps({ composition: { type: Object, required: true }, editable: Boolean })
defineEmits(['edit', 'remove'])
const config = computed(() => props.composition.config || {})
const target = computed(() => config.value.target || {})
const previewForm = ref(null), columns = ref([]), loading = ref(false), error = ref('')
let sequence = 0

/**
 * 设计态只读取发布布局，不执行关联数据查询。目标表单的嵌套关联不在空记录预览中展开，
 * 防止递归加载；真正的数据和嵌套权限由发布运行时统一解析。
 */
async function load() {
  const request = ++sequence
  const selection = { ...target.value }
  previewForm.value = null
  columns.value = []
  error.value = ''
  if (!selection.contentId) return
  loading.value = true
  try {
    if (selection.contentType === 'FORM') {
      const release = await getFormRuntimeRelease(selection.contentId, selection.releaseId, selection.releaseVersion)
      if (request !== sequence) return
      const form = normalizeRuntimeFormRelease(release, selection.contentId)
      previewForm.value = { ...form, viewCompositions: [] }
    } else {
      // 列表预览读已发布列定义，不能误用当前草稿，也不能发出未限定父记录的查询。
      const list = await entityListConfigApi.getById(selection.contentId)
      const releases = await entityListConfigApi.getReleases(selection.contentId)
      if (request !== sequence) return
      const id = selection.releaseId || list.activeReleaseId
      const release = (releases || []).find(item => String(item.id) === String(id))
      if (!release) throw new Error('关联列表缺少可用发布版本，请先发布列表')
      const snapshot = safeParseConfig(release.snapshotDocument)
      columns.value = (snapshot.list?.fields || []).filter(field => field.showInList && field.isHidden !== 1)
    }
  } catch (e) {
    if (request === sequence) error.value = e?.message || '关联页面预览加载失败'
  } finally {
    if (request === sequence) loading.value = false
  }
}
watch(() => [target.value.contentId, target.value.contentType, target.value.releaseId, props.composition.revision], load, { immediate: true })
</script>

<style scoped>
.relation-design-preview { margin: 20px 0; padding: 16px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
header, header > div { display: flex; align-items: center; gap: 10px; }
header { justify-content: space-between; }
p { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 10px 0 18px; }
</style>
