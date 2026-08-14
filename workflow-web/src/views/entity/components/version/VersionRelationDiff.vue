<template>
  <article class="relation-diff">
    <header class="relation-heading">
      <div>
        <h4>{{ relationTitle }}</h4>
        <p v-if="nameChanged">原关系名称：{{ node.oldName }}</p>
      </div>
      <div class="relation-counts">
        <el-tag type="success" effect="plain">新增 {{ counts.added || 0 }}</el-tag>
        <el-tag type="danger" effect="plain">删除 {{ counts.removed || 0 }}</el-tag>
        <el-tag type="warning" effect="plain">修改 {{ counts.modified || 0 }}</el-tag>
        <el-tag v-if="counts.moved" type="primary" effect="plain">移动 {{ counts.moved }}</el-tag>
      </div>
    </header>

    <VersionDiffForm
      v-if="node.formSections?.length"
      :sections="node.formSections"
      :changed-only="changedOnly"
      :show-code="showCode"
    />

    <el-collapse v-model="openedRows" class="relation-rows">
      <el-collapse-item v-for="row in visibleRows" :key="row.recordId" :name="row.recordId">
        <template #title>
          <div class="row-title">
            <span><strong>{{ row.title }}</strong><small>{{ row.recordId }}</small></span>
            <span class="row-change-tags">
              <el-tag :type="changeTag(row.changeType)" effect="plain" size="small">{{ changeText(row.changeType) }}</el-tag>
              <el-tag v-if="row.moved && row.changeType !== 'MOVED'" type="primary" effect="plain" size="small">同时移动</el-tag>
            </span>
          </div>
        </template>
        <VersionDiffForm :sections="row.formSections" :changed-only="changedOnly" :show-code="showCode" />
      </el-collapse-item>
    </el-collapse>

    <el-empty v-if="!visibleRows.length && !node.formSections?.length" description="筛选条件下没有关联行变化" :image-size="70" />
    <el-pagination
      v-if="node.rowPage?.total > node.rowPage?.pageSize"
      class="row-pagination"
      :current-page="node.rowPage.pageNum"
      :page-size="node.rowPage.pageSize"
      :total="node.rowPage.total"
      layout="total, prev, pager, next"
      @current-change="$emit('pageChange', node, $event)"
    />
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import VersionDiffForm from './VersionDiffForm.vue'

const props = defineProps<{
  node: any
  changedOnly?: boolean
  showCode?: boolean
  expandAll?: boolean
}>()
defineEmits<{ pageChange: [node: any, page: number] }>()
const openedRows = ref<string[]>([])
const visibleRows = computed(() => props.changedOnly
  ? (props.node.rowChanges || []).filter((row: any) => row.changeType !== 'UNCHANGED' || row.moved)
  : (props.node.rowChanges || []))
const counts = computed(() => props.node.counts || {})
const relationTitle = computed(() => props.node.newName || props.node.oldName || props.node.name || '关联数据')
const nameChanged = computed(() => props.node.oldName && props.node.newName && props.node.oldName !== props.node.newName)
watch(() => props.expandAll, value => {
  openedRows.value = value ? visibleRows.value.map((row: any) => row.recordId) : []
}, { immediate: true })
function changeText(type: string) { return ({ ADDED: '新增', REMOVED: '删除', MODIFIED: '修改', MOVED: '移动', UNCHANGED: '未变化' } as any)[type] || type }
function changeTag(type: string) { return ({ ADDED: 'success', REMOVED: 'danger', MODIFIED: 'warning', MOVED: 'primary', UNCHANGED: 'info' } as any)[type] || 'info' }
</script>

<style scoped>
.relation-diff { margin-bottom: 18px; padding: 14px; border: 1px solid var(--el-border-color); border-radius: 10px; background: var(--el-bg-color); }
.relation-heading, .row-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.relation-heading { margin-bottom: 12px; }
.relation-heading h4 { margin: 0; font-size: 16px; }
.relation-heading p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
.relation-counts { display: flex; flex-wrap: wrap; gap: 5px; }
.relation-rows { margin-top: 10px; }
.row-title { width: 100%; padding-right: 12px; }
.row-title span { display: flex; flex-direction: column; align-items: flex-start; }
.row-title .row-change-tags { flex-direction: row; align-items: center; gap: 5px; }
.row-title small { color: var(--el-text-color-secondary); font-family: monospace; }
.row-pagination { justify-content: flex-end; margin-top: 12px; }
@media (max-width: 767px) { .relation-heading { align-items: flex-start; flex-direction: column; } }
</style>
