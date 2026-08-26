<template>
  <el-drawer
    v-model="visible"
    title="关联内容"
    direction="rtl"
    size="min(920px, 92vw)"
    append-to-body
    class="related-content-panel"
  >
    <template #header>
      <div class="panel-heading">
        <div>
          <strong>关联内容</strong>
          <span>在当前{{ ownerType === 'FORM' ? '表单' : '列表' }}中组合其他实体的表单或列表</span>
        </div>
        <el-button type="primary" :disabled="!ownerId || loading" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新增关联内容
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="panel-body">
      <el-alert
        title="普通场景按“显示什么、数据怎么关联、允许做什么”三步即可完成；复杂场景再展开特殊处理。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
        class="load-error"
      >
        <template #default>
          <el-button size="small" type="danger" plain @click="load">重新加载</el-button>
        </template>
      </el-alert>

      <div class="panel-toolbar">
        <div>
          <strong>已配置 {{ rows.length }} 项</strong>
          <span>保存后进入草稿，发布当前{{ ownerType === 'FORM' ? '表单' : '列表' }}后生效</span>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-empty
        v-if="!loading && !loadError && !rows.length"
        description="还没有关联内容"
        :image-size="92"
      >
        <el-button type="primary" @click="openCreate">配置第一项关联内容</el-button>
      </el-empty>

      <div v-else class="content-list">
        <article
          v-for="item in rows"
          :key="item.id || item.compositionKey"
          class="content-card"
        >
          <div class="content-card__main">
            <div class="content-card__heading">
              <div>
                <strong>{{ item.config.name || item.config.target.contentName || '未命名关联内容' }}</strong>
                <el-tag
                  size="small"
                  :type="item.config.enabled === false ? 'info' : 'success'"
                  effect="plain"
                >
                  {{ item.config.enabled === false ? '已停用' : '已启用' }}
                </el-tag>
              </div>
              <span>修订 {{ item.revision || 0 }}</span>
            </div>
            <p>{{ describeRelatedContent(item, sourceEntity.entityName || '当前页面') }}</p>
            <div class="content-card__meta">
              <span>
                <el-icon><Link /></el-icon>
                {{ describeRelatedContentRelation(item) }}
              </span>
              <span>
                <el-icon><Operation /></el-icon>
                {{ describeRelatedContentActions(item) }}
              </span>
              <span>
                <el-icon><Tools /></el-icon>
                {{ describeRelatedContentSpecial(item) }}
              </span>
            </div>
          </div>
          <div class="content-card__actions">
            <el-button link type="primary" @click="openEdit(item)">编辑</el-button>
            <el-button
              link
              type="danger"
              :loading="deletingId === item.id"
              @click="remove(item)"
            >删除</el-button>
          </div>
        </article>
      </div>
    </div>

    <RelatedContentConfigDialog
      ref="dialogRef"
      :owner-type="ownerType"
      :owner-id="ownerId"
      :source-entity="sourceEntity"
      :source-fields="sourceFields"
      :source-content-fields="sourceContentFields"
      :anchor-options="anchorOptions"
      :existing-count="rows.length"
      @saved="handleSaved"
    />
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Link, Operation, Plus, Refresh, Tools } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RelatedContentConfigDialog from './RelatedContentConfigDialog.vue'
import { uiCompositionApi } from '@/api/uiComposition'
import {
  describeRelatedContent,
  describeRelatedContentActions,
  describeRelatedContentRelation,
  describeRelatedContentSpecial,
  normalizeRelatedContent
} from '@/shared/related-content'

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], default: '' },
  sourceEntity: { type: Object, default: () => ({}) },
  sourceFields: { type: Array, default: () => [] },
  sourceContentFields: { type: Array, default: () => [] },
  anchorOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['count-change', 'changed'])
const visible = ref(false)
const loading = ref(false)
const deletingId = ref('')
const loadError = ref('')
const rows = ref([])
const dialogRef = ref(null)

function normalizeRows(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.list)) return response.list
  return []
}

/**
 * 关联内容独立保存，但仍属于宿主草稿。每次重载都以服务端 revision 为准，
 * 避免编辑或删除时覆盖其他管理员刚保存的变更。
 */
async function load({ silent = false } = {}) {
  if (!props.ownerId) {
    rows.value = []
    emit('count-change', 0)
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const response = await uiCompositionApi.list(props.ownerType, props.ownerId)
    rows.value = normalizeRows(response)
      .map(item => normalizeRelatedContent(item, {
        ownerType: props.ownerType,
        sourceEntity: props.sourceEntity
      }))
      .sort((left, right) => Number(left.orderKey || 0) - Number(right.orderKey || 0))
    emit('count-change', rows.value.length)
  } catch (error) {
    rows.value = []
    emit('count-change', 0)
    loadError.value = error?.message || '关联内容加载失败，请重试'
    if (!silent) ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

async function open() {
  visible.value = true
  await load({ silent: true })
}

function openCreate() {
  dialogRef.value?.open()
}

function openEdit(item) {
  dialogRef.value?.open(item)
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(
      `删除“${item.config.name || item.config.target.contentName || '该关联内容'}”后，当前草稿将不再显示它；发布后运行页面才会生效。确定继续吗？`,
      '删除关联内容',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  deletingId.value = item.id
  try {
    const result = await uiCompositionApi.remove(
      props.ownerType,
      props.ownerId,
      item.id,
      item.revision,
      item.ownerRevision
    )
    ElMessage.success('关联内容已从草稿删除，发布页面配置后生效')
    await load({ silent: true })
    emit('changed', {
      action: 'DELETE',
      item,
      ownerRevision: result?.ownerRevision
        ?? rows.value[0]?.ownerRevision
    })
  } catch (error) {
    ElMessage.error(error?.message || '删除关联内容失败')
    await load({ silent: true })
  } finally {
    deletingId.value = ''
  }
}

async function handleSaved(saved) {
  await load({ silent: true })
  emit('changed', {
    action: 'SAVE',
    item: saved,
    ownerRevision: saved?.ownerRevision
      ?? rows.value[0]?.ownerRevision
  })
}

watch(
  () => props.ownerId,
  () => load({ silent: true }),
  { immediate: true }
)

defineExpose({ open, load })
</script>

<style scoped>
.panel-heading,
.panel-toolbar,
.content-card,
.content-card__heading,
.content-card__heading > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-heading {
  width: 100%;
}

.panel-heading > div,
.panel-toolbar > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.panel-heading span,
.panel-toolbar span,
.content-card__heading > span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.panel-body {
  min-height: 420px;
}

.load-error {
  margin-top: 12px;
}

.load-error :deep(.el-alert__content) {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
}

.panel-toolbar {
  margin: 16px 0 12px;
}

.content-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-card {
  align-items: stretch;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.content-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--el-box-shadow-light);
}

.content-card__main {
  min-width: 0;
  flex: 1;
}

.content-card__heading > div {
  justify-content: flex-start;
}

.content-card__main p {
  margin: 8px 0;
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.6;
}

.content-card__meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.content-card__meta span {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.content-card__meta .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.content-card__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  gap: 4px;
}

@media (max-width: 720px) {
  .panel-heading,
  .panel-toolbar,
  .content-card {
    flex-direction: column;
    align-items: stretch;
  }

  .content-card__actions {
    justify-content: flex-end;
  }
}
</style>
